package org.wit.fightfinder.activities

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
import java.io.IOException

class GymMapListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGymMapListBinding
    private var selectedId: Long? = null
    private var positioned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        selectedId = savedInstanceState?.takeIf { it.containsKey("selectedGym") }?.getLong("selectedGym")
        if (savedInstanceState?.containsKey("zoom") == true) {
            binding.map.controller.setZoom(savedInstanceState.getDouble("zoom"))
            binding.map.controller.setCenter(GeoPoint(savedInstanceState.getDouble("lat"), savedInstanceState.getDouble("lon")))
            positioned = true
        }
        lifecycleScope.launch {
            try {
                gymStore.load()
                gymStore.gyms.collect { renderGyms(it) }
            } catch (error: IOException) {
                showMessage(R.string.gym_load_failed)
                binding.map.isVisible = false
                binding.attribution.isVisible = false
            }
        }
    }

    private fun renderGyms(gyms: List<Gym>) {
        val map = binding.map
        map.overlays.clear()
        map.isVisible = gyms.isNotEmpty()
        binding.attribution.isVisible = gyms.isNotEmpty()
        if (gyms.isEmpty()) {
            positioned = false
            showMessage(R.string.gym_map_empty)
            return
        }

        val locations = gyms.groupBy { GeoPoint(it.latitude, it.longitude) }
        for ((point, gymsAtPin) in locations) {
            val marker = Marker(map).apply {
                position = point
                title = gymsAtPin.first().name
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
                    if (gymsAtPin.size == 1) {
                        showGym(gymsAtPin.first())
                    } else {
                        AlertDialog.Builder(this@GymMapListActivity)
                            .setTitle(R.string.gyms_at_pin)
                            .setItems(gymsAtPin.map { it.name }.toTypedArray()) { _, index -> showGym(gymsAtPin[index]) }
                            .show()
                    }
                    true
                }
            }
            map.overlays.add(marker)
        }
        val selected = gyms.find { it.id == selectedId }
        if (selected != null) showGym(selected) else showMessage(R.string.gym_map_select)
        map.invalidate()
        if (!positioned) {
            map.doOnLayout {
                val points = locations.keys.toList()
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

    private fun showGym(gym: Gym) {
        selectedId = gym.id
        binding.gymName.text = gym.name
        binding.summary.isVisible = true
        binding.summary.text = gym.location.ifBlank {
            gym.classes.ifBlank { getString(R.string.gym_pin_details, gym.latitude, gym.longitude) }
        }
        binding.photo.isVisible = true
        binding.photo.showGymPhoto(gym.imageUri)
    }

    private fun showMessage(message: Int) {
        selectedId = null
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
        selectedId?.let { outState.putLong("selectedGym", it) }
        if (positioned) {
            outState.putDouble("zoom", binding.map.zoomLevelDouble)
            outState.putDouble("lat", binding.map.mapCenter.latitude)
            outState.putDouble("lon", binding.map.mapCenter.longitude)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.map.onResume()
    }

    override fun onPause() {
        binding.map.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.map.onDetach()
        super.onDestroy()
    }
}
