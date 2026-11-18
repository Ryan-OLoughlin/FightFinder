package org.wit.fightfinder.activities

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import org.wit.fightfinder.R
import org.wit.fightfinder.databinding.DialogGymFormBinding
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListModel
import org.wit.fightfinder.showGymPhoto
import java.text.NumberFormat
import java.text.ParsePosition

class GymFormDialog : DialogFragment() {
    private val gymList by lazy { ViewModelProvider(requireActivity())[GymListModel::class.java] }
    private val coordinateFormat = NumberFormat.getNumberInstance().apply {
        isGroupingUsed = false
        maximumFractionDigits = 340
    }
    private var selectedImage: String? = null
    private var binding: DialogGymFormBinding? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            selectedImage = uri.toString()
            binding?.imagePreview?.showGymPhoto(selectedImage)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val form = DialogGymFormBinding.inflate(requireActivity().layoutInflater)
        binding = form
        val gymId = arguments?.takeIf { it.containsKey(GYM_ID) }?.getLong(GYM_ID)
        val gym = gymList.gyms.find { it.id == gymId }

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
            if (gymId != null && gym == null) {
                // Android can restore the dialog after the in-memory list has been lost.
                dismiss()
            } else {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save(form, gym) }
            }
        }
        return dialog
    }

    private fun save(form: DialogGymFormBinding, original: Gym?) {
        val name = form.inputGymName.text.toString().trim()
        if (name.isBlank()) {
            form.inputGymName.error = getString(R.string.gym_required_fields)
            form.inputGymName.requestFocus()
            return
        }
        val latitude = readCoordinate(form.inputLatitude, Gym.LATITUDE_RANGE, R.string.gym_invalid_latitude) ?: return
        val longitude = readCoordinate(form.inputLongitude, Gym.LONGITUDE_RANGE, R.string.gym_invalid_longitude) ?: return
        val gym = Gym(
            id = original?.id ?: gymList.allocateId(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            imageUri = selectedImage,
            location = form.inputGymLocation.text.toString().trim(),
            price = form.inputGymPrice.text.toString().trim(),
            openingHours = form.inputGymOpeningHours.text.toString().trim(),
            facilities = form.inputGymFacilities.text.toString().trim(),
            classes = form.inputGymClasses.text.toString().trim(),
            reviews = original?.reviews.orEmpty(),
            attendance = original?.attendance.orEmpty()
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
        if (original == null) {
            gymList.gyms.add(gym)
        } else {
            val index = gymList.gyms.indexOfFirst { it.id == original.id }
            if (index >= 0) gymList.gyms[index] = gym
        }
        parentFragmentManager.setFragmentResult(SAVED, Bundle())
        dismiss()
    }

    private fun readCoordinate(field: EditText, range: ClosedFloatingPointRange<Double>, error: Int): Double? {
        val text = field.text.toString().trim()
        val position = ParsePosition(0)
        val value = coordinateFormat.parse(text, position)?.toDouble()
        if (position.index == text.length && value != null && value in range) return value
        field.error = getString(error)
        field.requestFocus()
        return null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(IMAGE_URI, selectedImage)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    companion object {
        const val SAVED = "gym_saved"
        private const val GYM_ID = "gym_id"
        private const val IMAGE_URI = "image_uri"

        fun newInstance(id: Long?) = GymFormDialog().apply {
            arguments = Bundle().apply { if (id != null) putLong(GYM_ID, id) }
        }
    }
}
