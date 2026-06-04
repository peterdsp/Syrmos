plugins {
    id("syrmos.feature")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.koin)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.network)
        }
        androidMain.dependencies {
            implementation(libs.osmdroid.android)
        }
    }
}
