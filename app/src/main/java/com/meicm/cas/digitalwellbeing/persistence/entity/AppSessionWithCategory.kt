package com.meicm.cas.digitalwellbeing.persistence.entity

import androidx.room.Embedded

data class AppSessionWithCategory (
    @Embedded val appSession: AppSession,
    @Embedded val appCategory: AppCategory
)