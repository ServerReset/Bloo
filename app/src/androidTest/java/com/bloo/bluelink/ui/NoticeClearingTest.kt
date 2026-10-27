package com.bloo.bluelink.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bloo.bluelink.autolock.AutoLockNotification
import com.bloo.bluelink.autolock.DetectionState
import com.bloo.bluelink.data.NOTICE_CLEAR_AFTER_MS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** A notice that reports something that happened clears itself after half an hour; one that is still going on does not. */
@RunWith(AndroidJUnit4::class)
class NoticeClearingTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun theNoticeIsClearedAfterThirtyMinutes() {
        assertEquals(30 * 60 * 1000L, NOTICE_CLEAR_AFTER_MS)
    }

    @Test fun theEndOfAnAutoLockEvaluationClearsItself() {
        for (state in listOf(DetectionState.LOCKED, DetectionState.SKIPPED, DetectionState.ABORTED, DetectionState.ERROR)) {
            val n = AutoLockNotification.build(context, "VIN1", "Car", state, 0)
            assertEquals("$state clears after the window", NOTICE_CLEAR_AFTER_MS, n.timeoutAfter)
        }
    }

    @Test fun anAutoLockEvaluationInProgressDoesNotClear() {
        for (state in listOf(DetectionState.CONFIRMING, DetectionState.GRACE, DetectionState.VERIFYING, DetectionState.LOCKING)) {
            val n = AutoLockNotification.build(context, "VIN1", "Car", state, 5)
            assertEquals("$state stays while it is going on", 0L, n.timeoutAfter)
        }
    }
}
