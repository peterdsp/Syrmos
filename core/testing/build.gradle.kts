plugins {
    id("syrmos.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.domain)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
            implementation(libs.turbine)
        }
    }
}
