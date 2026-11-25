package org.wit.fightfinder.views.map

import org.wit.fightfinder.activities.gymStore

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import android.view.View
import androidx.fragment.app.Fragment
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.Marker
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.FragmentGymMapListBinding
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.showGymPhoto
import org.wit.fightfinder.models.Coordinates

class GymMapListFragment : Fragment(R.layout.fragment_gym_map_list), GymMapListPresenter.View {
    private var viewBinding: FragmentGymMapListBinding? = null
    private val binding get() = checkNotNull(viewBinding)
    private var mapState: Bundle? = null
    private val presenter by lazy { GymMapListPresenter(requireContext().gymStore, this) }
    private var hasPins = false
    private var positioned = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewBinding = FragmentGymMapListBinding.bind(view)
        positioned = false
        binding.map.configureGymMap()
        val state = savedInstanceState ?: mapState
        val selectedId = state?.takeIf { it.containsKey("selectedGym") }?.getLong("selectedGym")
        if (state?.containsKey("zoom") == true) {
            binding.map.controller.setZoom(state.getDouble("zoom"))
            binding.map.controller.setCenter(GeoPoint(state.getDouble("lat"), state.getDouble("lon")))
            positioned = true
        }
        viewLifecycleOwner.lifecycleScope.launch { presenter.loadAndObserve(selectedId) }
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
        AlertDialog.Builder(requireContext())
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putAll(viewBinding?.let { captureMapState(it) } ?: mapState ?: return)
    }

    private fun captureMapState(ui: FragmentGymMapListBinding) = Bundle().apply {
        presenter.selectedId?.let { putLong("selectedGym", it) }
        if (positioned) {
            putDouble("zoom", ui.map.zoomLevelDouble)
            putDouble("lat", ui.map.mapCenter.latitude)
            putDouble("lon", ui.map.mapCenter.longitude)
        }
    }

    override fun onResume() {
        super.onResume()
        viewBinding?.map?.onResume()
    }

    override fun onPause() {
        viewBinding?.map?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        viewBinding?.let {
            mapState = captureMapState(it)
            it.map.onDetach()
        }
        viewBinding = null
        super.onDestroyView()
    }
}
