package dev.groig.routing

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Slot { A, B }

data class SavedPoint(val position: LatLon, val label: String)

data class UiState(
    val pointA: SavedPoint? = null,
    val pointB: SavedPoint? = null,
    val targetKm: Int = 40,
    val profile: Profile = Profile.Trekking,
    val brouterInstalled: Boolean = true,
    val working: Boolean = false,
    val passes: List<Pass> = emptyList(),
    val route: GeneratedRoute? = null,
    val error: String? = null,
    val incomingPoint: LatLon? = null,
) {
    val canGenerate: Boolean get() = pointA != null && pointB != null && !working && brouterInstalled
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("points", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(
        UiState(
            pointA = loadPoint(Slot.A),
            pointB = loadPoint(Slot.B),
            targetKm = prefs.getInt("target_km", 40),
            profile = Profile.entries.firstOrNull { it.name == prefs.getString("profile", null) }
                ?: Profile.Trekking,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var job: Job? = null

    fun refreshBRouter() {
        _state.update { it.copy(brouterInstalled = isBRouterInstalled(getApplication())) }
    }

    fun setPoint(slot: Slot, position: LatLon, label: String) {
        val point = SavedPoint(position, label.ifBlank { if (slot == Slot.A) "Start" else "Finish" })
        prefs.edit {
            putString("${slot.name}_lat", position.lat.toString())
            putString("${slot.name}_lon", position.lon.toString())
            putString("${slot.name}_label", point.label)
        }
        _state.update { if (slot == Slot.A) it.copy(pointA = point) else it.copy(pointB = point) }
    }

    fun swap() {
        val (a, b) = _state.value.let { it.pointA to it.pointB }
        if (b != null) setPoint(Slot.A, b.position, b.label)
        if (a != null) setPoint(Slot.B, a.position, a.label)
    }

    fun setTarget(km: Int) {
        prefs.edit { putInt("target_km", km) }
        _state.update { it.copy(targetKm = km) }
    }

    fun setProfile(profile: Profile) {
        prefs.edit { putString("profile", profile.name) }
        _state.update { it.copy(profile = profile) }
    }

    fun offerIncoming(position: LatLon?) = _state.update { it.copy(incomingPoint = position) }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun generate() {
        val s = _state.value
        val a = s.pointA ?: return
        val b = s.pointB ?: return
        job?.cancel()
        _state.update { it.copy(working = true, passes = emptyList(), error = null) }
        job = viewModelScope.launch {
            try {
                BRouterSession.open(getApplication()).use { session ->
                    val route = RouteGenerator(session).generate(
                        start = a.position,
                        end = b.position,
                        targetMeters = s.targetKm * 1000.0,
                        profile = s.profile,
                        onPass = { pass -> _state.update { it.copy(passes = it.passes + pass) } },
                    )
                    _state.update { it.copy(route = route) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }

    private fun loadPoint(slot: Slot): SavedPoint? {
        val lat = prefs.getString("${slot.name}_lat", null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString("${slot.name}_lon", null)?.toDoubleOrNull() ?: return null
        return SavedPoint(LatLon(lat, lon), prefs.getString("${slot.name}_label", null) ?: slot.name)
    }
}
