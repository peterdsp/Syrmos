plugins {
    id("syrmos.kmp.library")
    // The station-complex registry and the per-line stop order are read from the
    // bundled seed JSON, so this module needs the serialization runtime and its
    // compiler plugin the same way core:data does.
    id("syrmos.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.network)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}
