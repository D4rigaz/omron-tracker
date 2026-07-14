package com.darigaz.omrontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.darigaz.omrontracker.ui.HistoryScreen
import com.darigaz.omrontracker.ui.MeasurementFormScreen
import com.darigaz.omrontracker.ui.MeasurementViewModel
import com.darigaz.omrontracker.ui.SettingsScreen
import com.darigaz.omrontracker.ui.TrendChartScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MeasurementViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launcher do fluxo de permissões do Health Connect
        val requestPermissions = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { granted ->
            // granted: Set<String> com as permissões concedidas
            if (granted.containsAll(viewModel.healthConnect.permissions)) {
                viewModel.syncPending()
            }
        }

        setContent {
            MaterialTheme {
                var tab by remember { mutableIntStateOf(0) }
                var hasPermissions by remember { mutableStateOf(false) }
                var hcAvailable by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    hcAvailable = viewModel.healthConnect.isAvailable()
                    if (hcAvailable) {
                        hasPermissions = viewModel.healthConnect.hasAllPermissions()
                    }
                }

                Scaffold { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {

                        if (hcAvailable && !hasPermissions) {
                            Button(
                                onClick = {
                                    requestPermissions.launch(viewModel.healthConnect.permissions)
                                },
                                modifier = Modifier.padding(16.dp),
                            ) {
                                Text("Conceder permissões do Health Connect")
                            }
                        }

                        if (!hcAvailable) {
                            Text(
                                "Health Connect indisponível neste aparelho. " +
                                    "Os dados serão salvos apenas localmente.",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }

                        TabRow(selectedTabIndex = tab) {
                            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Nova medição") })
                            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Histórico") })
                            Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Tendência") })
                            Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Config") })
                        }

                        when (tab) {
                            0 -> MeasurementFormScreen(viewModel)
                            1 -> HistoryScreen(viewModel)
                            2 -> TrendChartScreen(viewModel)
                            3 -> SettingsScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}
