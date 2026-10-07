package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import android.content.Context
import androidx.annotation.MainThread
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.unity3d.ads.metadata.MetaData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
import javax.inject.Inject
import javax.inject.Singleton

data class AdConsentState(
    val canRequestAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false,
    val revision: Long = 0,
    val busy: Boolean = false,
    val error: String? = null
)

interface AdConsentGate {
    val state: StateFlow<AdConsentState>
}

internal interface ConsentBackend {
    val canRequestAds: Boolean
    val privacyOptionsRequired: Boolean
    fun update(activity: Activity, onSuccess: () -> Unit, onFailure: (String) -> Unit)
    fun showRequiredForm(activity: Activity, onDismiss: (String?) -> Unit)
    fun showPrivacyOptions(activity: Activity, onDismiss: (String?) -> Unit)
    fun applyMediationPrivacy(): Boolean
}

@Singleton
class AdConsentManager internal constructor(private val backend: ConsentBackend) : AdConsentGate {
    @Inject constructor(@ApplicationContext context: Context) : this(UmpConsentBackend(context))

    private val mutableState = MutableStateFlow(AdConsentState())
    override val state = mutableState.asStateFlow()
    private var operation = 0L
    private var updatedThisLaunch = false
    private var owner = WeakReference<Activity>(null)
    private enum class Phase { Idle, Updating, Form }
    private var phase = Phase.Idle

    @MainThread
    fun gatherConsent(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (phase == Phase.Idle && updatedThisLaunch) return
        if (phase != Phase.Idle && owner.get() === activity) return
        val token = begin(activity, Phase.Updating)
        backend.update(activity, onSuccess = {
            if (token != operation || phase != Phase.Updating) return@update
            val currentActivity = owner.get()
            if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
                cancel()
                return@update
            }
            phase = Phase.Form
            backend.showRequiredForm(currentActivity) { error -> finish(token, error) }
        }, onFailure = { error ->
            if (phase == Phase.Updating) finish(token, error)
        })
    }

    @MainThread
    fun showPrivacyOptions(activity: Activity) {
        if (phase != Phase.Idle || !state.value.privacyOptionsRequired ||
            activity.isFinishing || activity.isDestroyed) return
        val token = begin(activity, Phase.Form)
        backend.showPrivacyOptions(activity) { error -> finish(token, error) }
    }

    private fun begin(activity: Activity, nextPhase: Phase): Long {
        operation++
        owner = WeakReference(activity)
        phase = nextPhase
        mutableState.value = state.value.copy(
            canRequestAds = false, revision = state.value.revision + 1, busy = true, error = null
        )
        return operation
    }

    private fun cancel() {
        phase = Phase.Idle
        updatedThisLaunch = false
        owner.clear()
        mutableState.value = state.value.copy(canRequestAds = false, busy = false)
    }

    private fun finish(token: Long, error: String?) {
        if (token != operation || phase == Phase.Idle) return
        val currentActivity = owner.get()
        if (currentActivity == null || currentActivity.isFinishing || currentActivity.isDestroyed) {
            cancel()
            return
        }
        phase = Phase.Idle
        updatedThisLaunch = true
        owner.clear()
        // canRequestAds 只代表可請求廣告，不代表同意個人化。
        val allowed = backend.canRequestAds
        val privacyApplied = !allowed || backend.applyMediationPrivacy()
        mutableState.value = AdConsentState(
            canRequestAds = allowed && privacyApplied,
            privacyOptionsRequired = backend.privacyOptionsRequired,
            revision = state.value.revision + 1,
            error = if (privacyApplied) error else "Mediation privacy configuration failed"
        )
    }
}

private class UmpConsentBackend(context: Context) : ConsentBackend {
    private val context = context.applicationContext
    private val information = UserMessagingPlatform.getConsentInformation(this.context)
    override val canRequestAds get() = information.canRequestAds()
    override val privacyOptionsRequired get() = information.privacyOptionsRequirementStatus ==
        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    override fun update(activity: Activity, onSuccess: () -> Unit, onFailure: (String) -> Unit) {
        information.requestConsentInfoUpdate(
            activity, ConsentRequestParameters.Builder().build(), onSuccess,
            { error -> onFailure(error.message) }
        )
    }

    override fun showRequiredForm(activity: Activity, onDismiss: (String?) -> Unit) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { onDismiss(it?.message) }
    }

    override fun showPrivacyOptions(activity: Activity, onDismiss: (String?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { onDismiss(it?.message) }
    }

    override fun applyMediationPrivacy(): Boolean = try {
        // GPP 自動傳遞尚未驗證；暫時對 Unity 一律限制個人化，不假定使用者 opt-in。
        MetaData(context).let { metadata ->
            if (metadata.set("privacy.consent", false)) {
                metadata.commit()
                true // commit 無成功回傳；實機仍須驗證 SDK 訊號。
            } else false
        }
    } catch (_: RuntimeException) {
        false
    }
}
