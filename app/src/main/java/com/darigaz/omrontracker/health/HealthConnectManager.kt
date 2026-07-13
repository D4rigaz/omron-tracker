package com.darigaz.omrontracker.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import com.darigaz.omrontracker.data.Measurement
import java.time.Instant
import java.time.ZoneOffset

/**
 * Encapsula toda a interação com o Health Connect.
 *
 * Fluxo: o app grava aqui -> Health Connect armazena -> Samsung Health
 * sincroniza (Samsung Health >= 6.22.5 com sync do Health Connect habilitado
 * em Configurações > Health Connect dentro do Samsung Health).
 */
class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
    )

    /** SDK_AVAILABLE, SDK_UNAVAILABLE ou SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED */
    fun availabilityStatus(): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(): Boolean = availabilityStatus() == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    suspend fun hasAllPermissions(): Boolean =
        client.permissionController.getGrantedPermissions().containsAll(permissions)

    /**
     * Insere uma medição da Omron como 4 registros no Health Connect.
     *
     * - WeightRecord: peso (kg)
     * - BodyFatRecord: gordura corporal (%)
     * - BasalMetabolicRateRecord: metabolismo de repouso (kcal/dia)
     * - LeanBodyMassRecord: massa magra derivada (kg)
     *
     * IMC, visceral, % músculo esquelético e idade corporal não têm tipo
     * nativo no Health Connect e permanecem apenas no Room.
     */
    suspend fun insertMeasurement(m: Measurement) {
        val time: Instant = Instant.ofEpochMilli(m.timestamp)
        val zoneOffset: ZoneOffset =
            ZoneOffset.systemDefault().rules.getOffset(time)

        val records = listOf(
            WeightRecord(
                time = time,
                zoneOffset = zoneOffset,
                weight = Mass.kilograms(m.weightKg),
                metadata = Metadata.manualEntry(),
            ),
            BodyFatRecord(
                time = time,
                zoneOffset = zoneOffset,
                percentage = Percentage(m.bodyFatPercent),
                metadata = Metadata.manualEntry(),
            ),
            BasalMetabolicRateRecord(
                time = time,
                zoneOffset = zoneOffset,
                basalMetabolicRate = Power.kilocaloriesPerDay(m.restingMetabolismKcal.toDouble()),
                metadata = Metadata.manualEntry(),
            ),
            LeanBodyMassRecord(
                time = time,
                zoneOffset = zoneOffset,
                mass = Mass.kilograms(m.leanBodyMassKg),
                metadata = Metadata.manualEntry(),
            ),
        )

        client.insertRecords(records)
    }
}
