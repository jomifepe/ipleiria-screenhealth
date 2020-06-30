package com.meicm.cas.screenhealth.communication

import com.meicm.cas.screenhealth.util.Const

class TimeRangeMessageEvent(
    val startTimestamp: Long,
    val endTimestamp: Long
): MessageEvent(Const.EVENT_TIME_RANGE)