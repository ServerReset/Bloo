package com.bloo.bluelink.data

import android.content.Context
import android.content.SharedPreferences

/**
 * One of the app's private SharedPreferences files, read through the application context so a short-lived component never holds an
 * Activity or Service. Every feature's prefs file opens through here rather than repeating the getSharedPreferences call.
 */
fun Context.privatePrefs(name: String): SharedPreferences =
    applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
