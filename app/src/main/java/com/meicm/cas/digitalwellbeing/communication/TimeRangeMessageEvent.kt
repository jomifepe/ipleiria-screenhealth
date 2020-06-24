package com.meicm.cas.digitalwellbeing.communication

import com.meicm.cas.digitalwellbeing.util.Const

class TimeRangeMessageEvent(
    val startTimestamp: Long,
    val endTimestamp: Long
): MessageEvent(Const.EVENT_TIME_RANGE)