plugins {
    id("syrmos.kmp.library")
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.common)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
        val wasmJsMain by getting {
            dependencies {
                // WebWorkerDriver requires org.w3c.dom.Worker which is unavailable
                // in Kotlin/WASM. Using stub driver until SQLDelight adds wasmJs support.
            }
        }
    }
}

sqldelight {
    databases {
        create("SyrmosDatabase") {
            packageName.set("com.syrmos.core.database")
        }
    }
}
