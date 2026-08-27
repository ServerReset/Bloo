pluginManagement {
    repositories {
        // Try Maven Central mirror first for Android/Google artifacts
        maven { url = uri("https://repo1.maven.org/maven2/") }
        mavenCentral()
        gradlePluginPortal()
        // Google repo - may be blocked by proxy
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
