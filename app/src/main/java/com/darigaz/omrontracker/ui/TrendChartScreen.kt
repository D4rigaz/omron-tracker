package com.darigaz.omrontracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darigaz.omrontracker.data.Measurement
import java.util.Locale

/** Métricas plotáveis, com extrator e formatação. */
enum class Metric(
    val label: String,
    val unit: String,
    val extract: (Measurement) -> Double,
) {
    WEIGHT("Peso", "kg", { it.weightKg }),
    BODY_FAT("Gordura", "%", { it.bodyFatPercent }),
    SKELETAL_MUSCLE("Músculo", "%", { it.skeletalMusclePercent }),
    LEAN_MASS("Massa magra", "kg", { it.leanBodyMassKg }),
    VISCERAL("Visceral", "", { it.visceralFatLevel.toDouble() }),
    BMI("IMC", "", { it.bmi }),
    METABOLISM("Metab.", "kcal", { it.restingMetabolismKcal.toDouble() }),
    BODY_AGE("Idade corporal", "anos", { it.bodyAge.toDouble() }),
}

/** Resultado da regressão linear simples (mínimos quadrados) sobre (t, valor). */
data class Trend(val slopePerDay: Double, val intercept: Double)

fun linearRegression(points: List<Pair<Double, Double>>): Trend? {
    if (points.size < 2) return null
    val n = points.size.toDouble()
    val sumX = points.sumOf { it.first }
    val sumY = points.sumOf { it.second }
    val sumXY = points.sumOf { it.first * it.second }
    val sumX2 = points.sumOf { it.first * it.first }
    val denominator = n * sumX2 - sumX * sumX
    if (denominator == 0.0) return null
    val slope = (n * sumXY - sumX * sumY) / denominator
    val intercept = (sumY - slope * sumX) / n
    return Trend(slope, intercept)
}

@Composable
fun TrendChartScreen(viewModel: MeasurementViewModel) {
    val history by viewModel.history.collectAsState()
    var metric by remember { mutableStateOf(Metric.WEIGHT) }

    // Histórico chega DESC; para o gráfico queremos ASC no tempo
    val series = remember(history) { history.sortedBy { it.timestamp } }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Tendência", style = MaterialTheme.typography.titleLarge)

        // Seletor de métrica
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Metric.entries.forEach { m ->
                FilterChip(
                    selected = metric == m,
                    onClick = { metric = m },
                    label = { Text(m.label) },
                )
            }
        }

        when {
            series.isEmpty() -> Text("Sem medições ainda. Registre a primeira na aba \"Nova medição\".")
            series.size == 1 -> Text("Só uma medição registrada — a tendência aparece a partir da segunda.")
            else -> {
                TrendSummary(series, metric)
                LineChart(series, metric)
            }
        }
    }
}

@Composable
private fun TrendSummary(series: List<Measurement>, metric: Metric) {
    val first = metric.extract(series.first())
    val last = metric.extract(series.last())
    val delta = last - first

    val dayMillis = 86_400_000.0
    val t0 = series.first().timestamp
    val points = series.map { (it.timestamp - t0) / dayMillis to metric.extract(it) }
    val trend = linearRegression(points)
    val perWeek = trend?.slopePerDay?.times(7)

    val sign = if (delta >= 0) "+" else ""
    val summary = buildString {
        append("Atual: ${fmt(last)} ${metric.unit}".trim())
        append("  ·  Δ período: $sign${fmt(delta)}")
        if (perWeek != null) {
            val ws = if (perWeek >= 0) "+" else ""
            append("  ·  tendência: $ws${fmt(perWeek)}/semana")
        }
    }
    Text(summary, style = MaterialTheme.typography.bodyMedium)
}

private fun fmt(v: Double): String = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", v)

@Composable
private fun LineChart(series: List<Measurement>, metric: Metric) {
    val textMeasurer = rememberTextMeasurer()
    val lineColor = MaterialTheme.colorScheme.primary
    val trendColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val dayMillis = 86_400_000.0
    val t0 = series.first().timestamp
    val points = series.map { (it.timestamp - t0) / dayMillis to metric.extract(it) }
    val trend = linearRegression(points)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
    ) {
        val paddingLeft = 64f
        val paddingBottom = 24f
        val paddingTop = 16f
        val chartWidth = size.width - paddingLeft
        val chartHeight = size.height - paddingBottom - paddingTop

        val xs = points.map { it.first }
        val ys = points.map { it.second }
        val minX = xs.min()
        val maxX = xs.max()
        val rawMinY = ys.min()
        val rawMaxY = ys.max()
        // Margem vertical de 10% para o gráfico respirar
        val pad = ((rawMaxY - rawMinY).takeIf { it > 0 } ?: 1.0) * 0.10
        val minY = rawMinY - pad
        val maxY = rawMaxY + pad

        fun toPx(x: Double, y: Double): Offset {
            val px = paddingLeft + ((x - minX) / (maxX - minX).coerceAtLeast(0.0001) * chartWidth).toFloat()
            val py = paddingTop + ((maxY - y) / (maxY - minY) * chartHeight).toFloat()
            return Offset(px, py)
        }

        // Linhas de grade horizontais (4 divisões) com rótulos
        val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
        for (i in 0..4) {
            val yVal = minY + (maxY - minY) * i / 4.0
            val y = toPx(minX, yVal).y
            drawLine(
                color = gridColor,
                start = Offset(paddingLeft, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            drawText(
                textMeasurer = textMeasurer,
                text = fmt(yVal),
                style = labelStyle,
                topLeft = Offset(0f, y - 14f),
            )
        }

        // Série: segmentos + pontos
        val offsets = points.map { toPx(it.first, it.second) }
        for (i in 0 until offsets.size - 1) {
            drawLine(
                color = lineColor,
                start = offsets[i],
                end = offsets[i + 1],
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
        }
        offsets.forEach { drawCircle(color = lineColor, radius = 6f, center = it) }

        // Linha de tendência (regressão) tracejada
        trend?.let { t ->
            val start = toPx(minX, t.intercept + t.slopePerDay * minX)
            val end = toPx(maxX, t.intercept + t.slopePerDay * maxX)
            drawLine(
                color = trendColor,
                start = start,
                end = end,
                strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
        }
    }
}
