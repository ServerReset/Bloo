pluginManagement {
    repositories {
        // Try Gradle plugin portal first (often caches Android plugins)
        gradlePluginPortal()
        // Maven Central usually has AGP cached
        mavenCentral()
        // Aliyun mirror often has cached artifacts
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        // JCenter often has cached Android artifacts
        maven { url = uri("https://jcenter.bintray.com/") }
        // Sonatype snapshots
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        // Google as last resort
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
