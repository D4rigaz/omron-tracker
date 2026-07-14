package com.darigaz.omrontracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darigaz.omrontracker.data.AppDatabase
import com.darigaz.omrontracker.data.Measurement
import com.darigaz.omrontracker.github.GitHubSyncManager
import com.darigaz.omrontracker.health.HealthConnectManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FormState(
    val weight: String = "",
    val bmi: String = "",
    val bodyFat: String = "",
    val skeletalMuscle: String = "",
    val visceralFat: String = "",
    val restingMetabolism: String = "",
    val bodyAge: String = "",
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val lastResult: String? = null,
    /** Medição sendo editada (null = criando nova). */
    val editing: Measurement? = null,
)

class MeasurementViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).measurementDao()
    val healthConnect = HealthConnectManager(app)
    val gitHub = GitHubSyncManager(app)

    private val _form = MutableStateFlow(FormState())
    val form: StateFlow<FormState> = _form

    val history = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFieldChange(field: String, value: String) {
        _form.value = when (field) {
            "weight" -> _form.value.copy(weight = value)
            "bmi" -> _form.value.copy(bmi = value)
            "bodyFat" -> _form.value.copy(bodyFat = value)
            "skeletalMuscle" -> _form.value.copy(skeletalMuscle = value)
            "visceralFat" -> _form.value.copy(visceralFat = value)
            "restingMetabolism" -> _form.value.copy(restingMetabolism = value)
            "bodyAge" -> _form.value.copy(bodyAge = value)
            else -> _form.value
        }
    }

    /** Carrega uma medição existente no formulário para edição. */
    fun startEdit(m: Measurement) {
        _form.value = FormState(
            weight = m.weightKg.toString(),
            bmi = m.bmi.toString(),
            bodyFat = m.bodyFatPercent.toString(),
            skeletalMuscle = m.skeletalMusclePercent.toString(),
            visceralFat = m.visceralFatLevel.toString(),
            restingMetabolism = m.restingMetabolismKcal.toString(),
            bodyAge = m.bodyAge.toString(),
            editing = m,
        )
    }

    fun cancelEdit() {
        _form.value = FormState()
    }

    /** Exclui uma medição do Room, do GitHub e do Health Connect. */
    fun delete(m: Measurement) {
        viewModelScope.launch {
            dao.delete(m)

            try {
                if (healthConnect.isAvailable() && healthConnect.hasAllPermissions()) {
                    healthConnect.deleteMeasurement(m)
                }
            } catch (_: Exception) {
                // best-effort: HC pode não ter os registros
            }

            if (gitHub.isConfigured()) {
                gitHub.markDeleted(m.timestamp)
                try {
                    val sent = gitHub.push(dao.pendingGitHubSync())
                    sent.forEach { dao.update(it.copy(syncedToGitHub = true)) }
                } catch (_: Exception) {
                    // exclusão fica pendente; aplicada no próximo sync
                }
            }
        }
    }

    /** Valida faixas plausíveis para os valores da HBF-514C. */
    private fun validate(f: FormState): Map<String, String> {
        val errors = mutableMapOf<String, String>()

        fun check(name: String, raw: String, min: Double, max: Double, label: String) {
            val v = raw.replace(',', '.').toDoubleOrNull()
            if (v == null || v < min || v > max) {
                errors[name] = "$label deve estar entre $min e $max"
            }
        }

        check("weight", f.weight, 20.0, 300.0, "Peso (kg)")
        check("bmi", f.bmi, 10.0, 80.0, "IMC")
        check("bodyFat", f.bodyFat, 3.0, 60.0, "Gordura (%)")
        check("skeletalMuscle", f.skeletalMuscle, 15.0, 60.0, "Músculo (%)")
        check("visceralFat", f.visceralFat, 1.0, 30.0, "Visceral")
        check("restingMetabolism", f.restingMetabolism, 800.0, 4000.0, "Metabolismo (kcal)")
        check("bodyAge", f.bodyAge, 18.0, 80.0, "Idade corporal")

        return errors
    }

    fun save() {
        val f = _form.value
        val errors = validate(f)
        if (errors.isNotEmpty()) {
            _form.value = f.copy(errors = errors, lastResult = null)
            return
        }

        val original = f.editing
        val measurement = (original ?: Measurement(
            weightKg = 0.0, bmi = 0.0, bodyFatPercent = 0.0,
            skeletalMusclePercent = 0.0, visceralFatLevel = 0,
            restingMetabolismKcal = 0, bodyAge = 0,
        )).copy(
            weightKg = f.weight.replace(',', '.').toDouble(),
            bmi = f.bmi.replace(',', '.').toDouble(),
            bodyFatPercent = f.bodyFat.replace(',', '.').toDouble(),
            skeletalMusclePercent = f.skeletalMuscle.replace(',', '.').toDouble(),
            visceralFatLevel = f.visceralFat.replace(',', '.').toDouble().toInt(),
            restingMetabolismKcal = f.restingMetabolism.replace(',', '.').toDouble().toInt(),
            bodyAge = f.bodyAge.replace(',', '.').toDouble().toInt(),
            syncedToHealthConnect = false,
            syncedToGitHub = false,
        )

        viewModelScope.launch {
            _form.value = _form.value.copy(saving = true, errors = emptyMap())

            var saved = if (original == null) {
                val id = dao.insert(measurement)
                measurement.copy(id = id)
            } else {
                dao.update(measurement)
                measurement
            }

            val hcResult = try {
                if (healthConnect.isAvailable() && healthConnect.hasAllPermissions()) {
                    if (original != null) {
                        // edição: remove os registros antigos antes de regravar
                        healthConnect.deleteMeasurement(original)
                    }
                    healthConnect.insertMeasurement(saved)
                    saved = saved.copy(syncedToHealthConnect = true)
                    dao.update(saved)
                    "Health Connect ✓"
                } else {
                    "Health Connect indisponível"
                }
            } catch (e: Exception) {
                "Health Connect falhou: ${e.message}"
            }

            val ghResult = try {
                if (gitHub.isConfigured()) {
                    // Aproveita para enviar tudo que estiver pendente
                    val sent = gitHub.push(dao.pendingGitHubSync())
                    sent.forEach { dao.update(it.copy(syncedToGitHub = true)) }
                    "GitHub ✓"
                } else {
                    null // não configurado: não polui a mensagem
                }
            } catch (e: Exception) {
                "GitHub falhou: ${e.message?.take(80)}"
            }

            val prefix = if (original == null) "Salvo localmente" else "Edição salva"
            val result = listOfNotNull(prefix, hcResult, ghResult)
                .joinToString(" · ")

            _form.value = FormState(lastResult = result)
        }
    }

    /** Reenvia medições que ficaram pendentes de sync (Health Connect e GitHub). */
    fun syncPending() {
        viewModelScope.launch {
            if (healthConnect.isAvailable() && healthConnect.hasAllPermissions()) {
                dao.pendingSync().forEach { m ->
                    try {
                        healthConnect.insertMeasurement(m)
                        dao.update(m.copy(syncedToHealthConnect = true))
                    } catch (_: Exception) {
                        // mantém pendente; tenta na próxima
                    }
                }
            }

            if (gitHub.isConfigured()) {
                try {
                    val sent = gitHub.push(dao.pendingGitHubSync())
                    sent.forEach { dao.update(it.copy(syncedToGitHub = true)) }
                } catch (_: Exception) {
                    // mantém pendente; tenta na próxima
                }
            }
        }
    }
}
