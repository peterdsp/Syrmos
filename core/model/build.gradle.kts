plugins {
    id("syrmos.kmp.library")
    id("syrmos.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
    }
}
