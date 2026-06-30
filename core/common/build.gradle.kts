plugins {
    id("syrmos.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // api, not implementation: LiveDataFreshness exposes kotlinx.datetime
            // Instant in its public surface, so every consumer of core/common
            // (network, data, feature modules) needs it resolvable on classpath.
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
