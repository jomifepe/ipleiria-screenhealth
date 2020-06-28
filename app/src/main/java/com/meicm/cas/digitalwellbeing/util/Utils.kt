package com.meicm.cas.digitalwellbeing.util

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*

fun compareTimestampsDateEqual(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = Calendar.getInstance()
    cal1.timeInMillis = timestamp1
    cal1.set(Calendar.HOUR_OF_DAY, 0)
    cal1.set(Calendar.MINUTE, 0)
    cal1.set(Calendar.SECOND, 0)
    cal1.set(Calendar.MILLISECOND, 0)
    val cal2 = Calendar.getInstance()
    cal2.timeInMillis = timestamp2
    cal2.set(Calendar.HOUR_OF_DAY, 0)
    cal2.set(Calendar.MINUTE, 0)
    cal2.set(Calendar.SECOND, 0)
    cal2.set(Calendar.MILLISECOND, 0)

    return cal1.timeInMillis == cal2.timeInMillis
}

fun getHoursMinutesSeconds(timeInMillis: Long): Triple<Long, Long, Long> {
    val seconds: Long = (timeInMillis / 1000) % 60
    val minutes: Long = (timeInMillis / (1000 * 60)) % 60
    val hours: Long = (timeInMillis / (1000 * 60 * 60))

    return Triple(hours, minutes, seconds)
}

fun getHoursMinutesSecondsString(timeInMillis: Long): String {
    val hms = getHoursMinutesSeconds(timeInMillis)
    val str = StringBuilder()
    if (hms.first > 0) str.append("${hms.first} h")
    if (hms.second > 0) str.append(" ${hms.second} min")
    if (hms.third > 0) str.append(" ${hms.third} s")
    return str.toString()
}

fun getAppName(context: Context, packageName: String): String {
    return try {
        val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
        appInfo.loadLabel(context.packageManager).toString()
    } catch (ex: PackageManager.NameNotFoundException) {
        packageName
    }
}

fun Calendar.setStartOfDay() {
    this.set(Calendar.HOUR_OF_DAY, 0)
    this.set(Calendar.MINUTE, 0)
    this.set(Calendar.SECOND, 0)
    this.set(Calendar.MILLISECOND, 0)
}

fun Calendar.setEndOfDay() {
    this.set(Calendar.HOUR_OF_DAY, 23)
    this.set(Calendar.MINUTE, 59)
    this.set(Calendar.SECOND, 59)
    this.set(Calendar.MILLISECOND, 999)
}

private fun getDateStringFromEpoch(timestamp: Long, format: String): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return SimpleDateFormat(format, Locale.getDefault()).format(cal.time)
}

fun getDateStringFromEpoch(timestamp: Long): String {
    return getDateStringFromEpoch(timestamp, "YYYY-MMM-dd")
}

fun getDateTimeStringFromEpoch(timestamp: Long): String {
    return getDateStringFromEpoch(timestamp, "YYYY-MMM-dd HH:mm:ss")
}

fun getInstalledPackages(context: Context): List<ApplicationInfo> {
    return context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
}

fun isAppFirstRun(context: Context): Boolean {
    return AppPreferences.with(context).getInt(Const.PREF_APP_RUN, 0) == 1
}

fun getApplicationInfo(context: Context, packageName: String): ApplicationInfo {
    return context.packageManager.getApplicationInfo(packageName, 0)
}

fun getApplicationIcon(context: Context, packageName: String): Drawable {
    return context.packageManager.getApplicationIcon(packageName)
}

fun isServiceRunning(context: Context, serviceClass: Class<out Service>): Boolean {
    val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return manager.getRunningServices(Integer.MAX_VALUE).any { it.service.className == serviceClass.name }
}

fun getStartOfDayCalendar(): Calendar {
    val start = Calendar.getInstance()
    start.setStartOfDay()
    return start
}

fun getEndOfDayCalendar(): Calendar {
    val end = Calendar.getInstance()
    end.setEndOfDay()
    return end
}

fun getCalendarFromMillis(millis: Long): Calendar {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return cal
}

