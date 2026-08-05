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
            implementation(projects.core.common)
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.koin)
        }
        androidMain.dependencies {
            implementation(libs.osmdroid.android)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
