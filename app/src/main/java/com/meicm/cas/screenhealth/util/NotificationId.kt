package com.meicm.cas.screenhealth.util

import java.util.concurrent.atomic.AtomicInteger

class NotificationId {
    companion object {
        private val atom: AtomicInteger = AtomicInteger(0)

        fun get(): Int = atom.get()
        fun getNewId(): Int = atom.getAndIncrement()
    }
}