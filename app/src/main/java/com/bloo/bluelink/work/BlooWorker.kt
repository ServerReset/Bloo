package com.bloo.bluelink.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bloo.bluelink.data.BootGate

/**
 * The base of every background worker: sealed after a phone restart until the user signs in (see [BootGate]), when nothing runs and
 * nothing posts, so each worker's [run] begins with the car's data available.
 */
abstract class BlooWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    final override suspend fun doWork(): Result =
        if (BootGate.isLocked(applicationContext)) Result.success() else run()

    protected abstract suspend fun run(): Result
}
