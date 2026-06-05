import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.gradle.api.tasks.Sync

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidLibrary)
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
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "composeApp"
            isStatic = true
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.domain)
            implementation(projects.core.database)
            implementation(projects.core.network)
            implementation(projects.core.designsystem)
            implementation(projects.core.navigation)

            implementation(projects.feature.home)
            implementation(projects.feature.lines)
            implementation(projects.feature.stations)
            implementation(projects.feature.schedule)
            implementation(projects.feature.map)
            implementation(projects.feature.settings)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.voyager.navigator)
            implementation(libs.voyager.tab.navigator)
            implementation(libs.voyager.transitions)
            implementation(libs.voyager.koin)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.koin.android)
        }
    }
}

android {
    namespace = "com.syrmos.app"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register<Sync>("stageWebRelease") {
    dependsOn("wasmJsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    from(project(":core:data").file("src/commonMain/composeResources/files")) {
        into("files")
    }
    into(layout.buildDirectory.dir("web-release"))
}

tasks.register<Copy>("copySeedForDev") {
    from(project(":core:data").file("src/commonMain/composeResources/files")) {
        into("files")
    }
    from(file("src/wasmJsMain/resources/icons")) {
        into("icons")
    }
    into(layout.buildDirectory.dir("processedResources/wasmJs/main"))
    mustRunAfter("wasmJsProcessResources")
}

tasks.matching { it.name == "wasmJsDevelopmentExecutableCompileSync" }.configureEach {
    dependsOn("copySeedForDev")
}
