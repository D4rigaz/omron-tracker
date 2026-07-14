package com.darigaz.omrontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val formEditFormatter: java.time.format.DateTimeFormatter =
    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(java.time.ZoneId.systemDefault())

@Composable
fun MeasurementFormScreen(viewModel: MeasurementViewModel) {
    val form by viewModel.form.collectAsState()
    val editing = form.editing // captura local: permite smart cast do nulável

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (editing != null)
                "Editando medição — " + formEditFormatter.format(
                    java.time.Instant.ofEpochMilli(editing.timestamp)
                )
            else "Nova medição — Omron HBF-514C",
            style = MaterialTheme.typography.titleLarge,
        )

        NumberField("Peso (kg)", form.weight, form.errors["weight"]) {
            viewModel.onFieldChange("weight", it)
        }
        NumberField("IMC", form.bmi, form.errors["bmi"]) {
            viewModel.onFieldChange("bmi", it)
        }
        NumberField("Gordura corporal (%)", form.bodyFat, form.errors["bodyFat"]) {
            viewModel.onFieldChange("bodyFat", it)
        }
        NumberField("Músculo esquelético (%)", form.skeletalMuscle, form.errors["skeletalMuscle"]) {
            viewModel.onFieldChange("skeletalMuscle", it)
        }
        NumberField("Gordura visceral (1–30)", form.visceralFat, form.errors["visceralFat"]) {
            viewModel.onFieldChange("visceralFat", it)
        }
        NumberField("Metabolismo de repouso (kcal)", form.restingMetabolism, form.errors["restingMetabolism"]) {
            viewModel.onFieldChange("restingMetabolism", it)
        }
        NumberField("Idade corporal", form.bodyAge, form.errors["bodyAge"]) {
            viewModel.onFieldChange("bodyAge", it)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.save() }, enabled = !form.saving) {
                Text(if (form.saving) "Salvando..." else if (editing != null) "Salvar edição" else "Salvar")
            }
            if (editing != null) {
                Button(onClick = { viewModel.cancelEdit() }) {
                    Text("Cancelar edição")
                }
            } else {
                Button(onClick = { viewModel.syncPending() }) {
                    Text("Sincronizar pendentes")
                }
            }
        }

        form.lastResult?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = { error?.let { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
