package com.meicm.cas.digitalwellbeing.ui.adapter

import android.app.usage.UsageStats
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.persistence.entity.AppCategory
import kotlinx.android.synthetic.main.app_time_usage_list_item.view.*

class AppTimeUsageRecyclerAdapter(onShortClick: RecyclerViewItemShortClick):
    BaseRecyclerAdapter<Pair<String, Long>, AppTimeUsageRecyclerAdapter.ViewHolder>(onShortClick) {

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

//        val seconds: Long = TimeUnit.MILLISECONDS.toHours(data.totalTimeInForeground)
//        val minutes: Long = TimeUnit.MILLISECONDS.toMinutes(data.totalTimeInForeground)
//        val hours: Long = TimeUnit.MILLISECONDS.toSeconds(data.totalTimeInForeground)

        val seconds: Long = (data.second / 1000) % 60
        val minutes: Long = (data.second / (1000 * 60)) % 60
        val hours: Long = (data.second / (1000 * 60 * 60))
        val time = "$hours h $minutes min $seconds s"

        holder.name.text = data.first
        holder.time.text = time
    }

    class ViewHolder (view: View): RecyclerView.ViewHolder(view) {
        val name: TextView = view.tv_app_time_name
        val time: TextView = view.tv_app_time_values
    }
}