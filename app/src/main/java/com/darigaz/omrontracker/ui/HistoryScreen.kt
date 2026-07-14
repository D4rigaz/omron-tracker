package com.darigaz.omrontracker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.darigaz.omrontracker.data.Measurement
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(viewModel: MeasurementViewModel, onEdit: () -> Unit) {
    val history by viewModel.history.collectAsState()
    var confirmDelete by remember { mutableStateOf<Measurement?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(history, key = { it.id }) { m ->
            MeasurementCard(
                m = m,
                onEdit = { viewModel.startEdit(m); onEdit() },
                onDelete = { confirmDelete = m },
            )
        }
    }

    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Excluir medição?") },
            text = {
                Text(
                    "A medição de ${formatter.format(Instant.ofEpochMilli(m.timestamp))} " +
                        "será removida do app, do GitHub e do Health Connect. " +
                        "Essa ação não pode ser desfeita."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(m); confirmDelete = null }) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun MeasurementCard(
    m: Measurement,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                formatter.format(Instant.ofEpochMilli(m.timestamp)) +
                    (if (m.syncedToHealthConnect) "  ✓ HC" else "  ⏳ HC") +
                    (if (m.syncedToGitHub) "  ✓ GH" else "  ⏳ GH"),
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Editar") }
                TextButton(onClick = onDelete) { Text("Excluir") }
            }
        }
    }
}
