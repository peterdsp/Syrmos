rootProject.name = "Syrmos"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:data")
include(":core:domain")
include(":core:network")
include(":core:designsystem")
include(":core:navigation")
include(":core:testing")

include(":feature:home")
include(":feature:lines")
include(":feature:stations")
include(":feature:schedule")
include(":feature:map")
include(":feature:settings")

include(":composeApp")
include(":androidApp")
