package org.wit.fightfinder.views.list

import org.wit.fightfinder.activities.gymStore
import org.wit.fightfinder.views.details.GymDetailsView
import org.wit.fightfinder.views.form.GymFormDialog
import org.wit.fightfinder.views.map.GymMapListActivity

import android.os.Bundle
import android.content.Intent
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.wit.fightfinder.R
import org.wit.fightfinder.adapters.GymAdapter
import org.wit.fightfinder.databinding.ActivityMainBinding
import org.wit.fightfinder.models.Gym
import timber.log.Timber

class MainActivity : AppCompatActivity(), GymListPresenter.View {

    private lateinit var binding: ActivityMainBinding
    private val presenter by lazy { GymListPresenter(gymStore, this) }
    private lateinit var gymAdapter: GymAdapter
    private var canDeleteAll = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarMain)

        gymAdapter = GymAdapter(
            onViewGym = { gym -> GymDetailsView(this, gym.id).show() },
            onEditGym = { gym -> showGymForm(gym.id) },
            onDeleteGym = { gym -> presenter.onDeleteRequested(gym.id) }
        )
        binding.gymRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.gymRecyclerView.adapter = gymAdapter
        binding.searchGyms.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                presenter.onSearchChanged(text?.toString().orEmpty())
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
        lifecycleScope.launch { presenter.loadAndObserve() }
    }

    override fun showLoading() {
        binding.btnAddGym.isEnabled = false
        binding.searchGyms.isEnabled = false
        binding.emptyState.visibility = View.GONE
    }

    override fun showLoadError() {
        binding.emptyState.setText(R.string.gym_load_failed)
        binding.emptyState.visibility = View.VISIBLE
        binding.btnAddGym.setText(R.string.gym_retry)
        binding.btnAddGym.setOnClickListener { loadGyms() }
        binding.btnAddGym.isEnabled = true
    }

    override fun showGyms(gyms: List<Gym>, emptyState: GymListPresenter.EmptyState?, canDeleteAll: Boolean) {
        binding.btnAddGym.setText(R.string.button_add_gym)
        binding.btnAddGym.setOnClickListener { showGymForm(null) }
        binding.btnAddGym.isEnabled = true
        binding.searchGyms.isEnabled = true
        gymAdapter.setGyms(gyms)
        if (this.canDeleteAll != canDeleteAll) {
            this.canDeleteAll = canDeleteAll
            invalidateOptionsMenu()
        }
        val message = when (emptyState) {
            GymListPresenter.EmptyState.EMPTY_LIST -> R.string.gym_list_empty
            GymListPresenter.EmptyState.NO_RESULTS -> R.string.gym_search_no_results
            null -> null
        }
        binding.emptyState.visibility = if (message == null) View.GONE else View.VISIBLE
        message?.let(binding.emptyState::setText)
    }

    override fun clearSearch() {
        binding.searchGyms.text?.clear()
    }

    override fun showDeleteError() {
        Toast.makeText(this, R.string.gym_save_failed, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_delete_all_gyms)?.isEnabled = canDeleteAll
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_all_gyms_map -> {
                startActivity(Intent(this, GymMapListActivity::class.java))
                true
            }
            R.id.action_delete_all_gyms -> {
                presenter.onDeleteAllRequested()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showGymForm(gymId: Long?) {
        GymFormDialog.newInstance(gymId).show(supportFragmentManager, "gym_form")
    }

    override fun confirmDelete(gym: Gym) {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_gym_title)
            .setMessage(getString(R.string.dialog_delete_gym_message, gym.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_delete_gym) { _, _ ->
                lifecycleScope.launch { presenter.onDeleteConfirmed(gym.id) }
            }
            .show()
    }

    override fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_delete_all_gyms_title)
            .setMessage(R.string.dialog_delete_all_gyms_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dialog_delete_all_gyms_confirm) { _, _ ->
                lifecycleScope.launch { presenter.onDeleteAllConfirmed() }
            }
            .show()
    }
}
