package com.bloo.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.bloo.wear.ui.BlooWearTheme
import com.bloo.wear.ui.WatchApp

class MainActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BlooWearTheme {
                WatchApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check phone reachability whenever the watch face returns to Bloo.
        viewModel.refreshConnection()
    }
}
