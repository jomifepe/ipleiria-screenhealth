package com.meicm.cas.digitalwellbeing.persistence

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import com.meicm.cas.digitalwellbeing.util.Const

class AppPreferences {

    companion object {
        private var instance: AppPreferences? = null
        private lateinit var preferences: SharedPreferences
        private lateinit var editor: SharedPreferences.Editor

        fun with(context: Context) : AppPreferences {
            if (null == instance)
                instance = Builder(context).build()
            return instance as AppPreferences
        }

        fun with(context: Context, mode: Int) : AppPreferences {
            if (null == instance)
                instance = Builder(context, mode).build()
            return instance as AppPreferences
        }
    }

    constructor()

    @SuppressLint("CommitPrefEdits")
    constructor(context: Context, mode: Int) {
        preferences = context.getSharedPreferences(Const.PREF_NAME, mode)
        editor = preferences.edit()
    }

    @SuppressLint("CommitPrefEdits")
    constructor(context: Context) {
        preferences = context.getSharedPreferences(Const.PREF_NAME, Context.MODE_PRIVATE)
        editor = preferences.edit()
    }

    fun contains(key: String): Boolean {
        return preferences.contains(key)
    }

    fun save(key: String, value: Boolean) {
        editor.putBoolean(key, value).apply()
    }

    fun save(key: String, value: Float) {
        editor.putFloat(key, value).apply()
    }

    fun save(key: String, value: Int) {
        editor.putInt(key, value).apply()
    }

    fun save(key: String, value: Long) {
        editor.putLong(key, value).apply()
    }

    fun save(key: String, value: String) {
        editor.putString(key, value).apply()
    }

    fun save(key: String, value: Set<String>) {
        editor.putStringSet(key, value).apply()
    }

    fun getBoolean(key: String, defValue: Boolean) : Boolean {
        return preferences.getBoolean(key, defValue)
    }

    fun getFloat(key: String, defValue: Float) : Float {
        return try {
            preferences.getFloat(key, defValue)
        } catch (ex: ClassCastException) {
            preferences.getString(key, defValue.toString())!!.toFloat()
        }
    }

    fun getInt(key: String, defValue: Int) : Int {
        return try {
            preferences.getInt(key, defValue)
        } catch (ex: ClassCastException) {
            preferences.getString(key, defValue.toString())!!.toInt()
        }
    }

    fun getLong(key: String, defValue: Long) : Long {
        return try {
            preferences.getLong(key, defValue)
        } catch (ex: ClassCastException) {
            preferences.getString(key, defValue.toString())!!.toLong()
        }
    }

    fun getString(key: String, defValue: String) : String? {
        return preferences.getString(key, defValue)
    }

    fun getStringSet(key: String, defValue: Set<String>) : Set<String>? {
        return preferences.getStringSet(key, defValue)
    }

    fun getAll(): MutableMap<String, *>? {
        return preferences.all
    }

    fun remove(key: String) {
        editor.remove(key).apply()
    }

    fun clear() {
        editor.clear().apply()
    }

    private class Builder(val context: Context, val mode: Int? = null) {

        fun build() : AppPreferences {
            return if (mode == null) AppPreferences(context) else AppPreferences(context, mode)
        }
    }
}
