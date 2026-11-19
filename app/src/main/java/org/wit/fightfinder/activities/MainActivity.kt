package org.wit.fightfinder.activities

import android.os.Bundle
import android.app.DatePickerDialog
import android.text.Editable
import android.text.TextWatcher
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.IOException
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
    private val gyms get() = gymStore.gyms.value
    private lateinit var gymAdapter: GymAdapter
    private var gymSearchQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)

        gymAdapter = GymAdapter(
            onViewGym = ::showGymDetails,
            onEditGym = { gym -> showGymForm(gym.id) },
            onDeleteGym = ::showDeleteGymDialog
        )
        binding.gymRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gymRecyclerView.adapter = gymAdapter
        binding.searchGyms.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                gymSearchQuery = text?.toString().orEmpty()
                refreshGymList()
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
        loadGyms()

        if (Timber.treeCount() == 0) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("FightFinder started")
    }

    private fun loadGyms() {
        binding.btnAddGym.isEnabled = false
        binding.searchGyms.isEnabled = false
        lifecycleScope.launch {
            try {
                gymStore.load()
                binding.btnAddGym.setText(R.string.button_add_gym)
                binding.btnAddGym.setOnClickListener { showGymForm(null) }
                binding.btnAddGym.isEnabled = true
                binding.searchGyms.isEnabled = true
                gymStore.gyms.collect { refreshGymList() }
            } catch (e: IOException) {
                Timber.e(e, "Unable to load gyms")
                binding.emptyState.setText(R.string.gym_load_failed)
                binding.emptyState.visibility = View.VISIBLE
                binding.btnAddGym.setText(R.string.gym_retry)
                binding.btnAddGym.setOnClickListener { loadGyms() }
                binding.btnAddGym.isEnabled = true
            }
        }
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

    private fun showGymForm(gymId: Long?) {
        GymFormDialog.newInstance(gymId).show(supportFragmentManager, "gym_form")
    }

    private fun showGymDetails(gym: Gym) {
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
                    saveGymChange(this, dialog.getButton(AlertDialog.BUTTON_POSITIVE)) {
                        val attendance = GymAttendance(
                            type = type,
                            date = formatDate(selectedDate),
                            className = className
                        )
                        val updatedGym = gymStore.update(gym.id) { it.copy(attendance = it.attendance + attendance) }
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
                    val review = GymReview(ratingPicker.value, comment)
                    saveGymChange(this, dialog.getButton(AlertDialog.BUTTON_POSITIVE)) {
                        val updatedGym = gymStore.update(gym.id) { it.copy(reviews = it.reviews + review) }
                        dialog.dismiss()
                        showGymDetails(updatedGym)
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
                saveGymChange(this) {
                    gymStore.delete(gym.id)
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
                saveGymChange(this) {
                    gymStore.deleteAll()
                    binding.searchGyms.text?.clear()
                    Timber.i("Deleted all gyms")
                }
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