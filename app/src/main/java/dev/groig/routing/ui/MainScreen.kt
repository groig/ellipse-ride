@file:OptIn(ExperimentalMaterial3Api::class)

package dev.groig.routing.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.groig.routing.BROUTER_PACKAGE
import dev.groig.routing.GeneratedRoute
import dev.groig.routing.LatLon
import dev.groig.routing.MainViewModel
import dev.groig.routing.Pass
import dev.groig.routing.Profile
import dev.groig.routing.RouteGenerator
import dev.groig.routing.SavedPoint
import dev.groig.routing.Slot
import dev.groig.routing.UiState
import dev.groig.routing.currentLocation
import dev.groig.routing.haversine
import dev.groig.routing.openInOsmAnd
import dev.groig.routing.openStore
import dev.groig.routing.parseLatLon
import dev.groig.routing.shareGpx
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_KM = 5
private const val MAX_KM = 200

@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var sheetFor by remember { mutableStateOf<Slot?>(null) }
    var editing by remember { mutableStateOf<Slot?>(null) }
    var picking by remember { mutableStateOf<Slot?>(null) }
    var locating by remember { mutableStateOf(false) }
    var locatingSlot by remember { mutableStateOf<Slot?>(null) }
    var pendingLocate by remember { mutableStateOf<((LatLon) -> Unit)?>(null) }

    fun locate(onFound: (LatLon) -> Unit) {
        scope.launch {
            locating = true
            val here = currentLocation(context)
            locating = false
            locatingSlot = null
            if (here != null) {
                onFound(here)
            } else {
                snackbar.showSnackbar("Couldn't get a location fix. Is location turned on?")
            }
        }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val onFound = pendingLocate
        pendingLocate = null
        if (onFound != null && granted.values.any { it }) locate(onFound) else locatingSlot = null
    }

    fun requestLocate(onFound: (LatLon) -> Unit) {
        val granted = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (granted) {
            locate(onFound)
        } else {
            pendingLocate = onFound
            permissions.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            )
        }
    }

    fun locateInto(slot: Slot) {
        locatingSlot = slot
        requestLocate { here -> vm.setPoint(slot, here, "My location") }
    }

    picking?.let { slot ->
        val current = if (slot == Slot.A) state.pointA else state.pointB
        val other = if (slot == Slot.A) state.pointB else state.pointA
        MapPicker(
            slot = slot,
            start = current?.position ?: other?.position ?: state.places.firstOrNull()?.position,
            onLocate = ::requestLocate,
            locating = locating,
            onCancel = { picking = null },
            onPick = { position, name ->
                vm.setPoint(slot, position, name.ifBlank { "Picked on the map" }, remember = name.isNotBlank())
                picking = null
            },
        )
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            GenerateBar(
                state = state,
                onGenerate = vm::generate,
                onCancel = vm::cancel,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { Hero() }
            if (!state.brouterInstalled) {
                item { BRouterMissingCard(onInstall = { openStore(context, BROUTER_PACKAGE) }) }
            }
            item {
                PointsCard(
                    a = state.pointA,
                    b = state.pointB,
                    locating = locatingSlot,
                    onEdit = { sheetFor = it },
                    onLocate = ::locateInto,
                    onSwap = vm::swap,
                )
            }
            item {
                DistanceCard(
                    state = state,
                    onTarget = vm::setTarget,
                    onProfile = vm::setProfile,
                )
            }
            if (state.working || (state.passes.isNotEmpty() && state.route == null)) {
                item { ProgressCard(state.passes, state.working) }
            }
            state.error?.let { message ->
                item { ErrorCard(message, onDismiss = vm::dismissError) }
            }
            val route = state.route
            if (route != null) {
                item {
                    RouteCard(
                        route = route,
                        onOsmAnd = {
                            if (!openInOsmAnd(context, route)) {
                                scope.launch { snackbar.showSnackbar("OsmAnd isn't installed; pick an app instead.") }
                                shareGpx(context, route)
                            }
                        },
                        onShare = { shareGpx(context, route) },
                    )
                }
            } else if (!state.working && state.error == null) {
                item { HintCard() }
            }
        }
    }

    editing?.let { slot ->
        PointDialog(
            slot = slot,
            initial = if (slot == Slot.A) state.pointA else state.pointB,
            onDismiss = { editing = null },
            onSave = { position, label ->
                vm.setPoint(slot, position, label, remember = true)
                editing = null
            },
        )
    }

    sheetFor?.let { slot ->
        PointSheet(
            slot = slot,
            places = state.places,
            other = (if (slot == Slot.A) state.pointB else state.pointA)?.position,
            locating = locatingSlot == slot,
            onDismiss = { sheetFor = null },
            onMap = {
                sheetFor = null
                picking = slot
            },
            onLocate = {
                sheetFor = null
                locateInto(slot)
            },
            onCoordinates = {
                sheetFor = null
                editing = slot
            },
            onPlace = { place ->
                vm.usePlace(slot, place)
                sheetFor = null
            },
            onDelete = vm::deletePlace,
        )
    }

    state.incomingPoint?.let { point ->
        AlertDialog(
            onDismissRequest = { vm.offerIncoming(null) },
            icon = { Icon(Icons.Rounded.Place, contentDescription = null) },
            title = { Text("Use this place?") },
            text = { Text("${point.format()}\n\nSave it as your start (A) or your finish (B)?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.setPoint(Slot.B, point, "Shared place")
                    vm.offerIncoming(null)
                }) { Text("Set as B") }
            },
            dismissButton = {
                TextButton(onClick = {
                    vm.setPoint(Slot.A, point, "Shared place")
                    vm.offerIncoming(null)
                }) { Text("Set as A") }
            },
        )
    }
}

