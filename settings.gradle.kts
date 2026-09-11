pluginManagement {
    repositories {
        // Try Gradle plugin portal first (often caches Android plugins)
        gradlePluginPortal()
        // Maven Central usually has AGP cached
        mavenCentral()
        // Google as fallback
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Bloo"
include(":app")
include(":shared")
include(":uicommon")
include(":wear")
// compileOnly framework-API stubs so :app can compile against hidden PackageInstaller
// AIDL interfaces for the Shizuku silent-install path (never shipped in the APK).
include(":hidden-api-stub")
