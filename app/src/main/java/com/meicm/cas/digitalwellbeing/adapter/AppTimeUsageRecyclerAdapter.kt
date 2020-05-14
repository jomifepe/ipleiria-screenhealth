package com.meicm.cas.digitalwellbeing.adapter

import android.app.usage.UsageStats
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.meicm.cas.digitalwellbeing.R
import kotlinx.android.synthetic.main.app_time_usage_list_item.view.*

class AppTimeUsageRecyclerAdapter(onShortClick: RecyclerViewItemShortClick):
    BaseRecyclerAdapter<UsageStats, AppTimeUsageRecyclerAdapter.ViewHolder>(onShortClick) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_time_usage_list_item, parent, false)
        val viewHolder = ViewHolder(view)

        if (shortClickListener != null) {
            view.setOnClickListener { v -> shortClickListener!!.onShortClick(v, viewHolder.layoutPosition) }
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = list[position]
        val seconds: Long = (data.totalTimeInForeground / 1000) % 60
        val minutes: Long = (data.totalTimeInForeground / (1000 * 60)) % 60
        val hours: Long = (data.totalTimeInForeground / (1000 * 60 * 60))
        val time = "$hours h $minutes min $seconds s"

        holder.name.text = data.packageName
        holder.time.text = time
    }

    class ViewHolder (view: View): RecyclerView.ViewHolder(view) {
        val name: TextView = view.tv_app_time_name
        val time: TextView = view.tv_app_time_values
    }
}