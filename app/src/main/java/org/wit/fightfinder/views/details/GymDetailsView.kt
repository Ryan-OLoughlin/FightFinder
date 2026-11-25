package org.wit.fightfinder.views.details

import android.app.DatePickerDialog
import android.view.View
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
import org.wit.fightfinder.databinding.FragmentGymDetailsBinding
import org.wit.fightfinder.R
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.AttendanceType
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import org.wit.fightfinder.activities.gymStore
import org.wit.fightfinder.activities.saveGymChange

class GymDetailsView : Fragment(R.layout.fragment_gym_details) {
    private val presenter by lazy {
        GymDetailsPresenter(requireContext().gymStore, requireArguments().getLong("gym_id"))
    }
    private var binding: FragmentGymDetailsBinding? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ui = FragmentGymDetailsBinding.bind(view)
        binding = ui
        ui.addReview.setOnClickListener { showAddReviewDialog() }
        ui.recordAttendance.setOnClickListener { showAttendanceTypeDialog() }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val gym = presenter.currentGym()
                if (gym == null) ui.details.setText(R.string.gym_missing) else renderGym(gym)
            } catch (_: IOException) {
                ui.details.setText(R.string.gym_load_failed)
            }
        }
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun renderGym(gym: Gym) {
        val notProvided = getString(R.string.gym_not_provided)
        val profileDetails = listOf(
            getString(R.string.gym_detail_location, gym.location.ifBlank { notProvided }),
            getString(R.string.gym_pin_details, gym.latitude, gym.longitude),
            gym.addedFrom?.let { getString(R.string.added_from_details, it.latitude, it.longitude) }
                ?: getString(R.string.added_from_missing),
            getString(R.string.gym_detail_price, gym.price.ifBlank { notProvided }),
            getString(R.string.gym_detail_opening_hours, gym.openingHours.ifBlank { notProvided }),
            getString(R.string.gym_detail_facilities, gym.facilities.ifBlank { notProvided }),
            getString(R.string.gym_detail_classes, gym.classes.ifBlank { notProvided })
        ).joinToString("\n\n")
        val reviewDetails = if (gym.reviews.isEmpty()) {
            getString(R.string.gym_reviews_empty)
        } else {
            gym.reviews.joinToString("\n\n") { review ->
                getString(R.string.gym_review_line, review.rating, review.comment)
            }
        }
        val attendanceDetails = if (gym.attendance.isEmpty()) {
            getString(R.string.gym_attendance_empty)
        } else {
            gym.attendance.joinToString("\n") { attendance ->
                when (attendance.type) {
                    AttendanceType.VISIT -> getString(R.string.gym_attendance_item_visit, attendance.date)
                    AttendanceType.CLASS -> getString(
                        R.string.gym_attendance_item_class,
                        attendance.className,
                        attendance.date
                    )
                }
            }
        }
        val details = "$profileDetails\n\n${getString(R.string.gym_reviews_title)}\n\n$reviewDetails" +
            "\n\n${getString(R.string.gym_attendance_title)}\n\n$attendanceDetails"

        binding?.apply {
            name.text = gym.name
            this.details.text = details
            addReview.isEnabled = true
            recordAttendance.isEnabled = true
        }
    }

    private fun showAttendanceTypeDialog() {
        val activity = requireActivity()
        val choices = arrayOf(
            getString(R.string.gym_attendance_visit),
            getString(R.string.gym_attendance_class)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.gym_attendance_record_action)
            .setItems(choices) { _, selected ->
                val type = if (selected == 0) AttendanceType.VISIT else AttendanceType.CLASS
                showAttendanceDetailsDialog(type)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAttendanceDetailsDialog(type: AttendanceType) {
        val activity = requireActivity()
        val selectedDate = Calendar.getInstance()
        val classNameInput = EditText(activity).apply {
            hint = getString(R.string.gym_attendance_class_hint)
            visibility = if (type == AttendanceType.CLASS) View.VISIBLE else View.GONE
            maxLines = 1
        }
        val dateButton = Button(activity).apply {
            text = getString(R.string.gym_attendance_date_label, formatDate(selectedDate))
            setOnClickListener {
                DatePickerDialog(
                    activity,
                    { _, year, month, day ->
                        selectedDate.set(year, month, day)
                        text = getString(R.string.gym_attendance_date_label, formatDate(selectedDate))
                    },
                    selectedDate.get(Calendar.YEAR),
                    selectedDate.get(Calendar.MONTH),
                    selectedDate.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 8, padding, 0)
            addView(dateButton)
            if (type == AttendanceType.CLASS) addView(classNameInput)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(if (type == AttendanceType.VISIT) R.string.gym_attendance_visit else R.string.gym_attendance_class)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.gym_attendance_record_action, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val className = classNameInput.text.toString()
                val date = formatDate(selectedDate)
                viewLifecycleOwner.saveGymChange(activity, dialog.getButton(AlertDialog.BUTTON_POSITIVE)) {
                    val updatedGym = presenter.recordAttendance(type, date, className)
                    if (updatedGym == null) {
                        Toast.makeText(activity, R.string.gym_attendance_class_required, Toast.LENGTH_SHORT).show()
                    } else {
                        dialog.dismiss()
                        renderGym(updatedGym)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun formatDate(calendar: Calendar): String =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

    private fun showAddReviewDialog() {
        val activity = requireActivity()
        val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
        val topPadding = (8 * resources.displayMetrics.density).toInt()
        val form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
        }
        val ratingLabel = TextView(activity).apply {
            setText(R.string.gym_review_rating)
        }
        val ratingPicker = NumberPicker(activity).apply {
            minValue = 1
            maxValue = 5
            value = 5
            wrapSelectorWheel = false
        }
        val commentInput = EditText(activity).apply {
            hint = getString(R.string.gym_review_comment_hint)
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        form.addView(ratingLabel)
        form.addView(ratingPicker)
        form.addView(commentInput)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.button_add_review)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_add_review, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val comment = commentInput.text.toString()
                val rating = ratingPicker.value
                viewLifecycleOwner.saveGymChange(activity, dialog.getButton(AlertDialog.BUTTON_POSITIVE)) {
                    val updatedGym = presenter.addReview(rating, comment)
                    if (updatedGym == null) {
                        Toast.makeText(activity, R.string.gym_review_required, Toast.LENGTH_SHORT).show()
                    } else {
                        dialog.dismiss()
                        renderGym(updatedGym)
                    }
                }
            }
        }
        dialog.show()
    }

}
