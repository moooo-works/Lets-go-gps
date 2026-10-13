package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng

/** 每個浮窗命令保留原始目標；關閉、取消或新命令會使舊回呼失效。 */
internal class CompanionExecutionRequests {
    data class Request(val token: Long, val action: CompanionAction, val previousRoute: List<LatLng>?)
    private var generation = 0L

    fun create(action: CompanionAction, previousRoute: List<LatLng>?): Request {
        val snapshot = when (action) {
            is CompanionAction.UseRoute -> action.copy(points = action.points.toList())
            is CompanionAction.PlayRoute -> action.copy(points = action.points.toList())
            else -> action
        }
        return Request(++generation, snapshot, previousRoute?.toList())
    }

    fun isValid(request: Request, currentRoute: List<LatLng>?): Boolean =
        request.token == generation && request.previousRoute == currentRoute

    fun isCurrent(request: Request): Boolean = request.token == generation

    fun invalidate() { generation++ }
}
