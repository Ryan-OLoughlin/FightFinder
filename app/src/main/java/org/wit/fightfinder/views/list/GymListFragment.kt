package org.wit.fightfinder.views.list

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import org.wit.fightfinder.R
import org.wit.fightfinder.activities.gymStore
import org.wit.fightfinder.adapters.GymAdapter
import org.wit.fightfinder.databinding.FragmentGymListBinding
import org.wit.fightfinder.models.Gym
import org.wit.fightfinder.models.GymListStore.SyncStatus

class GymListFragment : Fragment(R.layout.fragment_gym_list), GymListPresenter.View {

    private var viewBinding: FragmentGymListBinding? = null
    private val binding get() = checkNotNull(viewBinding)
    private val presenter by lazy { GymListPresenter(requireContext().gymStore, this) }
    private lateinit var gymAdapter: GymAdapter
    private var canDeleteAll = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewBinding = FragmentGymListBinding.bind(view)
        gymAdapter = GymAdapter(
            onViewGym = { gym ->
                val navigation = findNavController()
                if (navigation.currentDestination?.id == R.id.gymList) {
                    navigation.navigate(R.id.openGymDetails, bundleOf("gym_id" to gym.id))
                }
            },
            onEditGym = { gym -> showGymForm(gym.id) },
            onDeleteGym = { gym -> presenter.onDeleteRequested(gym.id) }
        )
        binding.gymRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.gymRecyclerView.adapter = gymAdapter
        binding.searchGyms.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                presenter.onSearchChanged(text?.toString().orEmpty())
            }

            override fun afterTextChanged(text: Editable?) = Unit
        })
        loadGyms()
        val store = requireContext().gymStore
        viewLifecycleOwner.lifecycleScope.launch {
            store.syncStatus.collect { status ->
                val message = when (status) {
                    SyncStatus.DISABLED -> null
                    SyncStatus.PENDING -> R.string.gym_sync_pending
                    SyncStatus.SYNCED -> R.string.gym_sync_complete
                    SyncStatus.UNAVAILABLE -> R.string.gym_sync_unavailable
                    SyncStatus.ACCESS_DENIED -> R.string.gym_sync_denied
                    SyncStatus.SIGN_IN_REQUIRED -> R.string.gym_sync_sign_in
                    SyncStatus.DATABASE_SETUP -> R.string.gym_sync_database_setup
                    SyncStatus.INVALID_RECORD -> R.string.gym_sync_invalid_record
                    SyncStatus.QUOTA_EXCEEDED -> R.string.gym_sync_quota
                }
                binding.gymSyncStatus.isVisible = message != null
                message?.let(binding.gymSyncStatus::setText)
            }
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
                inflater.inflate(R.menu.menu_main, menu)
            }

            override fun onPrepareMenu(menu: Menu) {
                menu.findItem(R.id.action_delete_all_gyms)?.isEnabled = canDeleteAll
            }

            override fun onMenuItemSelected(item: MenuItem): Boolean {
                if (item.itemId != R.id.action_delete_all_gyms) return false
                presenter.onDeleteAllRequested()
                return true
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        binding.gymRecyclerView.adapter = null
        viewBinding = null
        super.onDestroyView()
    }

    private fun loadGyms() {
        viewLifecycleOwner.lifecycleScope.launch { presenter.loadAndObserve() }
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
            requireActivity().invalidateMenu()
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
        Toast.makeText(requireContext(), R.string.gym_save_failed, Toast.LENGTH_LONG).show()
    }

    private fun showGymForm(gymId: Long?) {
        val navigation = findNavController()
        if (navigation.currentDestination?.id != R.id.gymList) return
        val arguments = gymId?.let { bundleOf("gym_id" to it) }
        navigation.navigate(R.id.openGymForm, arguments)
    }

    override fun confirmDelete(gym: Gym) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_gym_title)
            .setMessage(getString(R.string.dialog_delete_gym_message, gym.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.button_delete_gym) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch { presenter.onDeleteConfirmed(gym.id) }
            }
            .show()
    }

    override fun confirmDeleteAll() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_delete_all_gyms_title)
            .setMessage(R.string.dialog_delete_all_gyms_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dialog_delete_all_gyms_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch { presenter.onDeleteAllConfirmed() }
            }
            .show()
    }
}
