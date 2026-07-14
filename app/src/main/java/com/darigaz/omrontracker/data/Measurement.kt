package com.darigaz.omrontracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Uma medição completa da Omron HBF-514C.
 *
 * Todos os 7 campos que a balança exibe. Os campos que não têm tipo nativo
 * no Health Connect (visceral, % músculo esquelético, idade corporal, IMC)
 * ficam apenas no histórico local (Room).
 */
@Entity(tableName = "measurements")
data class Measurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Momento da medição (epoch millis). */
    val timestamp: Long = Instant.now().toEpochMilli(),

    /** Peso em kg. Ex.: 128.4 */
    val weightKg: Double,

    /** IMC calculado pela balança. Ex.: 37.1 */
    val bmi: Double,

    /** Gordura corporal em %. Ex.: 34.5 */
    val bodyFatPercent: Double,

    /** Músculo esquelético em %. Ex.: 29.8 */
    val skeletalMusclePercent: Double,

    /** Nível de gordura visceral (escala Omron 1–30). Ex.: 15 */
    val visceralFatLevel: Int,

    /** Metabolismo de repouso em kcal/dia. Ex.: 2210 */
    val restingMetabolismKcal: Int,

    /** Idade corporal estimada pela balança. Ex.: 52 */
    val bodyAge: Int,

    /** Se esta medição já foi sincronizada com o Health Connect. */
    val syncedToHealthConnect: Boolean = false,

    /** Se esta medição já foi enviada ao repositório GitHub (measurements.json). */
    val syncedToGitHub: Boolean = false,
) {
    /** Massa magra derivada: peso × (1 − %gordura). Usada no LeanBodyMassRecord. */
    val leanBodyMassKg: Double
        get() = weightKg * (1.0 - bodyFatPercent / 100.0)
}
