package org.wit.fightfinder.activities

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.DialogGymFormBinding
import org.wit.fightfinder.models.Coordinates
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.showGymPhoto
import java.text.NumberFormat
import java.text.ParsePosition

class GymFormDialog : DialogFragment() {
    private val coordinateFormat = NumberFormat.getNumberInstance().apply {
        isGroupingUsed = false
        maximumFractionDigits = 340
    }
    private var selectedImage: String? = null
    private var binding: DialogGymFormBinding? = null

    private var addedFrom: Coordinates? = null
    private var requestedPermission = false
    private var locationFinished = false
    private var locationJob: Job? = null
    private val editing get() = arguments?.containsKey(GYM_ID) == true

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (!hasLocationPermission()) finishLocation(null)
        else if (isResumed) startLocation()
    }

    private val locationSettings = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (!hasLocationPermission() || !deviceLocationEnabled()) finishLocation(null)
        else if (isResumed) startLocation()
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImage = uri.toString()
            binding?.imagePreview?.showGymPhoto(selectedImage)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val form = DialogGymFormBinding.inflate(requireActivity().layoutInflater)
        binding = form
        addedFrom = savedInstanceState?.getBundle(ADDED_FROM)?.getCoordinates()
        requestedPermission = savedInstanceState?.getBoolean(REQUESTED_PERMISSION) == true
        locationFinished = savedInstanceState?.getBoolean(LOCATION_FINISHED) == true
        form.retryLocation.setOnClickListener { retryLocation() }
        form.locationStatus.visibility = if (editing) View.GONE else View.VISIBLE
        childFragmentManager.setFragmentResultListener(GymMapDialog.RESULT, this) { _, result ->
            result.getCoordinates()?.let { showCoordinates(form, it) }
        }
        form.chooseLocation.setOnClickListener {
            val lat = parseCoordinate(form.inputLatitude, Gym.LATITUDE_RANGE)
            val lon = parseCoordinate(form.inputLongitude, Gym.LONGITUDE_RANGE)
            val start = if (lat != null && lon != null) Coordinates(lat, lon) else null
            GymMapDialog.newInstance(start, form.inputGymName.text.toString())
                .show(childFragmentManager, "gym_map")
        }
        val gymId = arguments?.takeIf { it.containsKey(GYM_ID) }?.getLong(GYM_ID)
        val gym = requireContext().gymStore.gyms.value.find { it.id == gymId }

        form.inputGymName.setText(gym?.name)
        form.inputGymLocation.setText(gym?.location)
        form.inputLatitude.setText(gym?.latitude?.let(coordinateFormat::format))
        form.inputLongitude.setText(gym?.longitude?.let(coordinateFormat::format))
        form.inputGymPrice.setText(gym?.price)
        form.inputGymOpeningHours.setText(gym?.openingHours)
        form.inputGymFacilities.setText(gym?.facilities)
        form.inputGymClasses.setText(gym?.classes)
        selectedImage = savedInstanceState?.getString(IMAGE_URI) ?: gym?.imageUri
        form.imagePreview.showGymPhoto(selectedImage)
        form.pickImage.setOnClickListener { pickImage.launch(arrayOf("image/*")) }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (gymId == null) R.string.dialog_add_gym_title else R.string.dialog_edit_gym_title)
            .setView(form.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(if (gymId == null) R.string.button_add_gym else R.string.button_edit_gym, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(form, gymId) }
        }
        return dialog
    }

    override fun onResume() {
        super.onResume()
        if (editing) return
        updateLocationStatus()
        if (locationFinished) return
        if (hasLocationPermission()) {
            startLocation()
        } else if (!requestedPermission) {
            requestedPermission = true
            locationPermission.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
        }
    }

    private fun hasLocationPermission() =
        requireContext().checkSelfPermission(ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED ||
            requireContext().checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED

    private fun deviceLocationEnabled() =
        requireContext().getSystemService(LocationManager::class.java).isLocationEnabled

    private fun retryLocation() {
        locationFinished = false
        updateLocationStatus()
        when {
            !hasLocationPermission() && shouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION) ->
                locationPermission.launch(arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION))
            !hasLocationPermission() -> locationSettings.launch(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${requireContext().packageName}".toUri())
            )
            !deviceLocationEnabled() -> locationSettings.launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            else -> startLocation()
        }
    }

    private fun startLocation() {
        if (locationJob?.isActive == true || locationFinished) return
        locationJob = lifecycleScope.launch {
            finishLocation(currentLocation(requireContext()))
        }
    }

    private fun finishLocation(fix: Coordinates?) {
        addedFrom = fix
        locationFinished = true
        updateLocationStatus()
    }

    private fun updateLocationStatus() {
        val form = binding ?: return
        form.locationStatus.setText(when {
            !locationFinished -> R.string.location_pending
            addedFrom != null -> R.string.location_captured
            !hasLocationPermission() -> R.string.location_permission_needed
            !deviceLocationEnabled() -> R.string.location_disabled
            else -> R.string.location_missing
        })
        form.retryLocation.visibility = if (locationFinished && addedFrom == null) View.VISIBLE else View.GONE
        form.retryLocation.setText(when {
            !hasLocationPermission() -> R.string.location_allow
            !deviceLocationEnabled() -> R.string.location_enable
            else -> R.string.location_retry
        })
        (dialog as? AlertDialog)?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = locationFinished
    }

    private fun showCoordinates(form: DialogGymFormBinding, point: Coordinates) {
        form.inputLatitude.setText(coordinateFormat.format(point.latitude))
        form.inputLongitude.setText(coordinateFormat.format(point.longitude))
        form.inputLatitude.error = null
        form.inputLongitude.error = null
    }

    override fun onStop() {
        locationJob?.cancel()
        locationJob = null
        super.onStop()
    }

    private fun save(form: DialogGymFormBinding, gymId: Long?) {
        val name = form.inputGymName.text.toString().trim()
        if (name.isBlank()) {
            form.inputGymName.error = getString(R.string.gym_required_fields)
            form.inputGymName.requestFocus()
            return
        }
        val latitude = readCoordinate(form.inputLatitude, Gym.LATITUDE_RANGE, R.string.gym_invalid_latitude) ?: return
        val longitude = readCoordinate(form.inputLongitude, Gym.LONGITUDE_RANGE, R.string.gym_invalid_longitude) ?: return
        val gym = Gym(
            id = gymId ?: 0,
            name = name,
            latitude = latitude,
            longitude = longitude,
            imageUri = selectedImage,
            addedFrom = addedFrom,
            location = form.inputGymLocation.text.toString().trim(),
            price = form.inputGymPrice.text.toString().trim(),
            openingHours = form.inputGymOpeningHours.text.toString().trim(),
            facilities = form.inputGymFacilities.text.toString().trim(),
            classes = form.inputGymClasses.text.toString().trim()
        )

        selectedImage?.let { image ->
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    image.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant temporary read access only.
            }
        }
        val store = requireContext().gymStore
        saveGymChange(requireContext(), (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)) {
            if (gymId == null) {
                store.create(gym)
            } else {
                store.update(gymId) { current ->
                    gym.copy(addedFrom = current.addedFrom, reviews = current.reviews, attendance = current.attendance)
                }
            }
            dismissAllowingStateLoss()
        }
    }

    private fun parseCoordinate(field: EditText, range: ClosedFloatingPointRange<Double>): Double? {
        val text = field.text.toString().trim()
        val position = ParsePosition(0)
        val value = coordinateFormat.parse(text, position)?.toDouble()
        return value?.takeIf { position.index == text.length && it in range }
    }

    private fun readCoordinate(field: EditText, range: ClosedFloatingPointRange<Double>, error: Int): Double? {
        parseCoordinate(field, range)?.let { return it }
        field.error = getString(error)
        field.requestFocus()
        return null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(IMAGE_URI, selectedImage)
        outState.putBoolean(REQUESTED_PERMISSION, requestedPermission)
        outState.putBoolean(LOCATION_FINISHED, locationFinished)
        addedFrom?.let { fix -> outState.putBundle(ADDED_FROM, Bundle().apply { putCoordinates(fix) }) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        private const val GYM_ID = "gym_id"
        private const val REQUESTED_PERMISSION = "requested_permission"
        private const val LOCATION_FINISHED = "location_finished"
        private const val ADDED_FROM = "added_from"
        private const val IMAGE_URI = "image_uri"

        fun newInstance(id: Long?) = GymFormDialog().apply {
            arguments = Bundle().apply { if (id != null) putLong(GYM_ID, id) }
        }
    }
}
