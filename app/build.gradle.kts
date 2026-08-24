import java.util.Properties
import com.android.build.api.variant.BuildConfigField

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.firebaseCrashlytics)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val versionMajor = 1
val versionMinor = 3
val versionPatch = 4

android {
    namespace = "com.moooo_works.letsgogps"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.moooo_works.letsgogps"
        minSdk = 26
        targetSdk = 36
        versionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
        versionName = "$versionMajor.$versionMinor.$versionPatch"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // MAPS_API_KEY: local.properties (local dev) or MAPS_API_KEY env var (CI)
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY")
            ?: System.getenv("MAPS_API_KEY")
            ?: error("MAPS_API_KEY not found. Set it in local.properties or as an environment variable.")
    }

    signingConfigs {
        create("release") {
            val keystorePath = localProperties.getProperty("KEYSTORE_PATH")
                ?: System.getenv("KEYSTORE_PATH")
            val keystorePassword = localProperties.getProperty("KEYSTORE_PASSWORD")
                ?: System.getenv("KEYSTORE_PASSWORD")
            val keyAlias = localProperties.getProperty("KEY_ALIAS")
                ?: System.getenv("KEY_ALIAS")
            val keyPassword = localProperties.getProperty("KEY_PASSWORD")
                ?: System.getenv("KEY_PASSWORD")

            if (keystorePath != null && keystorePassword != null && keyAlias != null && keyPassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        debug {
            // 注意：不要加 applicationIdSuffix。google-services.json 只註冊了
            // com.moooo_works.letsgogps，加了 suffix 會讓 Google Services plugin
            // 以 "No matching client found" 直接 fail build。
            isDebuggable = true
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        disable += "WrongConstant"
        disable += "UnusedResources"
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests {
            // 注意：目前刻意 *不* 開 isIncludeAndroidResources。
            // 開啟後 Robolectric 才找得到合併 manifest，但也會載入真正的
            // @HiltAndroidApp，導致 Service 測試的手動 mock 被 Hilt 覆寫。
            // 詳見 .trellis/tasks/07-28-robolectric-tests-silently-skipped。
            all {
                it.jvmArgs("-XX:+EnableDynamicAgentLoading")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants { variant ->
        val fields = if (variant.buildType == "debug") {
            mapOf(
                "DEV_FORCE_PRO" to BuildConfigField("Boolean", "true", "Debug Pro override"),
                "ADMOB_APP_ID" to BuildConfigField("String", "\"ca-app-pub-3940256099942544~3347511713\"", "Google sample App ID"),
                "BANNER_AD_UNIT_ID" to BuildConfigField("String", "\"ca-app-pub-3940256099942544/6300978111\"", "Google sample Banner unit"),
                "REWARDED_AD_UNIT_ID" to BuildConfigField("String", "\"ca-app-pub-3940256099942544/5224354917\"", "Google sample Rewarded unit")
            )
        } else {
            mapOf(
                "DEV_FORCE_PRO" to BuildConfigField("Boolean", "false", "Release Pro behavior"),
                "ADMOB_APP_ID" to BuildConfigField("String", "\"ca-app-pub-7328056144057376~2219581212\"", "Production App ID"),
                "BANNER_AD_UNIT_ID" to BuildConfigField("String", "\"ca-app-pub-7328056144057376/1824598031\"", "Production Banner unit"),
                "REWARDED_AD_UNIT_ID" to BuildConfigField("String", "\"ca-app-pub-7328056144057376/6473078035\"", "Production Rewarded unit")
            )
        }
        variant.buildConfigFields?.putAll(fields)
    }
}

// AAB 輸出重新命名
tasks.whenTaskAdded {
    if (name.matches(Regex("bundle(Release|Debug)"))) {
        val buildType = name.removePrefix("bundle").lowercase()
        doLast {
            val bundleDir = layout.buildDirectory.dir("outputs/bundle/$buildType").get().asFile
            bundleDir.listFiles()
                ?.filter { it.extension == "aab" && !it.nameWithoutExtension.startsWith("letsgo-") }
                ?.forEach { file ->
                    file.renameTo(File(bundleDir, "letsgo-$versionMajor.$versionMinor.$versionPatch-$buildType.aab"))
                }
        }
    }
}

dependencies {
    testImplementation(libs.robolectric)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.process)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.appcompat)

    // Compose
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3)
    implementation(libs.material.icons.extended)
    implementation(libs.reorderable)

    // Firebase Crashlytics (crash reporting; works without GMS via its own upload)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)

    // Gson
    implementation(libs.gson)
    implementation(libs.openlocationcode)

    // Maps
    implementation(libs.google.maps.compose)

    // AdMob
    implementation(libs.admob.next.gen)

    // Billing
    implementation(libs.billing)
    implementation(libs.play.review)

    // Health Connect — 步數寫入（不得改用 play-services-fitness，Fit API 已停止服務）
    implementation(libs.health.connect)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.datastore.preferences)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.coroutines.android)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
