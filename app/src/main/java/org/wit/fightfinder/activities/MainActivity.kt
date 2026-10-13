package org.wit.fightfinder.activities

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import org.wit.fightfinder.R
import org.wit.fightfinder.adapters.GymAdapter
import org.wit.fightfinder.databinding.ActivityMainBinding
import org.wit.fightfinder.models.Gym
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val gyms = mutableListOf<Gym>()
    private lateinit var gymAdapter: GymAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        gymAdapter = GymAdapter(::showGymDetails)
        binding.gymRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gymRecyclerView.adapter = gymAdapter
        binding.btnAddGym.setOnClickListener { showAddGymDialog() }
        refreshGymList()

        if (Timber.treeCount() == 0) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.i("FightFinder started")
    }

    private fun showAddGymDialog() {
        val gymNameInput = EditText(this).apply {
            hint = getString(R.string.gym_name_hint)
            maxLines = 1
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_add_gym_title)
            .setView(gymNameInput)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_add_gym) { _, _ ->
                val name = gymNameInput.text.toString().trim()
                if (name.isBlank()) {
                    Toast.makeText(this, R.string.gym_required_fields, Toast.LENGTH_SHORT).show()
                } else {
                    gyms.add(Gym(name))
                    refreshGymList()
                    Timber.i("Added gym: $name")
                }
            }
            .show()
    }

    private fun showGymDetails(gym: Gym) {
        AlertDialog.Builder(this)
            .setTitle(gym.name)
            .setMessage(R.string.gym_details_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun refreshGymList() {
        gymAdapter.setGyms(gyms)
        binding.emptyState.visibility = if (gyms.isEmpty()) View.VISIBLE else View.GONE
    }
}