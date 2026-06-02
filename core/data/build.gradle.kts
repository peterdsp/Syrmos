plugins {
    id("syrmos.kmp.library")
    id("syrmos.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
        }
    }
}
