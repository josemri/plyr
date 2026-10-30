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
    plugins {
        id("org.jetbrains.compose.hot-reload") version "1.2.0" apply false
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.compose.hot-reload") {
                useVersion("1.2.0")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            // Mismo filtro que en pluginManagement: solo buscar aquí los
            // grupos de Google. El resto (asm de lint, kover-agent, etc.)
            // van directo a mavenCentral y no dependen de dl.google.com.
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "plyr"
include(":app")
include(":core")
include(":desktop")
 