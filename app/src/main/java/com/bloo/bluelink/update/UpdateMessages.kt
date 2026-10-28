package com.bloo.bluelink.update

/**
 * The words the update flow shows when a download or an install ends. The app's own update surfaces and the crash screen's recovery panel
 * both say these, so they are written once here.
 */
object UpdateMessages {
    const val DOWNLOAD_FAILED = "Download failed. Check your connection and try again."
    const val INSTALLED_REOPEN = "Update installed. Reopen Bloo to finish."
}
