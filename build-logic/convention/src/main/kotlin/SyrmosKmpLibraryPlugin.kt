import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class SyrmosKmpLibraryPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        @OptIn(ExperimentalWasmDsl::class)
        extensions.configure<KotlinMultiplatformExtension> {
            androidTarget {
                compilations.all {
                    compileTaskProvider.configure {
                        compilerOptions {
                            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                        }
                    }
                }
            }
            iosX64()
            iosArm64()
            iosSimulatorArm64()
            wasmJs { browser() }
        }

        extensions.configure<com.android.build.gradle.LibraryExtension> {
            namespace = "com.syrmos.${project.path.removePrefix(":").replace(":", ".")}"
            compileSdk = 35
            defaultConfig {
                minSdk = 26
            }
            compileOptions {
                sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
            }
        }
    }
}
