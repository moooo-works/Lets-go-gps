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

    /**
     * Google Fit 是否已安裝。
     *
     * Fit 是步數同步鏈路的必要中繼：本 app 寫入 Health Connect，Fit 讀取後
     * 存進 Google 帳戶，目標 app 再從 Fit 取用。少了 Fit，鏈路只到 Health Connect
     * 就斷了。
     */
    fun isGoogleFitInstalled(): Boolean = googleFitLaunchIntent() != null

    /**
     * Google Fit 的啟動 Intent；未安裝或被停用時回 null。
     *
     * 用 getLaunchIntentForPackage 而非 getPackageInfo：前者在 package 存在但被
     * 停用時就回 null，那種狀態下使用者一樣打不開 Fit，視為不可用才合理。
     * 需要 manifest 的 <queries> 宣告，否則 Android 11+ 一律回 null。
     */
    fun googleFitLaunchIntent(): Intent? =
        context.packageManager.getLaunchIntentForPackage(GOOGLE_FIT_PACKAGE)

    /** 導向 Health Connect 的 Play 商店頁。 */
    fun openPlayStoreForProvider() = openPlayStore(HEALTH_CONNECT_PACKAGE)

    /** 導向 Google Fit 的 Play 商店頁。 */
    fun openPlayStoreForGoogleFit() = openPlayStore(GOOGLE_FIT_PACKAGE)

    /**
     * 開啟 Google Fit，讓使用者在其中完成「與健康資料同步平台保持同步」。
     *
     * 那個授權流程只能在 Fit 內部發起，沒有外部 intent 可以直接跳進去，
     * 所以只能把使用者送到 Fit 首頁並在說明中指路。
     *
     * @return 是否成功開啟
     */
    fun openGoogleFit(): Boolean {
        val launch = googleFitLaunchIntent() ?: return false
        return try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: Exception) {
            Log.w(TAG, "無法開啟 Google Fit", e)
            false
        }
    }

    /**
     * 開啟 Health Connect 設定。
     *
     * 依序嘗試多個 action——Health Connect 在 Android 13 以下是獨立 APK、
     * 14+ 內建於系統，入口不同；且 controller 沒有 launcher activity。
     *
     * @return 是否成功開啟
     */
    fun openHealthConnectSettings(): Boolean {
        val candidates = listOf(
            Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS"),
            Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"),
        )
        for (intent in candidates) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) {
                Log.w(TAG, "無法以 ${intent.action} 開啟 Health Connect", e)
            }
        }
        return false
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
        const val GOOGLE_FIT_PACKAGE = "com.google.android.apps.fitness"
    }
}
