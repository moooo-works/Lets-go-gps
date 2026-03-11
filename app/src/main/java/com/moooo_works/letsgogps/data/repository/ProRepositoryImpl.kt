package com.moooo_works.letsgogps.data.repository

import android.app.Activity
import com.moooo_works.letsgogps.data.billing.BillingManager
import com.moooo_works.letsgogps.domain.repository.ProRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProRepositoryImpl @Inject constructor(
    private val billingManager: BillingManager
) : ProRepository {

    override val isProActive: StateFlow<Boolean> = billingManager.isProActive

    override suspend fun refreshProStatus() {
        billingManager.queryActiveSubscriptions()
    }

    override fun launchBillingFlow(activity: Activity) {
        billingManager.launchBillingFlow(activity)
    }
}
