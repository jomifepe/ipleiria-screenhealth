package com.meicm.cas.digitalwellbeing.ui

import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.opengl.Visibility
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.provider.Settings
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.databinding.DataBindingUtil
import androidx.navigation.NavController
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.communication.TimeRangeMessageEvent
import com.meicm.cas.digitalwellbeing.databinding.ActivityMainBinding
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.service.ActivityRecognitionIntentService
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.service.AppUsageGathererService
import com.meicm.cas.digitalwellbeing.service.UnlockService
import com.meicm.cas.digitalwellbeing.util.*
import kotlinx.android.synthetic.main.activity_app_usage.view.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.partial_time_picker.view.*
import org.greenrobot.eventbus.EventBus
import java.util.*

private const val MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS = 3

class MainActivity : AppCompatActivity() {
    private var rangeModifier: Int = 0
    private var startTime: Calendar = getStartOfDayCalendar()
    private var endTime: Calendar = getEndOfDayCalendar()
    private var backPressedOnce = false
    private lateinit var unlocksServiceIntent: Intent
    private lateinit var usageGathererServiceIntent: Intent

    private var recognitionIntent = Intent()
    private var recognitionService: ActivityRecognitionIntentService = ActivityRecognitionIntentService()

    init {
        startTime = getStartOfDayCalendar()
        endTime = getEndOfDayCalendar()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityMainBinding>(this, R.layout.activity_main)

        setupNavigation()
        incrementAppRunCount()
        createNotificationChannel()
        updateTimeRangeLabel()
        startActivityRecognitionService()

        time_picker.bt_date_range_backwards.setOnClickListener { incrementOrDecrementTimeRange(-1) }
        time_picker.bt_date_range_forward.setOnClickListener { incrementOrDecrementTimeRange(1) }


        if (!hasUsagePermission()) showUsagePermissionDialog() else enableDataGathering()
        startUnlocksService()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.action_menu, menu)
        return true
    }

    override fun onBackPressed() {
        val navController = findNavController(R.id.myNavHostFragment)

        if (navController.graph.startDestination == navController.currentDestination?.id) {
            if (backPressedOnce) {
                super.onBackPressed()
                return
            }
            backPressedOnce = true
            Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()

            Handler().postDelayed(2000) { backPressedOnce = false }
            return
        }

        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopService(unlocksServiceIntent)
        stopService(usageGathererServiceIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS -> {
                if (!hasUsagePermission()) return
                enableDataGathering()
            }
        }
    }

    private fun enableDataGathering() {
        if (!isServiceRunning(this, AppUsageGathererService::class.java)) {
            usageGathererServiceIntent = Intent(this, AppUsageGathererService::class.java)
            val service = Intent(usageGathererServiceIntent).apply {
                action = Const.ACTION_FIRST_LAUNCH
            }
            startService(service)
        }
    }

    private fun startUnlocksService() {
        if (!isServiceRunning(this, UnlockService::class.java)) {
            unlocksServiceIntent = Intent(this, UnlockService::class.java)
            val service = Intent(unlocksServiceIntent).apply {
                action = Const.ACTION_FIRST_LAUNCH
            }
            startService(service)
        }
    }

    private fun showUsagePermissionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Usage Access")
        builder.setMessage("This app needs to have access to your device's app usage and statistics in order to work")

        builder.setPositiveButton(android.R.string.ok) { dialog, which ->
            showPermissionAccessSettings()
        }

        builder.setNegativeButton(android.R.string.cancel) { dialog, which ->
            Toast.makeText(this, "Sorry, but without usage access this app won't show anything", Toast.LENGTH_LONG).show()
        }

        builder.show()
    }

    private fun showPermissionAccessSettings() {
        startActivityForResult(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            MY_PERMISSIONS_REQUEST_PACKAGE_USAGE_STATS
        )
    }

    private fun hasUsagePermission(): Boolean {
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            val appOpsManager = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                applicationInfo.uid,
                applicationInfo.packageName
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun setupNavigation() {
        val navController = findNavController(R.id.myNavHostFragment)
        bottom_navigation_view.setupWithNavController(navController)
        val appBarConfiguration = AppBarConfiguration(
            topLevelDestinationIds = setOf (
                R.id.fragment_usage_statistics,
                R.id.fragment_insights
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onDestroy() {
        Log.d(Const.LOG_TAG, "On destroy")
        stopService(recognitionIntent)
        super.onDestroy()
    }

    private fun startActivityRecognitionService() {
        recognitionIntent = Intent(this, ActivityRecognitionIntentService::class.java)
        recognitionService = ActivityRecognitionIntentService()
        if (!isServiceRunning(this, recognitionService.javaClass)) {
            this.startService(recognitionIntent)
        }
    }

    private fun incrementAppRunCount() {
        val pref = AppPreferences.with(this)
        val runCount = pref.getInt(Const.PREF_APP_RUN, 0)
        if (runCount < 2) pref.save(Const.PREF_APP_RUN, runCount + 1)
    }

    private fun incrementOrDecrementTimeRange(days: Int) {
        // TODO
        rangeModifier += days
        startTime.add(Calendar.DAY_OF_YEAR, days)
        endTime.add(Calendar.DAY_OF_YEAR, days)
        EventBus.getDefault().post(TimeRangeMessageEvent(startTime.timeInMillis, endTime.timeInMillis))
        updateTimeRangeLabel()
    }

    private fun updateTimeRangeLabel() {
        time_picker.tv_date_range.text = getDateStringFromEpoch(startTime.timeInMillis)
        if (rangeModifier >= 0) {
            time_picker.bt_date_range_forward.visibility = View.INVISIBLE
        } else {
            time_picker.bt_date_range_forward.visibility = View.VISIBLE
        }
        if (rangeModifier <= -7) {
            time_picker.bt_date_range_backwards.visibility = View.INVISIBLE
        } else {
            time_picker.bt_date_range_backwards.visibility = View.VISIBLE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "General"
            val channel = NotificationChannel(
                Const.NOTIFICATION_CHANNEL_GENERAL,
                name,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            channel.description = "General notifications"
            channel.setShowBadge(true)

            val notificationManager = this.getSystemService(NotificationManager::class.java)
            notificationManager!!.createNotificationChannel(channel)
        }
    }
}
