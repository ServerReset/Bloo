package com.bloo.bluelink.wear

/** The sideload build installs the watch app itself, over ADB. */
internal fun newWatchInstaller(): WatchInstall? = runCatching { WatchAdbInstaller() }.getOrNull()
