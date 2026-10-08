package com.moooo_works.letsgogps

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.moooo_works.letsgogps.data.engine.AndroidLocationMockEngine
import com.moooo_works.letsgogps.data.healthcheck.SystemHealthCheckImpl
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckItem
import com.moooo_works.letsgogps.domain.healthcheck.ItemStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class Android17DeveloperSettingsTest {
    @Test fun hiddenDeveloperSettingDoesNotOverrideActualMockPermission() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val global=Settings.Global.getInt(context.contentResolver,Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,0)
        val ops=context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode=ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,Process.myUid(),context.packageName)
        Log.i("DeveloperStateRegression","sdk=${Build.VERSION.SDK_INT}, global=$global, mockMode=$mode")
        assertEquals(ItemStatus.NotApplicable,SystemHealthCheckImpl(context).refresh().statusOf(HealthCheckItem.DeveloperMode))
        val expected=if(mode==AppOpsManager.MODE_ALLOWED) MockPermissionStatus.Allowed else MockPermissionStatus.NotAllowed
        assertEquals(expected,AndroidLocationMockEngine(context).getMockPermissionStatus())
    }
}
