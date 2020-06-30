package com.meicm.cas.screenhealth.persistence.entity

import androidx.room.Embedded

data class AppSessionWithCategory (
    @Embedded val appSession: AppSession,
    @Embedded val appCategory: AppCategory
)