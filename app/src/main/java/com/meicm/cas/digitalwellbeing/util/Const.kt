package com.meicm.cas.digitalwellbeing.util

object Const {
    const val BASE_PACKAGE = "com.meicm.cas.digitalwellbeing"
    const val SERVICE_NAME_DATA_GATHERER = "Service_UsageGatherer"
    const val NOTIFICATION_CHANNEL_GENERAL = "General"
    const val LOG_TAG = "DW_LOGGING"
    const val DATABASE_NAME = "DigitalWellbeing"

    const val PREF_NAME = "DW_Preferences"
    const val PREF_APP_RUN = "$BASE_PACKAGE.preference.app_run"
    const val PREF_KEY_SNOOZE_LONG = "$BASE_PACKAGE.preference.uw_snooze"
    const val PREF_UW_LAST_TIME = "$BASE_PACKAGE.preference.uw_last_time"
    const val PREF_UW_LAST_NOTIFICATION_ID = "$BASE_PACKAGE.preference.last_notification_id"

    const val EVENT_TIME_RANGE = "$BASE_PACKAGE.event.timerange"

    const val ACTION_FIRST_LAUNCH = "$BASE_PACKAGE.intent.action.FIRST_LAUNCH"
    const val ACTION_UNLOCK_SERVICE_RESTART = "$BASE_PACKAGE.intent.action.UNLOCK_SERVICE_RESTART"
}