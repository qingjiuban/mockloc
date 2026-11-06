package com.mockloc.assistant.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockloc.assistant.core.GeoUtils
import com.mockloc.assistant.mock.MockSessionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: HomeViewModel, onOpenMapPicker: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.transientMessage) {
        state.transientMessage?.let { message ->
            kotlinx.coroutines.delay(2000)
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("虚拟定位助手") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(
                running = state.running,
                verified = (state.session as? MockSessionState.Running)?.verified == true,
                error = state.error,
            )

            LocationCard(
                point = state.point,
                onSelectLocation = onOpenMapPicker,
                onGetCurrentLocation = viewModel::refreshRealLocation,
                loadingReal = state.loadingRealLocation,
            )

            ControlCard(
                canStart = state.canStart,
                running = state.running,
                starting = state.starting,
                onToggle = viewModel::toggleMocking,
            )

            state.realLocation?.let { real ->
                RealLocationCard(real = real)
            }

            state.transientMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, verified: Boolean, error: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                error != null -> MaterialTheme.colorScheme.errorContainer
                running && verified -> MaterialTheme.colorScheme.primaryContainer
                running -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = when {
                    error != null -> "错误"
                    running && verified -> "运行中"
                    running -> "启动中"
                    else -> "未启动"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = error ?: if (running) "正在注入模拟位置" else "点击下方按钮开始模拟定位",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun LocationCard(
    point: com.mockloc.assistant.data.model.MockPoint?,
    onSelectLocation: () -> Unit,
    onGetCurrentLocation: () -> Unit,
    loadingReal: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "目标位置",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))

            if (point != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(point.displayName, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${point.address} · ${String.format("%.5f, %.5f", point.latitude, point.longitude)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    "尚未选择位置",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectLocation,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Place, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("在地图上选择")
                }

                IconButton(onClick = onGetCurrentLocation) {
                    if (loadingReal) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "获取当前位置")
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlCard(
    canStart: Boolean,
    running: Boolean,
    starting: Boolean,
    onToggle: () -> Unit,
) {
    Button(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        enabled = !starting && (canStart || running),
        colors = if (running) ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
        ) else ButtonDefaults.buttonColors(),
    ) {
        if (starting) {
            CircularProgressIndicator(
                Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(if (running) "停止模拟" else "开始模拟")
    }
}

@Composable
private fun RealLocationCard(real: com.mockloc.assistant.ui.state.RealLocationUi) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "当前真实位置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${String.format("%.5f, %.5f", real.latitude, real.longitude)} (±${real.accuracyMeters.toInt()}m)",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "来源: ${real.provider}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
