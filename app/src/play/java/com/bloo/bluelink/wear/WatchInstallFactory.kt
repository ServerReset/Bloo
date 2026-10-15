package com.bloo.bluelink.wear

/**
 * The Play build cannot: it carries neither the ADB library nor the permissions it needs (see
 * [WatchInstall]). It does not need them either -- Google Play installs the watch app on a paired
 * watch by itself -- so the phone-side installer simply does not exist here, and the UI shows the Play
 * path instead of steps it could not perform.
 */
internal fun newWatchInstaller(): WatchInstall? = null
