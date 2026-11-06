package com.mockloc.assistant.ui.places

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mockloc.assistant.ui.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(viewModel: HomeViewModel) {
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    val history by viewModel.history.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var placeToDelete by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("常用地点") },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("收藏") },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("历史") },
            )
        }

        if (selectedTab == 0) {
            if (savedPlaces.isEmpty()) {
                EmptyState("暂无收藏地点", "在首页选择位置后可以保存到这里")
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(savedPlaces) { place ->
                        PlaceItem(
                            title = place.name,
                            subtitle = "${place.address} · ${String.format("%.5f, %.5f", place.latitude, place.longitude)}",
                            onSelect = {
                                viewModel.selectPoint(
                                    com.mockloc.assistant.data.model.MockPoint(
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        name = place.name,
                                        address = place.address,
                                    ),
                                )
                            },
                            onDelete = { placeToDelete = place.id },
                        )
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                if (history.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.clearHistory() },
                        modifier = Modifier.padding(16.dp),
                    ) {
                        Text("清空历史")
                    }
                }
                if (history.isEmpty()) {
                    EmptyState("暂无历史记录", "使用过的地点会显示在这里")
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(history) { entry ->
                            PlaceItem(
                                title = entry.name,
                                subtitle = "${entry.address} · ${String.format("%.5f, %.5f", entry.latitude, entry.longitude)}",
                                onSelect = {
                                    viewModel.selectPoint(
                                        com.mockloc.assistant.data.model.MockPoint(
                                            latitude = entry.latitude,
                                            longitude = entry.longitude,
                                            name = entry.name,
                                            address = entry.address,
                                        ),
                                    )
                                },
                                showDelete = false,
                            )
                        }
                    }
                }
            }
        }
    }

    placeToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { placeToDelete = null },
            title = { Text("删除地点") },
            text = { Text("确定要删除这个收藏地点吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePlace(id)
                    placeToDelete = null
                }) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { placeToDelete = null }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun PlaceItem(
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
    showDelete: Boolean = true,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Place,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (showDelete && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
