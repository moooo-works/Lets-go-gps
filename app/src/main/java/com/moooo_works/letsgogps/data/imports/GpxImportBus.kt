package com.moooo_works.letsgogps.data.imports

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Carries an externally-launched GPX file URI from MainActivity (which sees the
 * incoming ACTION_VIEW intent) over to SettingsScreen (which already owns the
 * import-preview/import-apply flow).
 *
 * Why a singleton bus instead of a NavController argument: the URI may arrive
 * before NavHost is composed (cold start), and content:// URIs are awkward to
 * encode as nav route query parameters.
 *
 * Consumers must call [consume] after handling so the flow latches back to
 * null — otherwise StateFlow's replay would re-trigger the import every time
 * a new collector subscribes (e.g. screen rotation, returning to settings).
 */
@Singleton
class GpxImportBus @Inject constructor() {
    private val _pending = MutableStateFlow<Uri?>(null)
    val pending: StateFlow<Uri?> = _pending.asStateFlow()

    fun emit(uri: Uri) {
        _pending.value = uri
    }

    fun consume() {
        _pending.value = null
    }
}
