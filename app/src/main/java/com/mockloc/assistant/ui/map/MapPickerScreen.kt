package com.mockloc.assistant.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mockloc.assistant.data.map.MapTileSources
import com.mockloc.assistant.data.model.MapStyle
import com.mockloc.assistant.data.model.MockPoint
import com.mockloc.assistant.data.search.PlaceResult
import com.mockloc.assistant.data.search.PlaceSearchService
import com.mockloc.assistant.data.search.SearchOutcome
import com.mockloc.assistant.ui.home.HomeViewModel
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapPickerScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    val searchResults = remember { mutableStateListOf<PlaceResult>() }
    var showResults by remember { mutableStateOf(false) }

    var selectedLat by remember { mutableDoubleStateOf(state.point?.latitude ?: 39.9042) }
    var selectedLng by remember { mutableDoubleStateOf(state.point?.longitude ?: 116.4074) }
    var mapRef by remember { mutableStateOf<MapView?>(null) }
    var lastScrollTime by remember { mutableStateOf(0L) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("选择位置") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; showResults = false; searchResults.clear() },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索地点...") },
                singleLine = true,
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (query.isNotBlank()) {
                        searching = true
                        showResults = false
                        val searchService = PlaceSearchService()
                        scope.launch {
                            val outcome = searchService.search(query, limit = 12)
                            when (outcome) {
                                is SearchOutcome.Success -> {
                                    searchResults.clear()
                                    searchResults.addAll(outcome.results)
                                    showResults = true
                                }
                                is SearchOutcome.Failure -> {
                                    searchResults.clear()
                                    showResults = false
                                }
                            }
                            searching = false
                        }
                    }
                },
            ) {
                Text("搜索")
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(MapTileSources.OSM_STANDARD)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                        setTilesScaledToDpi(true)
                        controller.setZoom(12.0)
                        controller.setCenter(GeoPoint(selectedLat, selectedLng))
                        mapRef = this

                        addMapListener(object : org.osmdroid.events.MapListener {
                            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                                val now = System.currentTimeMillis()
                                if (now - lastScrollTime > 300) {
                                    lastScrollTime = now
                                    val center = mapCenter
                                    if (center != null) {
                                        selectedLat = center.latitude
                                        selectedLng = center.longitude
                                    }
                                }
                                return true
                            }
                            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                                val now = System.currentTimeMillis()
                                if (now - lastScrollTime > 300) {
                                    lastScrollTime = now
                                    val center = mapCenter
                                    if (center != null) {
                                        selectedLat = center.latitude
                                        selectedLng = center.longitude
                                    }
                                }
                                return true
                            }
                        })
                    }
                },
                update = { map ->
                    map.postDelayed({
                        val center = map.mapCenter
                        if (center != null) {
                            selectedLat = center.latitude
                            selectedLng = center.longitude
                        }
                    }, 500)
                },
            )

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "★",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (showResults && searchResults.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .align(Alignment.TopStart),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    LazyColumn {
                        items(searchResults) { result ->
                            SearchResultRow(
                                result = result,
                                onClick = {
                                    selectedLat = result.latitude
                                    selectedLng = result.longitude
                                    showResults = false
                                    mapRef?.let { map ->
                                        map.controller.animateTo(GeoPoint(result.latitude, result.longitude), 14.0, 300)
                                        map.overlays.clear()
                                        val marker = Marker(map)
                                        marker.position = GeoPoint(result.latitude, result.longitude)
                                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        marker.title = result.displayName
                                        map.overlays.add(marker)
                                        map.invalidate()
                                    }
                                },
                            )
                            Divider()
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
        ) {
            Text(
                "选中坐标: ${String.format("%.6f", selectedLat)}, ${String.format("%.6f", selectedLng)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    viewModel.selectPoint(
                        MockPoint(
                            latitude = selectedLat,
                            longitude = selectedLng,
                            name = query.ifBlank { "地图选点" },
                            address = "${String.format("%.4f", selectedLat)}, ${String.format("%.4f", selectedLng)}",
                        ),
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("确认选择此位置")
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: PlaceResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(result.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                result.displayName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}
