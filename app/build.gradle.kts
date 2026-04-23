import java.util.Properties

@Suppress("DSL_SCOPE_VIOLATION")
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val versionMajor = 1
val versionMinor = 0
val versionPatch = 13

android {
    namespace = "com.moooo_works.letsgogps"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.moooo_works.letsgogps"
        minSdk = 26
        targetSdk = 35
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
            isDebuggable = true
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/6300978111\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/1033173712\"")
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-8495982996587452/2988295108\"")
            buildConfigField("String", "INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-8495982996587452/7493536385\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    lint {
        disable += "WrongConstant"
        disable += "UnusedResources"
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

    // Gson
    implementation(libs.gson)
    implementation(libs.openlocationcode)

    // Maps
    implementation(libs.google.maps.compose)
    implementation(libs.play.services.maps)

    // AdMob
    implementation(libs.admob)

    // Billing
    implementation(libs.billing)
    implementation(libs.play.review)

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
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}
