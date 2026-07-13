package com.halehoundforge.fire.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.halehoundforge.fire.databinding.ItemScanRowBinding

data class ScanRow(val line1: String, val line2: String)

class ScanRowAdapter : RecyclerView.Adapter<ScanRowAdapter.VH>() {

    private val items = mutableListOf<ScanRow>()

    fun submit(rows: List<ScanRow>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemScanRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.binding.line1.text = row.line1
        holder.binding.line2.text = row.line2
    }

    override fun getItemCount(): Int = items.size

    class VH(val binding: ItemScanRowBinding) : RecyclerView.ViewHolder(binding.root)
}
