package org.wit.fightfinder.views.map

import org.wit.fightfinder.FightFinderApplication
import org.wit.fightfinder.activities.gymStore
import org.wit.fightfinder.views.auth.openAuth

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.ActivityGymMapListBinding
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.showGymPhoto
import org.wit.fightfinder.models.Coordinates

class GymMapListActivity : AppCompatActivity(), GymMapListPresenter.View {
    private lateinit var binding: ActivityGymMapListBinding
    private val presenter by lazy { GymMapListPresenter(gymStore, this) }
    private var hasPins = false
    private var positioned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((application as FightFinderApplication).signedInUid == null) {
            openAuth()
            return
        }
        binding = ActivityGymMapListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setTitle(R.string.all_gyms_map_title)
            setDisplayHomeAsUpEnabled(true)
        }
        binding.map.apply {
            setDestroyMode(false)
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
        }
        val selectedId = savedInstanceState?.takeIf { it.containsKey("selectedGym") }?.getLong("selectedGym")
        if (savedInstanceState?.containsKey("zoom") == true) {
            binding.map.controller.setZoom(savedInstanceState.getDouble("zoom"))
            binding.map.controller.setCenter(GeoPoint(savedInstanceState.getDouble("lat"), savedInstanceState.getDouble("lon")))
            positioned = true
        }
        lifecycleScope.launch { presenter.loadAndObserve(selectedId) }
    }

    override fun showPins(locations: Map<Coordinates, List<Gym>>) {
        val map = binding.map
        map.overlays.clear()
        hasPins = locations.isNotEmpty()
        map.isVisible = hasPins
        binding.attribution.isVisible = hasPins
        if (!hasPins) {
            positioned = false
            return
        }

        for ((point, gymsAtPin) in locations) {
            val marker = Marker(map).apply {
                position = GeoPoint(point.latitude, point.longitude)
                title = gymsAtPin.first().name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    presenter.onPinSelected(gymsAtPin)
                    true
                }
            }
            map.overlays.add(marker)
        }
        map.invalidate()
        if (!positioned) {
            map.doOnLayout {
                val points = locations.keys.map { GeoPoint(it.latitude, it.longitude) }
                if (points.size == 1) {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(points.first())
                } else {
                    val padding = (32 * resources.displayMetrics.density).toInt()
                    map.zoomToBoundingBox(BoundingBox.fromGeoPointsSafe(points), false, padding, 16.0, null)
                }
                positioned = true
            }
        }
    }

    override fun chooseGymAtPin(gyms: List<Gym>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.gyms_at_pin)
            .setItems(gyms.map { it.name }.toTypedArray()) { _, index ->
                presenter.onGymSelected(gyms[index].id)
            }
            .show()
    }

    override fun showLoadError() {
        showMessage(R.string.gym_load_failed)
        binding.map.isVisible = false
        binding.attribution.isVisible = false
    }

    override fun showSelection(gym: Gym?) {
        if (gym == null) {
            showMessage(if (hasPins) R.string.gym_map_select else R.string.gym_map_empty)
            return
        }
        binding.gymName.text = gym.name
        binding.summary.isVisible = true
        binding.summary.text = gym.location.ifBlank {
            gym.classes.ifBlank { getString(R.string.gym_pin_details, gym.latitude, gym.longitude) }
        }
        binding.photo.isVisible = true
        binding.photo.showGymPhoto(gym.imageUri)
    }

    private fun showMessage(message: Int) {
        binding.gymName.setText(message)
        binding.summary.isVisible = false
        binding.photo.isVisible = false
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!::binding.isInitialized) return
        presenter.selectedId?.let { outState.putLong("selectedGym", it) }
        if (positioned) {
            outState.putDouble("zoom", binding.map.zoomLevelDouble)
            outState.putDouble("lat", binding.map.mapCenter.latitude)
            outState.putDouble("lon", binding.map.mapCenter.longitude)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized) binding.map.onResume()
    }

    override fun onPause() {
        if (::binding.isInitialized) binding.map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        if (::binding.isInitialized) binding.map.onDetach()
        super.onDestroy()
    }
}
