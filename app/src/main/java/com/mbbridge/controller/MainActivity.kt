package com.mbbridge.controller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mbbridge.controller.ui.theme.MBBridgeControllerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MBBridgeControllerTheme {
                MBBridgeScreen()
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MBBridgeScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = context.getString(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            val isWide = maxWidth > 900.dp
            val isExtraWide = maxWidth > 1100.dp
            if (isWide) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.weight(if (isExtraWide) 0.9f else 1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ServerCard(
                            isRunning = uiState.isServerRunning,
                            portText = uiState.portText,
                            onPortChange = viewModel::updatePortText,
                            onApplyPort = viewModel::applyPort,
                            onStart = viewModel::startServer,
                            onStop = viewModel::stopServer
                        )
                        SimulateCard(
                            onSimulatePrev = { viewModel.simulateCommand(CommandType.PREV) },
                            onSimulateNext = { viewModel.simulateCommand(CommandType.NEXT) }
                        )
                        SettingsCard(
                            token = uiState.token,
                            onSaveToken = viewModel::saveToken,
                            onOpenAccessibility = viewModel::openAccessibilitySettings
                        )
                    }
                    Column(
                        modifier = Modifier.weight(if (isExtraWide) 0.9f else 1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LastCommandCard(command = uiState.lastCommand)
                        StatsCard(stats = uiState.stats)
                    }
                    Column(
                        modifier = Modifier.weight(if (isExtraWide) 1.2f else 1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LogToggleCard(
                            enabled = uiState.logsEnabled,
                            onToggle = viewModel::setLogsEnabled
                        )
                        LogPanel(
                            enabled = uiState.logsEnabled,
                            logs = uiState.logs,
                            onClear = viewModel::clearLogs
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item {
                        ServerCard(
                            isRunning = uiState.isServerRunning,
                            portText = uiState.portText,
                            onPortChange = viewModel::updatePortText,
                            onApplyPort = viewModel::applyPort,
                            onStart = viewModel::startServer,
                            onStop = viewModel::stopServer
                        )
                    }
                    item { LastCommandCard(command = uiState.lastCommand) }
                    item { StatsCard(stats = uiState.stats) }
                    item {
                        SimulateCard(
                            onSimulatePrev = { viewModel.simulateCommand(CommandType.PREV) },
                            onSimulateNext = { viewModel.simulateCommand(CommandType.NEXT) }
                        )
                    }
                    item {
                        SettingsCard(
                            token = uiState.token,
                            onSaveToken = viewModel::saveToken,
                            onOpenAccessibility = viewModel::openAccessibilitySettings
                        )
                    }
                    item {
                        LogToggleCard(
                            enabled = uiState.logsEnabled,
                            onToggle = viewModel::setLogsEnabled
                        )
                    }
                    if (uiState.logsEnabled) {
                        item { LogCard(logs = uiState.logs, onClear = viewModel::clearLogs) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCard(
    isRunning: Boolean,
    portText: String,
    onPortChange: (String) -> Unit,
    onApplyPort: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = context.getString(R.string.server_status),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isRunning) context.getString(R.string.server_running)
                else context.getString(R.string.server_stopped),
                style = MaterialTheme.typography.headlineSmall,
                color = if (isRunning) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "监听地址: 127.0.0.1:${portText.ifBlank { "27123" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = portText,
                onValueChange = onPortChange,
                label = { Text(context.getString(R.string.port_settings)) },
                placeholder = { Text(context.getString(R.string.port_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onApplyPort,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.apply_port))
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = if (isRunning) onStop else onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isRunning) context.getString(R.string.stop_server)
                    else context.getString(R.string.start_server)
                )
            }
        }
    }
}

@Composable
private fun LastCommandCard(command: Command?) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.last_command),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (command == null) {
                Text(
                    text = context.getString(R.string.no_command),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val ts = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", command.ts)
                Text("类型: ${command.getCommandType()}")
                Text("值 (v): ${command.v}")
                Text("时间: $ts")
                Text("来源: ${command.source}")
            }
        }
    }
}

@Composable
private fun StatsCard(stats: CommandStats) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.stats),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "PREV", value = stats.prevCount.toString())
                StatItem(label = "NEXT", value = stats.nextCount.toString())
                StatItem(label = "TOTAL", value = stats.totalCount.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SimulateCard(
    onSimulatePrev: () -> Unit,
    onSimulateNext: () -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.simulate),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onSimulatePrev,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(context.getString(R.string.simulate_prev))
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onSimulateNext,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(context.getString(R.string.simulate_next))
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    token: String,
    onSaveToken: (String) -> Unit,
    onOpenAccessibility: () -> Unit
) {
    val context = LocalContext.current
    var tokenInput by remember(token) { mutableStateOf(token) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = context.getString(R.string.token_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = tokenInput,
                onValueChange = { tokenInput = it },
                placeholder = { Text(context.getString(R.string.token_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onSaveToken(tokenInput) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.save_token))
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenAccessibility,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(context.getString(R.string.open_accessibility))
            }
        }
    }
}

@Composable
private fun LogCard(logs: List<String>, onClear: () -> Unit) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.log_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (logs.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onClear) {
                            Text(context.getString(R.string.clear_logs))
                        }
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val content = logs.joinToString("\n")
                                clipboard.setPrimaryClip(ClipData.newPlainText("MBBridgeLogs", content))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.logs_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        ) {
                            Text(context.getString(R.string.copy_logs))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Text(
                    text = context.getString(R.string.no_logs),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(520.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs.take(200)) { log ->
                        Text(text = log, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogPanel(
    enabled: Boolean,
    logs: List<String>,
    onClear: () -> Unit
) {
    if (enabled) {
        LogCard(logs = logs, onClear = onClear)
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "日志未开启",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "打开日志开关后，将显示协议与关键交互日志。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LogToggleCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = context.getString(R.string.log_toggle),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (enabled) context.getString(R.string.log_on)
                    else context.getString(R.string.log_off),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