@Composable
private fun Hero() {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0E6B4F), Color(0xFF1F8A70), Color(0xFFE98A5F))))
            .padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        Column {
            Icon(
                Icons.Rounded.DirectionsBike,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Ellipse Ride",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Random bike rides of just the right length, routed offline by BRouter.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BRouterMissingCard(onInstall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("BRouter is needed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Routes are calculated on your phone by BRouter. Install it, open it once and download " +
                    "the map segments for your area.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onInstall) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Get BRouter")
            }
        }
    }
}

@Composable
private fun PointsCard(
    a: SavedPoint?,
    b: SavedPoint?,
    locating: Slot?,
    onEdit: (Slot) -> Unit,
    onLocate: (Slot) -> Unit,
    onSwap: () -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(vertical = 16.dp)) {
            Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("Start and finish", Icons.Rounded.Place) }
            Spacer(Modifier.height(8.dp))
            PointRow(Slot.A, a, MaterialTheme.colorScheme.primary, locating == Slot.A, onEdit, onLocate)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 72.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f))
                IconButton(onClick = onSwap, enabled = a != null || b != null) {
                    Icon(Icons.Rounded.SwapVert, contentDescription = "Swap A and B")
                }
            }
            PointRow(Slot.B, b, MaterialTheme.colorScheme.secondary, locating == Slot.B, onEdit, onLocate)
            if (a != null && b != null) {
                val km = haversine(a.position, b.position) / 1000
                Text(
                    if (km < 0.1) "Same place: you'll get a loop." else "%.1f km apart as the crow flies".format(km),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 72.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PointRow(
    slot: Slot,
    point: SavedPoint?,
    color: Color,
    locating: Boolean,
    onEdit: (Slot) -> Unit,
    onLocate: (Slot) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onEdit(slot) }
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(slot.name, color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                point?.label ?: if (slot == Slot.A) "Set a start" else "Set a finish",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                point?.position?.format() ?: "Tap to choose a place",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = if (point != null) FontFamily.Monospace else FontFamily.Default,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (locating) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            }
        } else {
            IconButton(onClick = { onLocate(slot) }) {
                Icon(Icons.Rounded.MyLocation, contentDescription = "Use my location for ${slot.name}")
            }
        }
        IconButton(onClick = { onEdit(slot) }) {
            Icon(Icons.Rounded.Edit, contentDescription = "Edit ${slot.name}")
        }
    }
}

