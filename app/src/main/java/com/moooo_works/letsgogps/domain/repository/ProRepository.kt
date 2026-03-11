package com.moooo_works.letsgogps.domain.repository

import android.app.Activity
import kotlinx.coroutines.flow.StateFlow

interface ProRepository {
    val isProActive: StateFlow<Boolean>
    suspend fun refreshProStatus()
    fun launchBillingFlow(activity: Activity)
}
