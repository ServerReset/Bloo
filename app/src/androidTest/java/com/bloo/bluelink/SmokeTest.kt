package com.bloo.bluelink

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertEquals
import org.junit.Test

/** The app starts on a clean install and stays up: the cheapest check that nothing crashes at launch. */
class SmokeTest {
    @Test
    fun launchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(4_000)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
