package com.bloo.bluelink.ui

/** This per-car map with [vin]'s entry set to [value]. Every per-car map on [UiState] is updated this way. */
internal fun <V> Map<String, V>.withCar(vin: String, value: V): Map<String, V> = this + (vin to value)
