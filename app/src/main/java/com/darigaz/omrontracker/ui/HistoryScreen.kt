package com.darigaz.omrontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darigaz.omrontracker.data.Measurement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(viewModel: MeasurementViewModel) {
    val history by viewModel.history.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(history, key = { it.id }) { m ->
            MeasurementCard(m)
        }
    }
}

@Composable
private fun MeasurementCard(m: Measurement) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatter.format(Instant.ofEpochMilli(m.timestamp)) +
                    if (m.syncedToHealthConnect) "  ✓ HC" else "  ⏳ pendente",
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                "Peso ${m.weightKg} kg · Gordura ${m.bodyFatPercent}% · Músculo ${m.skeletalMusclePercent}%",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "IMC ${m.bmi} · Visceral ${m.visceralFatLevel} · ${m.restingMetabolismKcal} kcal · Idade corporal ${m.bodyAge}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
