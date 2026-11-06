package com.mockloc.assistant.ui.guide

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockloc.assistant.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(viewModel: HomeViewModel) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("使用指南") },
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
            GuideCard(
                title = "第 1 步：开启开发者选项",
                completed = state.prerequisites.developerOptions,
                content = {
                    Text(
                        "进入「设置 → 关于手机」，连续点击「版本号」7 次即可开启开发者选项。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
                        },
                    ) {
                        Text("打开关于手机")
                    }
                },
            )

            GuideCard(
                title = "第 2 步：授权定位权限",
                completed = state.prerequisites.locationPermission,
                content = {
                    Text(
                        "本应用需要「精确位置」权限才能正常工作。系统会在首次使用时弹出授权对话框。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
            )

            GuideCard(
                title = "第 3 步：选择模拟位置应用",
                completed = state.prerequisites.mockAppSelected,
                content = {
                    Text(
                        "进入「开发者选项」，找到「选择模拟位置信息应用」，将其设为本应用。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                        },
                    ) {
                        Text("打开开发者选项")
                    }
                },
            )

            GuideCard(
                title = "第 4 步：关闭电池优化（推荐）",
                completed = !state.prerequisites.batteryOptimized,
                content = {
                    Text(
                        "为避免系统在后台自动停止模拟，建议将本应用从电池优化中排除。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        },
                    ) {
                        Text("电池优化设置")
                    }
                },
            )

            Spacer(Modifier.height(8.dp))

            if (state.prerequisites.allSatisfied) {
                Text(
                    "所有准备工作已完成，可以开始使用！",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    "请按照以上步骤完成设置后即可使用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GuideCard(
    title: String,
    completed: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (completed) {
                    Text(
                        "已完成",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
