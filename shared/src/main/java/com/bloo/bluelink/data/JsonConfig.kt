package com.bloo.bluelink.data

import kotlinx.serialization.json.Json

/**
 * The app's default JSON: unknown keys are ignored, so an old payload (or a newer one carrying
 * fields this build does not model) still decodes instead of throwing. Use this unless a file needs
 * one of the two variants below.
 */
val BlooJson: Json = Json { ignoreUnknownKeys = true }

/**
 * For third-party or vendor bodies that may carry loose JSON (weather, update responses): like
 * [BlooJson] but lenient about the syntax it accepts.
 */
val BlooLenientJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * For settings and backup files written to disk, which people may open and diff: like [BlooJson] but
 * pretty-printed.
 */
val BlooBackupJson: Json = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
}
