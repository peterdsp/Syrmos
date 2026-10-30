import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(projects.composeApp)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(libs.androidx.activity.compose)
            // Compose UI for the widget configuration Activity (Edit widget).
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.koin.android)
            implementation(libs.androidx.glance.appwidget)
            implementation(libs.androidx.glance.material3)
            implementation(libs.androidx.work.runtime)
            // 3.0.2: broadcasts by push (FCM, manual FirebaseOptions) and the
            // nearby next-train geofences.
            implementation(libs.firebase.messaging)
            implementation(libs.play.services.location)
        }

        // Keep Android-only parity tests in the Android target while reusing
        // the same KMP contracts exercised by iOS and the other targets.
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.datetime)
        }
    }
}

dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.8.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.8.0")
}

android {
    namespace = "com.syrmos.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.syrmos.android"
        minSdk = 26
        targetSdk = 36
        // 105 (rejected) and 106 (released 2026-07-16) are consumed and can
        // never be reused. Bump this before every release; Play refuses a code
        // it has already seen, even from a discarded upload. 233 shipped as
        // v3.0.0-beta.28, 234 as beta.29, 235 as beta.30, 236 as beta.31, 237 as beta.32, 238 as beta.33, 239 as beta.34, 240 as beta.35, 241 as beta.36, 242 as beta.37, 243 as beta.38, 244 as beta.39, 245 as beta.40, 246 as beta.41, 247 as beta.42, 248 as beta.43, 249 as beta.44, 250 as beta.45, 251 as beta.46, 252 as beta.47, 253 as beta.48, 254 as beta.49, 255 as beta.50, 256 as beta.51, 257 as beta.52, 258 as beta.53, 259 as beta.54, 260 as beta.55, 261 as beta.56, 262 as beta.57, 263 as beta.58, 264 as beta.59, 265 as beta.60, 266 as the 3.0.0 release; 267 is the 3.0.0 build 2 (weather + Android parity); 268 broken weather fetch (unused); 269 is 3.0.1 weather fix; 270 adds storm lightning + Ichnos scope hardening; 271 is 3.0.2 (closures, broadcasts, nearby and calendar notifications); 272 is the 3.0.2 release build; 273 adds Greek station names on the Home board, the startup notification permission request, the offline Athens network map, tappable map stations (the pin bitmap was drawn in raw pixels, so it was ~8dp on a dense screen), trains removed from the map on a suspended line (strike), the Ariadne model opt-in moved to Settings with an honest engine readout, a working Open-app-settings route out of a permanent permission denial, the non-Athens departures fallback, and the Hellenic Train strike surfacing.
        versionCode = 277
        versionName = "3.0.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Firebase Cloud Messaging without google-services.json: the values come
        // from local.properties or the environment (CI secrets). When any is
        // empty, push is disabled at runtime and the app still builds and runs,
        // falling back to polling /api/broadcasts.
        fun fcm(key: String): String =
            (localProps.getProperty(key) ?: System.getenv(key) ?: "").replace("\"", "")
        // Off until Google approves the background-location declaration in Play Console.
        buildConfigField("boolean", "NEARBY_NOTIFICATIONS", "false")
        buildConfigField("String", "FCM_PROJECT_ID", "\"${fcm("FCM_PROJECT_ID")}\"")
        buildConfigField("String", "FCM_APP_ID", "\"${fcm("FCM_APP_ID")}\"")
        buildConfigField("String", "FCM_API_KEY", "\"${fcm("FCM_API_KEY")}\"")
        buildConfigField("String", "FCM_SENDER_ID", "\"${fcm("FCM_SENDER_ID")}\"")
    }
    buildFeatures {
        buildConfig = true
    }
    signingConfigs {
        create("release") {
            storeFile = file(localProps.getProperty("RELEASE_STORE_FILE", "syrmos-release.keystore"))
            storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
            keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "syrmos")
            keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        // Detectors that crash with IncompatibleClassChangeError under the
        // current Kotlin/AGP combo. Re-enable once the AGP fix ships.
        disable += setOf(
            "NullSafeMutableLiveData",
            "RememberInComposition",
            "FrequentlyChangingValue",
            "AutoboxingStateCreation",
        )
    }
    bundle {
        language { enableSplit = false }
    }
}
