package com.meicm.cas.digitalwellbeing.service

import android.app.Activity
import android.app.IntentService
import android.content.Intent
import android.util.Log
import android.view.SurfaceControl
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransition.*
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.DetectedActivity.*
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.util.Const
import com.meicm.cas.digitalwellbeing.util.activityToString
import com.meicm.cas.digitalwellbeing.util.transactionTypeToString

class ActivityRecognitionIntentService : IntentService(Const.SERVICE_NAME_ACTIVITY_RECOGNITION) {

    private val acceptableConfidence: Int = 70

    override fun onHandleIntent(intent: Intent?) {
        Log.d(Const.LOG_TAG, "onHandleIntent")
        handleActivityTransition(intent)
        handleActivityRecognition(intent)
    }

    private fun handleActivityRecognition(intent: Intent?) {
        Log.d(Const.LOG_TAG, "handleActivityRecognition")

        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result: ActivityRecognitionResult = ActivityRecognitionResult.extractResult(intent)
        val detectedActivities: MutableList<DetectedActivity> = result.probableActivities

        detectedActivities.sortByDescending { it.confidence }
        //debug only
        detectedActivities.forEach {
            Log.d(Const.LOG_TAG,
                "Activity: ${activityToString(it.type)} with confidence of: ${it.confidence}")
        }

        if (detectedActivities.first().confidence < acceptableConfidence) return
        Log.d(Const.LOG_TAG,
            "Saving: ${activityToString(detectedActivities.first().type)} with confidence of: ${detectedActivities.first().confidence} as the current activity")
        AppPreferences.with(this).save(Const.PREF_CURRENT_ACTIVITY, detectedActivities.first().type)
    }

    /*private fun handleActivityRecognition(intent: Intent?){
        Log.d(Const.LOG_TAG, "handleActivityRecognition")

        if(!ActivityRecognitionResult.hasResult(intent)) return
        val result: ActivityRecognitionResult = ActivityRecognitionResult.extractResult(intent)
        val detectedActivities: MutableList<DetectedActivity> = result.probableActivities

        val activities = mutableListOf<String>()
        val confidences = mutableListOf<Int>()

        detectedActivities.sortByDescending { it.confidence }
        if(detectedActivities.first().confidence < acceptableConfidence) return

        val pref = AppPreferences.with(this)

        pref.save(Const.PREF_CURRENT_ACTIVITY,  5)

        detectedActivities.forEach {

            //pref.save(Const.PREF_CURRENT_ACTIVITY, it.type)


            activities.add(activityToString(it.type))
            confidences.add(it.confidence)
            Log.d(
                Const.LOG_TAG,
                "Activity: ${activityToString(it.type)} with confidence of: ${it.confidence}"
            )
        }
    }*/

    private fun handleActivityTransition(intent: Intent?) {
        Log.d(Const.LOG_TAG, "handleActivityTransition")
        if (!ActivityTransitionResult.hasResult(intent)) return

        val result = ActivityTransitionResult.extractResult(intent)!!

        //debug only
        result.transitionEvents.forEach {
            Log.d(Const.LOG_TAG,
                "Activity: ${activityToString(it.activityType)} transaction type: ${transactionTypeToString(
                    it.transitionType)}"
            )
        }

        val currentActivity = result.transitionEvents.last()
        val pref = AppPreferences.with(this)
        if (currentActivity.transitionType == ACTIVITY_TRANSITION_ENTER) {
            pref.save(Const.PREF_CURRENT_ACTIVITY, currentActivity.activityType)

            Log.d(Const.LOG_TAG,
                "Saving: ${activityToString(currentActivity.activityType)} transaction type: ${transactionTypeToString(
                    currentActivity.transitionType)} as the current activity"
            )
            return
        }
        //ACTIVITY_TRANSITION_EXIT
        pref.remove(Const.PREF_CURRENT_ACTIVITY)
        Log.d(Const.LOG_TAG, "Removing the current activity")
    }

    /*
    override fun onHandleIntent(intent: Intent?) {

        Log.d(Const.LOG_TAG, "Vem aqui")

        if (intent == null) return

        val result: ActivityRecognitionResult = ActivityRecognitionResult.extractResult(intent)
        val detectedActivities: List<DetectedActivity> = result.probableActivities

        val activities = mutableListOf<String>()
        val confidences = mutableListOf<Int>()

        detectedActivities.forEach {
            activities.add(getActivityString(it.type))
            confidences.add(it.confidence)
            Log.d(
                Const.LOG_TAG,
                "Activity: ${getActivityString(it.type)} with confidence of: ${it.confidence}"
            )
        }

        /*
        * JSONObject json = new JSONObject();
		String str = null;
		try {
			long now = System.currentTimeMillis();
			json.put("time", now);
			json.put("offset", TimeZone.getDefault().getOffset(now));
			json.put("activities", new JSONArray(activities));
			json.put("confidences", new JSONArray(confidences));
			str = json.toString();
		} catch (JSONException e) {
			if(Const.DEBUG) e.printStackTrace();
		}

		SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
		sp.edit().putString(Const.PREF_LAST_ACTIVITY, str).apply();*/
    }
    */
}