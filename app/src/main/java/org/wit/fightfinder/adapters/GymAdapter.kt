package org.wit.fightfinder.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.wit.fightfinder.databinding.ItemGymBinding
import org.wit.fightfinder.models.Gym

class GymAdapter(
    private val onViewGym: (Gym) -> Unit,
    private val onEditGym: (Gym) -> Unit,
    private val onDeleteGym: (Gym) -> Unit
) : RecyclerView.Adapter<GymAdapter.GymViewHolder>() {

    private val gyms = mutableListOf<Gym>()

    fun setGyms(updatedGyms: List<Gym>) {
        gyms.clear()
        gyms.addAll(updatedGyms)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GymViewHolder {
        val binding = ItemGymBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GymViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GymViewHolder, position: Int) {
        holder.bind(gyms[position])
    }

    override fun getItemCount(): Int = gyms.size

    inner class GymViewHolder(
        private val binding: ItemGymBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(gym: Gym) {
            binding.gymName.text = gym.name
            binding.btnViewGym.setOnClickListener { onViewGym(gym) }
            binding.btnEditGym.setOnClickListener { onEditGym(gym) }
            binding.btnDeleteGym.setOnClickListener { onDeleteGym(gym) }
        }
    }
}