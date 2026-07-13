package com.halehoundforge.fire.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.halehoundforge.fire.databinding.ItemScanRowBinding

data class ScanRow(val line1: String, val line2: String)

/**
 * DiffUtil list adapter — avoids full notifyDataSetChanged jank on Fire's modest GPU/CPU.
 */
class ScanRowAdapter : ListAdapter<ScanRow, ScanRowAdapter.VH>(DIFF) {

    fun submit(rows: List<ScanRow>) {
        submitList(rows)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScanRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = getItem(position)
        holder.binding.line1.text = row.line1
        holder.binding.line2.text = row.line2
    }

    class VH(val binding: ItemScanRowBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScanRow>() {
            override fun areItemsTheSame(a: ScanRow, b: ScanRow): Boolean =
                a.line1 == b.line1 && a.line2 == b.line2

            override fun areContentsTheSame(a: ScanRow, b: ScanRow): Boolean = a == b
        }
    }
}
