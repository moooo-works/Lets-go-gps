package com.moooo_works.letsgogps.data.billing

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.common.RequestConfiguration
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.moooo_works.letsgogps.BuildConfig
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AdMobInitializationState {
    data object NotStarted : AdMobInitializationState
    data object Initializing : AdMobInitializationState
    data object Ready : AdMobInitializationState
    data object Failed : AdMobInitializationState
}

interface AdMobInitializationGate {
    fun initialize()
    fun whenReady(callback: (Boolean) -> Unit)
}

@Singleton
class AdMobInitializer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val consent: AdConsentManager
) : AdMobInitializationGate {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    private val callbackLock = Any()
    private val pendingCallbacks = mutableListOf<(Boolean) -> Unit>()
    private val mutableState = MutableStateFlow<AdMobInitializationState>(AdMobInitializationState.NotStarted)

    val state: StateFlow<AdMobInitializationState> = mutableState.asStateFlow()

    init {
        scope.launch(Dispatchers.Main.immediate) {
            consent.state.collect { if (it.canRequestAds) initialize() }
        }
    }

    override fun initialize() {
        if (!consent.state.value.canRequestAds) return
        if (!started.compareAndSet(false, true)) return

        mutableState.value = AdMobInitializationState.Initializing
        scope.launch {
            try {
                if (!consent.state.value.canRequestAds) {
                    started.set(false)
                    mutableState.value = AdMobInitializationState.NotStarted
                    drainCallbacks(success = false)
                    return@launch
                }
                val requestConfiguration = RequestConfiguration.Builder()
                    .apply {
                        if (BuildConfig.DEBUG) {
                            setTestDeviceIds(listOf(TEST_DEVICE_ID))
                        }
                    }
                    .build()
                val initializationConfig = InitializationConfig.Builder(BuildConfig.ADMOB_APP_ID)
                    .setRequestConfiguration(requestConfiguration)
                    .build()

                MobileAds.initialize(context, initializationConfig) {
                    complete(success = true)
                }
            } catch (_: RuntimeException) {
                complete(success = false)
            }
        }
    }

    override fun whenReady(callback: (Boolean) -> Unit) {
        if (!consent.state.value.canRequestAds) {
            dispatchToMain { callback(false) }
            return
        }
        val completedState = synchronized(callbackLock) {
            when (mutableState.value) {
                AdMobInitializationState.Ready -> true
                AdMobInitializationState.Failed -> false
                AdMobInitializationState.NotStarted,
                AdMobInitializationState.Initializing -> {
                    pendingCallbacks += callback
                    null
                }
            }
        }

        if (completedState != null) {
            dispatchToMain { callback(completedState && consent.state.value.canRequestAds) }
        } else {
            initialize()
        }
    }

    private fun complete(success: Boolean) {
        mutableState.value = if (success) AdMobInitializationState.Ready else AdMobInitializationState.Failed
        drainCallbacks(success)
    }

    private fun drainCallbacks(success: Boolean) {
        val callbacks = synchronized(callbackLock) {
            pendingCallbacks.toList().also { pendingCallbacks.clear() }
        }
        dispatchToMain {
            callbacks.forEach { callback -> callback(success && consent.state.value.canRequestAds) }
        }
    }

    private fun dispatchToMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    companion object {
        private const val TEST_DEVICE_ID = "3E51381E6BA58281AEEC253ACF8F7529"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdMobInitializerEntryPoint {
    fun adMobInitializer(): AdMobInitializer
    fun adConsentManager(): AdConsentManager
}
