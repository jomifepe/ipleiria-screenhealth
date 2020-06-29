package com.meicm.cas.digitalwellbeing.ui.adapter

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.util.*
import kotlinx.android.synthetic.main.app_time_usage_list_item.view.*
import java.lang.Exception
import java.lang.ref.WeakReference

class AppTimeUsageRecyclerAdapter(onShortClick: RecyclerViewItemShortClick):
    BaseRecyclerAdapter<Pair<String, Long>, AppTimeUsageRecyclerAdapter.ViewHolder>(onShortClick) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.app_time_usage_list_item, parent, false)
        val viewHolder = ViewHolder(view)

        if (shortClickListener != null) {
            view.setOnClickListener { v ->
                shortClickListener!!.onShortClick(v, viewHolder.layoutPosition) }
        }
        return viewHolder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = list[position]
        val time = getHoursMinutesSecondsString(data.second)
        holder.name.text = getAppName(holder.name.context, data.first)
        holder.time.text = time
        val isInstalled = isPackageInstalled(holder.uninstalled.context, data.first)
        holder.uninstalled.visibility = if (isInstalled) View.GONE else View.VISIBLE

        try {
            holder.icon.setImageDrawable(getApplicationIcon(holder.icon.context, data.first))
        } catch (ex: Exception) {}
        Log.d(Const.LOG_TAG, "onBindViewHolder")
    }

    class ViewHolder (view: View): RecyclerView.ViewHolder(view) {
        val name: TextView = view.tv_app_time_name
        val time: TextView = view.tv_app_time_values
        val icon: ImageView = view.iv_app_time_icon
        val uninstalled: TextView = view.tv_uninstalled
    }
}