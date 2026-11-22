pluginManagement {
    repositories {
        // Google first: the Android Gradle plugin and AndroidX live there.
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
        // The ADB client used to sideload the watch app, and the liquid-glass widget.
        maven {
            url = uri("https://jitpack.io")
            content {
                includeGroupByRegex("com\\.github\\.MuntashirAkon.*")
            }
        }
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
