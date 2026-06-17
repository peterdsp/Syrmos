plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            implementation(libs.androidx.activity.compose)
            implementation(libs.koin.android)
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
        versionCode = 23
        versionName = "1.0.3"
    }
    signingConfigs {
        create("release") {
            storeFile = file("syrmos-release.keystore")
            storePassword = "REDACTED"
            keyAlias = "syrmos"
            keyPassword = "REDACTED"
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
