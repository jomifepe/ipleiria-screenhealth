package com.meicm.cas.screenhealth.util

object Const {
    const val BASE_PACKAGE = "com.meicm.cas.screenhealth"

    const val SERVICE_NAME_DATA_GATHERER = "Service_UsageGatherer"
    const val SERVICE_NAME_ACTIVITY_RECOGNITION = "Service_ActivityRecognition"
    const val NOTIFICATION_CHANNEL_GENERAL = "General"
    const val LOG_TAG = "DW_LOGGING"
    const val DATABASE_NAME = "ScreenHealth"

    const val PREF_NAME = "SH_Preferences"
    const val PREF_APP_RUN = "$BASE_PACKAGE.preference.app_run"
    const val PREF_KEY_SNOOZE_LONG = "$BASE_PACKAGE.preference.uw_snooze"
    const val PREF_LAST_UW_TIMER_ELAPSED = "$BASE_PACKAGE.preference.uw_last_time"
    const val PREF_UW_LAST_NOTIFICATION_ID = "$BASE_PACKAGE.preference.last_notification_id"
    const val PREF_LOCK_TIME = "$BASE_PACKAGE.preference.lock_time"
    const val PREF_CURRENT_ACTIVITY = "$BASE_PACKAGE.preference.current_activity"

    const val EVENT_TIME_RANGE = "$BASE_PACKAGE.event.timerange"

    const val ACTION_FIRST_LAUNCH = "$BASE_PACKAGE.intent.action.FIRST_LAUNCH"
    const val ACTION_UNLOCK_SERVICE_RESTART = "$BASE_PACKAGE.intent.action.UNLOCK_SERVICE_RESTART"
    const val ACTION_GATHER_DATA = "$BASE_PACKAGE.intent.action.GATHER_DATA"

    const val UW_UNLOCK_THRESHOLD_MS = 2000L
    const val UW_ANALYSED_APPS_THRESHOLD_MS = 60 * 60000L
    const val UW_TIME_TO_TRIGGER = 5000L
    val UW_ALLOWED_CATEGORIES = setOf("Productivity", "Tools")

    //Used on requestActivityUpdates to
    const val ACTIVITY_UPDATE_TIME = 200L

    //Used on analyse Notification Condition to give the required percentage of allowed apps to whether or not send a notification
    const val ALLOWED_APPS_PERCENTAGE = 0.5
}