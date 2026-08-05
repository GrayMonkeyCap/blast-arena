pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LifeLedger"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:datastore")
include(":core:security")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")

include(":sms")
include(":data")
include(":domain")

include(":feature:home")
include(":feature:transactions")
include(":feature:money")
include(":feature:analytics")
include(":feature:search")
include(":feature:settings")
