plugins {
    id("syrmos.kmp.library")
    id("syrmos.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(compose.runtime)
            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tab.navigator)
        }
    }
}