@Composable
private fun DistanceCard(state: UiState, onTarget: (Int) -> Unit, onProfile: (Profile) -> Unit) {
    ElevatedCard {
        Column(Modifier.padding(20.dp)) {
            SectionTitle("How far", Icons.Rounded.Straighten)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(
                    onClick = { onTarget((state.targetKm - 1).coerceAtLeast(MIN_KM)) },
                    enabled = state.targetKm > MIN_KM,
                ) { Icon(Icons.Rounded.Remove, contentDescription = "Shorter") }
                Row(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        state.targetKm.toString(),
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        " km",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                FilledTonalIconButton(
                    onClick = { onTarget((state.targetKm + 1).coerceAtMost(MAX_KM)) },
                    enabled = state.targetKm < MAX_KM,
                ) { Icon(Icons.Rounded.Add, contentDescription = "Longer") }
            }
            val lo = state.targetKm * (1 - RouteGenerator.TOLERANCE)
            val hi = state.targetKm * (1 + RouteGenerator.TOLERANCE)
            Text(
                "Aiming for %.1f to %.1f km".format(lo, hi),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Slider(
                value = state.targetKm.toFloat(),
                onValueChange = { onTarget(it.roundToInt()) },
                valueRange = MIN_KM.toFloat()..MAX_KM.toFloat(),
            )
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Profile.entries.forEachIndexed { i, profile ->
                    SegmentedButton(
                        selected = state.profile == profile,
                        onClick = { onProfile(profile) },
                        shape = SegmentedButtonDefaults.itemShape(i, Profile.entries.size),
                    ) { Text(profile.label) }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(passes: List<Pass>, working: Boolean) {
    ElevatedCard(Modifier.animateContentSize()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (working) "Fitting the ellipse…" else "No route found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            AnimatedVisibility(working) {
                LinearProgressIndicator(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clip(CircleShape),
                )
            }
            if (passes.isEmpty()) {
                Text(
                    "Connecting to BRouter",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            passes.takeLast(6).forEach { pass -> PassRow(pass) }
        }
    }
}

@Composable
private fun PassRow(pass: Pass) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Try ${pass.attempt}, pass ${pass.pass}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        val length = pass.lengthMeters
        if (length != null) {
            Text(
                "%.1f km".format(length / 1000) +
                    (pass.overlap?.let { "  ·  %.0f%% repeated".format(it * 100) } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
            )
        } else {
            Text(
                pass.error ?: "failed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("BRouter couldn't make a route", fontWeight = FontWeight.SemiBold)
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss") }
        }
    }
}

@Composable
private fun RouteCard(
    route: GeneratedRoute,
    onOsmAnd: () -> Unit,
    onShare: () -> Unit,
) {
    ElevatedCard {
        Column(Modifier.padding(12.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(20.dp),
            ) {
                RouteMap(
                    route = route,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Distance", "%.1f km".format(route.lengthMeters / 1000), Modifier.weight(1f))
                val pct = route.deviation * 100
                Stat(
                    "Off target",
                    String.format(Locale.US, "%+.1f%%", pct),
                    Modifier.weight(1f),
                    highlight = if (route.withinTolerance) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Stat("Climb", route.ascendMeters?.let { "$it m" } ?: "–", Modifier.weight(1f))
                Stat(
                    "Repeated",
                    "%.0f%%".format(route.overlap * 100),
                    Modifier.weight(1f),
                    highlight = if (route.overlap > 0.15) MaterialTheme.colorScheme.tertiary else null,
                )
            }
            val notes = buildList<String> {
                if (route.trimmedMeters >= 50) {
                    add("Cut %.1f km of dead-end detours from BRouter's track.".format(route.trimmedMeters / 1000))
                }
                if (route.withinTolerance) add("Found in ${route.passes} routing passes.")
            }
            if (notes.isNotEmpty()) {
                Text(
                    notes.joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                )
            }
            if (!route.withinTolerance) {
                Text(
                    "This is the closest of ${route.passes} tries. " +
                        if (abs(route.deviation) > 0.3) "The target may be too short for these two points." else "Generate again for a better fit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOsmAnd, modifier = Modifier.weight(1f), contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Open in OsmAnd")
                }
                OutlinedButton(onClick = onShare, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, highlight: Color? = null) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = highlight ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HintCard() {
    Text(
        "Pick a start and a finish (the same spot gives you a loop), choose a distance and tap Generate. " +
            "Via points are scattered on an ellipse around A and B, and it grows or shrinks until the ride " +
            "comes out within 5% of your target.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun GenerateBar(state: UiState, onGenerate: () -> Unit, onCancel: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.working) {
                Button(onClick = {}, enabled = false, modifier = Modifier.weight(1f).height(56.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Routing…")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.height(56.dp)) { Text("Cancel") }
            } else {
                Button(
                    onClick = onGenerate,
                    enabled = state.canGenerate,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(
                        if (state.route != null) Icons.Rounded.Refresh else Icons.Rounded.DirectionsBike,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.route != null) "Try another route" else "Generate route",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PointDialog(
    slot: Slot,
    initial: SavedPoint?,
    onDismiss: () -> Unit,
    onSave: (LatLon, String) -> Unit,
) {
    var coords by remember { mutableStateOf(initial?.position?.format() ?: "") }
    var label by remember { mutableStateOf(initial?.label ?: "") }
    val parsed = parseLatLon(coords)
    val showError = coords.isNotBlank() && parsed == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (slot == Slot.A) "Start (A)" else "Finish (B)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Name (optional)") },
                    placeholder = { Text("Home") },
                    supportingText = { Text("Named places are saved for next time") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it },
                    label = { Text("Latitude, longitude") },
                    placeholder = { Text("52.37403, 4.88969") },
                    isError = showError,
                    supportingText = {
                        Text(if (showError) "Couldn't read that" else "A \"geo:\" link works too")
                    },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let { onSave(it, label) } }, enabled = parsed != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
