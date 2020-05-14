package com.meicm.cas.digitalwellbeing.adapter

import androidx.recyclerview.widget.RecyclerView

abstract class BaseRecyclerAdapter<T, V: RecyclerView.ViewHolder>: RecyclerView.Adapter<V> {

    var list: List<T> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    protected var shortClickListener: RecyclerViewItemShortClick? = null

    constructor(list: List<T>, shortClickListener: RecyclerViewItemShortClick) {
        this.list = list
        this.shortClickListener = shortClickListener
    }

    constructor(shortClickListener: RecyclerViewItemShortClick) {
        this.shortClickListener = shortClickListener
    }

    constructor()

    fun setItems(list: List<T>) {
        this.list = list
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return list.size
    }
}