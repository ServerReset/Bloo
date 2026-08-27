plugins {
    // Downgraded from 9.1.0 to 8.1.x for better availability on non-Google mirrors
    // AGP 9.0+ provides built-in Kotlin support, so the kotlin.android plugin
    // is no longer applied. Compose and serialization compiler plugins remain.
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.20" apply false
}
