// WeatherApi / Weather / WeatherCode moved to the :shared module
// (shared/src/main/java/com/bloo/bluelink/data/WeatherApi.kt) so the Wear app can
// fetch weather directly when running standalone. Same package, so every existing
// com.bloo.bluelink.data.WeatherApi / Weather / WeatherCode reference in :app
// resolves to the shared copy unchanged — no import edits were needed here.
//
// This file is intentionally declaration-free to avoid a duplicate-class clash
// with the shared definitions. It can be deleted outright once the tree is next
// touched from an environment with shell access.
package com.bloo.bluelink.data
