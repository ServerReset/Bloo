package com.bloo.bluelink

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

/** The app starts on a clean install and stays up: the cheapest check that nothing crashes at launch. */
class SmokeTest {
    @Test
    fun launchesWithoutCrashing() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // startActivitySync returns once the activity has launched, or fails if it can't.
        val activity = instrumentation.startActivitySync(intent)
        assertNotNull(activity)
        // Let the first screen compose and settle; a crash on the way would have killed the process.
        Thread.sleep(5_000)
        assertFalse("MainActivity finished itself during launch", activity.isFinishing)
        instrumentation.runOnMainSync { activity.finish() }
    }
}
