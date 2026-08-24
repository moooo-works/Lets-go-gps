package com.moooo_works.letsgogps.data.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Health Connect 的可用性與權限查詢。
 *
 * 所有對外方法在任何裝置上都不得拋出例外——不支援的裝置只會回報
 * [Status.Unsupported]，呼叫端據此降級 UI 即可。
 */
@Singleton
class HealthConnectAvailability @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Health Connect 在本裝置上的狀態。 */
    enum class Status {
        /** 裝置不支援，或 SDK 查詢失敗。 */
        Unsupported,

        /** 支援，但 Health Connect 未安裝或版本過舊，需前往商店。 */
        UpdateRequired,

        /** 可用。權限是否已授予要另外用 [hasWritePermission] 查。 */
        Available,
    }

    /**
     * Lazy 建立。不支援的裝置上 [HealthConnectClient.getOrCreate] 會拋例外，
     * 因此包在 runCatching 裡回傳 null，絕不能讓 app 啟動時崩潰。
     */
    private val client: HealthConnectClient? by lazy {
        if (status() != Status.Available) null
        else runCatching { HealthConnectClient.getOrCreate(context) }
            .onFailure { Log.w(TAG, "HealthConnectClient 建立失敗", it) }
            .getOrNull()
    }

    /** 本功能只寫入步數，不讀取任何健康資料。 */
    val writePermissions: Set<String> =
        setOf(HealthPermission.getWritePermission(StepsRecord::class))

    fun status(): Status = runCatching {
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> Status.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> Status.UpdateRequired
            else -> Status.Unsupported
        }
    }.getOrElse {
        Log.w(TAG, "getSdkStatus 失敗，視為不支援", it)
        Status.Unsupported
    }

    /** 供寫入端取用；不可用時回 null，呼叫端須自行降級。 */
    fun clientOrNull(): HealthConnectClient? = client

    suspend fun hasWritePermission(): Boolean {
        val client = client ?: return false
        return runCatching {
            client.permissionController.getGrantedPermissions()
                .containsAll(writePermissions)
        }.getOrElse {
            Log.w(TAG, "查詢已授予權限失敗", it)
            false
        }
    }

    /**
     * 權限請求用的 contract。呼叫端搭配 `rememberLauncherForActivityResult` 使用，
     * 傳入 [writePermissions]，回傳已授予的權限集合。
     */
    fun requestPermissionsContract() =
        PermissionController.createRequestPermissionResultContract()

    /** 導向 Health Connect 的 Play 商店頁。 */
    fun openPlayStoreForProvider() = openPlayStore(HEALTH_CONNECT_PACKAGE)

    /**
     * 開啟 Health Connect 設定。
     *
     * Jetpack 提供的 action 會依 Android 版本選擇正確入口：Android 13 以下
     * 使用獨立 APK，Android 14+ 使用系統內建的 Health Connect 首頁。
     *
     * @return 是否成功開啟
     */
    fun openHealthConnectSettings(): Boolean {
        val intent = Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w(TAG, "無法開啟 Health Connect 設定", e)
            false
        }
    }

    /** 導向指定 package 的 Play 商店頁。失敗則靜默——這只是引導，不是關鍵路徑。 */
    private fun openPlayStore(packageName: String) {
        val targets = listOf(
            Uri.parse("market://details?id=$packageName"),
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
        for (target in targets) {
            val intent = Intent(Intent.ACTION_VIEW, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
                return
            } catch (e: ActivityNotFoundException) {
                Log.w(TAG, "無法開啟 $target", e)
            }
        }
    }

    private companion object {
        const val TAG = "HealthConnectAvail"
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
    }
}
