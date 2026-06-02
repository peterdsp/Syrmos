plugins {
    `kotlin-dsl`
}

dependencies {
    compileOnly(libs.plugins.kotlinMultiplatform.toDep())
    compileOnly(libs.plugins.composeMultiplatform.toDep())
    compileOnly(libs.plugins.composeCompiler.toDep())
    compileOnly(libs.plugins.androidLibrary.toDep())
    compileOnly(libs.plugins.kotlinSerialization.toDep())
}

fun Provider<PluginDependency>.toDep() = map {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
}

gradlePlugin {
    plugins {
        register("syrmosKmpLibrary") {
            id = "syrmos.kmp.library"
            implementationClass = "SyrmosKmpLibraryPlugin"
        }
        register("syrmosCompose") {
            id = "syrmos.compose"
            implementationClass = "SyrmosComposePlugin"
        }
        register("syrmosFeature") {
            id = "syrmos.feature"
            implementationClass = "SyrmosFeaturePlugin"
        }
        register("syrmosSerialization") {
            id = "syrmos.serialization"
            implementationClass = "SyrmosSerializationPlugin"
        }
    }
}
