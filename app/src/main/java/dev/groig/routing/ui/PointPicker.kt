@file:OptIn(ExperimentalMaterial3Api::class)

package dev.groig.routing.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EditLocation
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.groig.routing.LatLon
import dev.groig.routing.Place
import dev.groig.routing.Slot
import dev.groig.routing.haversine
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay

private fun Slot.title() = if (this == Slot.A) "Start (A)" else "Finish (B)"

/** The sheet that opens from a point row: saved places plus the other ways to set it. */
@Composable
fun PointSheet(
    slot: Slot,
    places: List<Place>,
    other: LatLon?,
    locating: Boolean,
    onDismiss: () -> Unit,
    onMap: () -> Unit,
    onLocate: () -> Unit,
    onCoordinates: () -> Unit,
    onPlace: (Place) -> Unit,
    onDelete: (Place) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.padding(bottom = 16.dp)) {
            Text(
                slot.title(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetAction("On the map", Icons.Rounded.Map, Modifier.weight(1f), onClick = onMap)
                SheetAction("Where I am", Icons.Rounded.MyLocation, Modifier.weight(1f), busy = locating, onClick = onLocate)
                SheetAction("Coordinates", Icons.Rounded.EditLocation, Modifier.weight(1f), onClick = onCoordinates)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Saved places",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (places.isEmpty()) {
                Text(
                    "Give a place a name when you set it and it will be saved here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(places, key = { it.name }) { place ->
                        ListItem(
                            headlineContent = { Text(place.name, fontWeight = FontWeight.Medium) },
                            supportingContent = {
                                val away = other?.let { haversine(it, place.position) / 1000 }
                                Text(
                                    place.position.format() +
                                        (away?.let { "  ·  %.1f km from %s".format(it, if (slot == Slot.A) "B" else "A") } ?: ""),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                            leadingContent = {
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Bookmark,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { onDelete(place) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Forget ${place.name}")
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onPlace(place) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(icon, null)
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * Full-screen map with a fixed pin in the middle: drag the map (or tap a spot)
 * until the pin sits where you want, optionally name it, and confirm.
 */
@Composable
fun MapPicker(
    slot: Slot,
    start: LatLon?,
    onLocate: ((LatLon) -> Unit) -> Unit,
    locating: Boolean,
    onCancel: () -> Unit,
    onPick: (LatLon, String) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val map = rememberMapView()
    var name by remember { mutableStateOf("") }
    val pinColor = if (slot == Slot.A) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    val pinSize = 48.dp
    val pinLift = with(LocalDensity.current) { (pinSize / 2).roundToPx() }

    LaunchedEffect(map) {
        map.overlays.clear()
        map.overlays += MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                map.controller.animateTo(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        if (start != null) {
            map.controller.setZoom(16.0)
            map.controller.setCenter(start.toGeoPoint())
        } else {
            // Nothing to start from yet; the location button gets you home.
            map.controller.setZoom(4.0)
            map.controller.setCenter(GeoPoint(48.0, 10.0))
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())

        // The pin's tip marks the map centre.
        Icon(
            Icons.Rounded.Place,
            contentDescription = null,
            tint = pinColor,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { IntOffset(0, -pinLift) }
                .size(pinSize),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)),
        )

        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp),
        ) {
            Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Text("Pick ${slot.title()}", style = MaterialTheme.typography.titleMedium)
            }
        }

        Column(Modifier.align(Alignment.BottomCenter)) {
            SmallFloatingActionButton(
                onClick = {
                    onLocate { here ->
                        map.controller.setZoom(maxOf(map.zoomLevelDouble, 15.0))
                        map.controller.animateTo(here.toGeoPoint())
                    }
                },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(16.dp),
            ) {
                if (locating) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.MyLocation, contentDescription = "Go to my location")
                }
            }
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                tonalElevation = 3.dp,
                shadowElevation = 12.dp,
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(20.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name (optional)") },
                        supportingText = { Text("Named places are saved for next time") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { map.controller.zoomOut() }) { Icon(Icons.Rounded.Remove, contentDescription = "Zoom out") }
                        Spacer(Modifier.width(4.dp))
                        FilledTonalIconButton(onClick = { map.controller.zoomIn() }) { Icon(Icons.Rounded.Add, contentDescription = "Zoom in") }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val c = map.mapCenter
                                onPick(LatLon(c.latitude, c.longitude), name)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                        ) { Text("Use this spot", style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }
        }
    }
}
