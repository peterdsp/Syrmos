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
        }
    }
}

android {
    namespace = "com.syrmos.android"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.syrmos.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 104
        versionName = "1.2.1"
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
