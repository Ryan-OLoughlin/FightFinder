package org.wit.fightfinder.activities

import android.os.Bundle
import android.app.DatePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.wit.fightfinder.databinding.DialogGymFormBinding
import org.wit.fightfinder.R
import org.wit.fightfinder.adapters.GymAdapter
import org.wit.fightfinder.databinding.ActivityMainBinding
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.AttendanceType
import org.wit.fightfinder.models.GymAttendance
import org.wit.fightfinder.models.GymReview
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gyms = mutableListOf<Gym>()
    private lateinit var gymAdapter: GymAdapter
    private var gymSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)

        gymAdapter = GymAdapter(
            onViewGym = ::showGymDetails,
            onEditGym = ::showEditGymDialog,
            onDeleteGym = ::showDeleteGymDialog
        )
        binding.gymRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gymRecyclerView.adapter = gymAdapter
        binding.btnAddGym.setOnClickListener { showAddGymDialog() }
        binding.searchGyms.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                gymSearchQuery = text?.toString().orEmpty()
                refreshGymList()
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
        refreshGymList()

        if (Timber.treeCount() == 0) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("FightFinder started")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_delete_all_gyms)?.isEnabled = gyms.isNotEmpty()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete_all_gyms -> {
                showDeleteAllGymsDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddGymDialog() = showGymFormDialog()

    private fun showGymDetails(gym: Gym) {
        val notProvided = getString(R.string.gym_not_provided)
        val profileDetails = listOf(
            getString(R.string.gym_detail_location, gym.location.ifBlank { notProvided }),
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

        AlertDialog.Builder(this)
            .setTitle(gym.name)
            .setMessage(details)
            .setNeutralButton(R.string.gym_attendance_record_action) { _, _ -> showAttendanceTypeDialog(gym) }
            .setNegativeButton(R.string.button_add_review) { _, _ -> showAddReviewDialog(gym) }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAttendanceTypeDialog(gym: Gym) {
        val choices = arrayOf(
            getString(R.string.gym_attendance_visit),
            getString(R.string.gym_attendance_class)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.gym_attendance_record_action)
            .setItems(choices) { _, selected ->
                val type = if (selected == 0) AttendanceType.VISIT else AttendanceType.CLASS
                showAttendanceDetailsDialog(gym, type)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showAttendanceDetailsDialog(gym: Gym, type: AttendanceType) {
        val selectedDate = Calendar.getInstance()
        val classNameInput = EditText(this).apply {
            hint = getString(R.string.gym_attendance_class_hint)
            visibility = if (type == AttendanceType.CLASS) View.VISIBLE else View.GONE
            maxLines = 1
        }
        val dateButton = Button(this).apply {
            text = getString(R.string.gym_attendance_date_label, formatDate(selectedDate))
            setOnClickListener {
                DatePickerDialog(
                    this@MainActivity,
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
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, 8, padding, 0)
            addView(dateButton)
            if (type == AttendanceType.CLASS) addView(classNameInput)
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (type == AttendanceType.VISIT) R.string.gym_attendance_visit else R.string.gym_attendance_class)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.gym_attendance_record_action, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val className = classNameInput.text.toString().trim()
                if (type == AttendanceType.CLASS && className.isBlank()) {
                    Toast.makeText(this, R.string.gym_attendance_class_required, Toast.LENGTH_SHORT).show()
                } else {
                    val gymIndex = gyms.indexOfFirst { it === gym }
                    if (gymIndex != -1) {
                        val attendance = GymAttendance(
                            type = type,
                            date = formatDate(selectedDate),
                            className = className
                        )
                        val updatedGym = gym.copy(attendance = gym.attendance + attendance)
                        gyms[gymIndex] = updatedGym
                        refreshGymList()
                        dialog.dismiss()
                        showGymDetails(updatedGym)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun formatDate(calendar: Calendar): String =
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

    private fun showAddReviewDialog(gym: Gym) {
        val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
        val topPadding = (8 * resources.displayMetrics.density).toInt()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, topPadding, horizontalPadding, 0)
        }
        val ratingLabel = TextView(this).apply {
            setText(R.string.gym_review_rating)
        }
        val ratingPicker = NumberPicker(this).apply {
            minValue = 1
            maxValue = 5
            value = 5
            wrapSelectorWheel = false
        }
        val commentInput = EditText(this).apply {
            hint = getString(R.string.gym_review_comment_hint)
            minLines = 2
            gravity = android.view.Gravity.TOP
        }
        form.addView(ratingLabel)
        form.addView(ratingPicker)
        form.addView(commentInput)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.button_add_review)
            .setView(form)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_add_review, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val comment = commentInput.text.toString().trim()
                if (comment.isBlank()) {
                    Toast.makeText(this, R.string.gym_review_required, Toast.LENGTH_SHORT).show()
                } else {
                    val gymIndex = gyms.indexOfFirst { it === gym }
                    if (gymIndex != -1) {
                        val updatedGym = gym.copy(
                            reviews = gym.reviews + GymReview(ratingPicker.value, comment)
                        )
                        gyms[gymIndex] = updatedGym
                        refreshGymList()
                        dialog.dismiss()
                        showGymDetails(updatedGym)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showEditGymDialog(gym: Gym) = showGymFormDialog(gym)

    private fun showGymFormDialog(gym: Gym? = null) {
        val formBinding = DialogGymFormBinding.inflate(layoutInflater)
        formBinding.inputGymName.setText(gym?.name.orEmpty())
        formBinding.inputGymLocation.setText(gym?.location.orEmpty())
        formBinding.inputGymPrice.setText(gym?.price.orEmpty())
        formBinding.inputGymOpeningHours.setText(gym?.openingHours.orEmpty())
        formBinding.inputGymFacilities.setText(gym?.facilities.orEmpty())
        formBinding.inputGymClasses.setText(gym?.classes.orEmpty())

        val isEditing = gym != null
        val dialog = AlertDialog.Builder(this)
            .setTitle(if (isEditing) R.string.dialog_edit_gym_title else R.string.dialog_add_gym_title)
            .setView(formBinding.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(if (isEditing) R.string.button_edit_gym else R.string.button_add_gym, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = formBinding.inputGymName.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.gym_required_fields, Toast.LENGTH_SHORT).show()
                } else {
                    val updatedGym = Gym(
                        name = name,
                        location = formBinding.inputGymLocation.text.toString().trim(),
                        price = formBinding.inputGymPrice.text.toString().trim(),
                        openingHours = formBinding.inputGymOpeningHours.text.toString().trim(),
                        facilities = formBinding.inputGymFacilities.text.toString().trim(),
                        classes = formBinding.inputGymClasses.text.toString().trim(),
                        reviews = gym?.reviews.orEmpty(),
                        attendance = gym?.attendance.orEmpty()
                    )

                    var saved = false
                    if (gym == null) {
                        gyms.add(updatedGym)
                        Timber.i("Added gym: $name")
                        saved = true
                    } else {
                        val gymIndex = gyms.indexOfFirst { it === gym }
                        if (gymIndex != -1) {
                            gyms[gymIndex] = updatedGym
                            Timber.i("Updated gym: $name")
                            saved = true
                        }
                    }
                    if (saved) {
                        refreshGymList()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteGymDialog(gym: Gym) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_gym_title)
            .setMessage(getString(R.string.dialog_delete_gym_message, gym.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_delete_gym) { _, _ ->
                val gymIndex = gyms.indexOfFirst { it === gym }
                if (gymIndex != -1) {
                    gyms.removeAt(gymIndex)
                    refreshGymList()
                    Timber.i("Deleted gym: ${gym.name}")
                }
            }
            .show()
    }

    private fun showDeleteAllGymsDialog() {
        if (gyms.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_all_gyms_title)
            .setMessage(R.string.dialog_delete_all_gyms_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dialog_delete_all_gyms_confirm) { _, _ ->
                gyms.clear()
                binding.searchGyms.text?.clear()
                gymSearchQuery = ""
                refreshGymList()
                invalidateOptionsMenu()
                Timber.i("Deleted all gyms")
            }
            .show()
    }

    private fun refreshGymList() {
        val query = gymSearchQuery.trim()
        val visibleGyms = if (query.isBlank()) {
            gyms
        } else {
            gyms.filter { gym ->
                gym.name.contains(query, ignoreCase = true) ||
                    gym.location.contains(query, ignoreCase = true) ||
                    gym.classes.contains(query, ignoreCase = true) ||
                    gym.facilities.contains(query, ignoreCase = true)
            }
        }

        gymAdapter.setGyms(visibleGyms)
        invalidateOptionsMenu()
        if (gyms.isEmpty()) {
            binding.emptyState.setText(R.string.gym_list_empty)
            binding.emptyState.visibility = View.VISIBLE
        } else if (visibleGyms.isEmpty()) {
            binding.emptyState.setText(R.string.gym_search_no_results)
            binding.emptyState.visibility = View.VISIBLE
        } else {
            binding.emptyState.visibility = View.GONE
        }
    }
}