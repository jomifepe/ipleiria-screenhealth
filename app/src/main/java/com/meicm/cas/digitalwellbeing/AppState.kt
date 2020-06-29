package com.meicm.cas.digitalwellbeing

object AppState {
    var isUnlocked: Boolean = false
    var unlockTime: Long? = null
    var currentNotificationId: Int? = null
    var lastUWTimerStart: Long? = null

    var isGatheringUsageStats: Boolean = false
    var isGatheringUnlocks: Boolean = false
    var isGatheringCategories: Boolean = false
}