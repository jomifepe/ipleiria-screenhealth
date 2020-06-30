package com.meicm.cas.screenhealth.service

import android.app.IntentService
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.location.ActivityTransition.*
import com.meicm.cas.screenhealth.persistence.AppPreferences
import com.meicm.cas.screenhealth.util.Const
import com.meicm.cas.screenhealth.util.activityToString
import com.meicm.cas.screenhealth.util.transactionTypeToString

class ActivityRecognitionIntentService : IntentService(Const.SERVICE_NAME_ACTIVITY_RECOGNITION) {

    private val acceptableConfidence: Int = 70

    override fun onHandleIntent(intent: Intent?) {
        handleActivityTransition(intent)
        handleActivityRecognition(intent)
    }

    private fun handleActivityRecognition(intent: Intent?) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result: ActivityRecognitionResult = ActivityRecognitionResult.extractResult(intent)
        val detectedActivities: MutableList<DetectedActivity> = result.probableActivities

        detectedActivities.sortByDescending { it.confidence }
        //debug only
        detectedActivities.forEach {
            Log.d(
                Const.LOG_TAG,
                "Activity: ${activityToString(it.type)} with confidence of: ${it.confidence}%"
            )
        }

        if (detectedActivities.first().confidence < acceptableConfidence) return
        Log.d(
            Const.LOG_TAG,
            "Saving: ${activityToString(detectedActivities.first().type)} with confidence of: ${detectedActivities.first().confidence}% as the current activity"
        )
        AppPreferences.with(this).save(Const.PREF_CURRENT_ACTIVITY, detectedActivities.first().type)
    }

    private fun handleActivityTransition(intent: Intent?) {
        Log.d(Const.LOG_TAG, "handleActivityTransition")
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent)!!

        //debug only
        result.transitionEvents.forEach {
            Log.d(
                Const.LOG_TAG,
                "Activity: ${activityToString(it.activityType)} transaction type: ${transactionTypeToString(
                    it.transitionType
                )}"
            )
        }

        val currentActivity = result.transitionEvents.last()
        val pref = AppPreferences.with(this)
        if (currentActivity.transitionType == ACTIVITY_TRANSITION_ENTER) {
            pref.save(Const.PREF_CURRENT_ACTIVITY, currentActivity.activityType)

            Log.d(
                Const.LOG_TAG,
                "Saving: ${activityToString(currentActivity.activityType)} transaction type: ${transactionTypeToString(
                    currentActivity.transitionType
                )} as the current activity"
            )
            return
        }
        //ACTIVITY_TRANSITION_EXIT
        pref.remove(Const.PREF_CURRENT_ACTIVITY)
        Log.d(Const.LOG_TAG, "Removing the current activity")
    }
}