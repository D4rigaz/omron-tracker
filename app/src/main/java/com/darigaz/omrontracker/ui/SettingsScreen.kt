package com.darigaz.omrontracker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Configuração do sync com o repositório GitHub (privado) de dados.
 * O token deve ser um fine-grained PAT com "Contents: Read and write"
 * restrito ao repositório de dados.
 */
@Composable
fun SettingsScreen(viewModel: MeasurementViewModel) {
    val gh = viewModel.gitHub

    var owner by remember { mutableStateOf(gh.owner) }
    var repo by remember { mutableStateOf(gh.repo.ifBlank { "omron-data" }) }
    var token by remember { mutableStateOf(gh.token) }
    var status by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Sincronização com GitHub", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "As medições são gravadas no arquivo measurements.json de um " +
                "repositório PRIVADO, consultado pela página web do tracker.",
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = owner,
            onValueChange = { owner = it },
            label = { Text("Usuário/organização (owner)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = repo,
            onValueChange = { repo = it },
            label = { Text("Repositório de dados (privado)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Fine-grained PAT (Contents: read/write)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                gh.owner = owner
                gh.repo = repo
                gh.token = token
                status = if (gh.isConfigured()) {
                    viewModel.syncPending()
                    "Configuração salva. Enviando pendentes…"
                } else {
                    "Configuração salva (incompleta — sync desativado)."
                }
            },
        ) {
            Text("Salvar e sincronizar pendentes")
        }

        status?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}
