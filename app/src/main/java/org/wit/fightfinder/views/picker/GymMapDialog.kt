package org.wit.fightfinder.views.picker

import org.wit.fightfinder.activities.gymStore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
import org.osmdroid.events.MapEventsReceiver
import org.wit.fightfinder.views.map.configureGymMap
import org.wit.fightfinder.views.map.styleSavedGymPin
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.DialogGymMapBinding
import org.wit.fightfinder.models.Coordinates
import org.wit.fightfinder.models.Gym

class GymMapDialog : DialogFragment() {
    private var binding: DialogGymMapBinding? = null
    private val presenter by lazy { GymMapPickerPresenter(requireContext().gymStore) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setStyle(STYLE_NO_TITLE, R.style.Theme_FightFinder)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val ui = DialogGymMapBinding.inflate(inflater, container, false)
        ui.map.configureGymMap()
        binding = ui
        return ui.root
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        val ui = requireNotNull(binding)
        ui.cancelLocation.setOnClickListener { dismiss() }
        ui.confirmLocation.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                configureMap(ui, state, presenter.savedGyms())
            } catch (e: IOException) {
                ui.mapInstruction.setText(R.string.gym_load_failed)
            }
        }
    }

    private fun configureMap(ui: DialogGymMapBinding, state: Bundle?, gyms: List<Gym>) {
        val map = ui.map
        val selectedMarker = Marker(map).apply {
            title = requireArguments().getString(TITLE).orEmpty().ifBlank { getString(R.string.selected_gym_pin) }
            icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_selected_gym_pin)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        fun select(point: GeoPoint) {
            presenter.select(Coordinates(point.latitude, point.longitude))
            selectedMarker.position = point
            if (!map.overlays.contains(selectedMarker)) map.overlays.add(selectedMarker)
            ui.confirmLocation.isEnabled = true
            ui.mapInstruction.text = getString(R.string.gym_pin_details, point.latitude, point.longitude)
            map.invalidate()
        }

        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                select(point)
                return true
            }
            override fun longPressHelper(point: GeoPoint) = singleTapConfirmedHelper(point)
        }))
        for (gym in gyms) {
            val marker = Marker(map).apply {
                position = GeoPoint(gym.latitude, gym.longitude)
                title = gym.name
                styleSavedGymPin(map)
                snippet = gym.location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { tapped, _ ->
                    select(tapped.position)
                    tapped.showInfoWindow()
                    true
                }
            }
            map.overlays.add(marker)
        }

        val start = state?.getCoordinates() ?: requireArguments().getCoordinates()
        ui.confirmLocation.isEnabled = start != null
        start?.let { select(GeoPoint(it.latitude, it.longitude)) }
        val center = presenter.initialCenter(gyms)
        map.controller.setZoom(if (center != null) 16.0 else 7.0)
        map.controller.setCenter(center?.let { GeoPoint(it.latitude, it.longitude) } ?: GeoPoint(52.246, -7.140))
        ui.confirmLocation.setOnClickListener {
            val result = presenter.selected ?: return@setOnClickListener
            parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putCoordinates(result) })
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onResume() {
        super.onResume()
        binding?.map?.onResume()
    }

    override fun onPause() {
        binding?.map?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        binding?.map?.onDetach()
        binding = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        presenter.selected?.let { outState.putCoordinates(it) }
    }

    companion object {
        const val RESULT = "gym_map_result"
        private const val TITLE = "gym_title"

        fun argumentsFor(point: Coordinates?, title: String) = Bundle().apply {
            point?.let { putCoordinates(it) }
            putString(TITLE, title)
        }
    }
}

internal fun Bundle.putCoordinates(point: Coordinates) {
    putDouble("latitude", point.latitude)
    putDouble("longitude", point.longitude)
}

internal fun Bundle.getCoordinates(): Coordinates? =
    if (containsKey("latitude") && containsKey("longitude")) {
        Coordinates(getDouble("latitude"), getDouble("longitude"))
    } else null
