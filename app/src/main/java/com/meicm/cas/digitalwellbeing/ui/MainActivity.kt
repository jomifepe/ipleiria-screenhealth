package com.meicm.cas.digitalwellbeing.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.os.Handler
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.postDelayed
import androidx.databinding.DataBindingUtil
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.meicm.cas.digitalwellbeing.AppState
import com.meicm.cas.digitalwellbeing.R
import com.meicm.cas.digitalwellbeing.communication.TimeRangeMessageEvent
import com.meicm.cas.digitalwellbeing.databinding.ActivityMainBinding
import com.meicm.cas.digitalwellbeing.persistence.AppPreferences
import com.meicm.cas.digitalwellbeing.service.ActivityRecognitionIntentService
import com.meicm.cas.digitalwellbeing.util.*
import com.meicm.cas.digitalwellbeing.service.AppUsageGathererService
import com.meicm.cas.digitalwellbeing.service.UnlockService
import com.meicm.cas.digitalwellbeing.util.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.partial_time_picker.view.*
import org.greenrobot.eventbus.EventBus
import java.util.*

private const val PERMISSIONS_REQUEST_USAGE_STATS = 1001
private const val PERMISSIONS_REQUEST_BATTERY_OPTIMIZATION = 1002

class MainActivity : AppCompatActivity() {
    private var rangeModifier: Int = 0
    private var startTime: Calendar = getStartOfDayCalendar()
    private var endTime: Calendar = getEndOfDayCalendar()
    private var backPressedOnce = false
    private lateinit var unlocksServiceIntent: Intent
    private lateinit var usageGathererServiceIntent: Intent

    private var recognitionIntent = Intent()
    private var recognitionService: ActivityRecognitionIntentService =
        ActivityRecognitionIntentService()

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


        if (!hasUsagePermission()) {
            showUsagePermissionDialog()
        } else if (!isIgnoringBatteryOptimizations()) {
            showBatteryOptimizationDialog()
        }
        startUnlocksService()
    }

    override fun onResume() {
        super.onResume()
        triggerDataGathering()
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
        Log.d(Const.LOG_TAG, "[MainActivity] onDestroy")

        stopService(unlocksServiceIntent)
        stopService(usageGathererServiceIntent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            PERMISSIONS_REQUEST_USAGE_STATS -> {
                if (!hasUsagePermission()) showNoUsagePermissionWarning()
                if (!isIgnoringBatteryOptimizations()) showBatteryOptimizationDialog()
            }
            PERMISSIONS_REQUEST_BATTERY_OPTIMIZATION -> {
                if (!isIgnoringBatteryOptimizations()) showBatteryOptimizationWarning()
            }
        }
    }

    private fun triggerDataGathering() {
        if (!hasUsagePermission()) return
        startService(Intent(this, AppUsageGathererService::class.java))
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
        builder
            .setTitle("Usage Access")
            .setMessage("This app needs to have access to your " +
                    "device's app usage and statistics in order to work")
            .setPositiveButton(android.R.string.ok) {
                    dialog, which -> showPermissionAccessSettings() }
            .setNegativeButton(android.R.string.cancel) {
                    dialog, which -> showNoUsagePermissionWarning() }


        builder.show()
    }

    private fun showBatteryOptimizationDialog() {
        val builder = AlertDialog.Builder(this)
        builder
            .setTitle("Battery Optimization")
            .setMessage("In order for this app to work properly, " +
                    "please disable it's battery optimizations")
            .setPositiveButton(android.R.string.ok) {
                    dialog, which -> showBatteryOptimizationSettings() }
            .setNegativeButton(android.R.string.cancel) {
                    dialog, which -> showBatteryOptimizationWarning() }

        builder.show()
    }

    private fun showNoUsagePermissionWarning() {
        Toast.makeText(this, "Sorry, but without usage access " +
                "this app won't show anything", Toast.LENGTH_LONG).show()
    }

    private fun showBatteryOptimizationWarning() {
        Toast.makeText(this, "With battery optimization enabled, " +
                "we cannot guarantee full function", Toast.LENGTH_LONG).show()
    }

    private fun showPermissionAccessSettings() {
        startActivityForResult(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            PERMISSIONS_REQUEST_USAGE_STATS
        )
    }

    @SuppressLint("BatteryLife")
    private fun showBatteryOptimizationSettings() {
        startActivityForResult(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }, PERMISSIONS_REQUEST_BATTERY_OPTIMIZATION
        )
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm: PowerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
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
            topLevelDestinationIds = setOf(
                R.id.fragment_usage_statistics,
                R.id.fragment_insights
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(Const.LOG_TAG, "[Main Activity] On destroy")
        stopService(unlocksServiceIntent)
        stopService(usageGathererServiceIntent)
        stopService(recognitionIntent)
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
        EventBus.getDefault()
            .post(TimeRangeMessageEvent(startTime.timeInMillis, endTime.timeInMillis))
        updateTimeRangeLabel()
    }

    private fun updateTimeRangeLabel() {
        time_picker.tv_date_range.text = getDateStringFromEpoch(startTime.timeInMillis, "MMM dd, YYYY")
        time_picker.bt_date_range_forward.visibility =
            if (rangeModifier >= 0) View.INVISIBLE else View.VISIBLE
        time_picker.bt_date_range_backwards.visibility =
            if (rangeModifier <= -7) View.INVISIBLE else View.VISIBLE
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