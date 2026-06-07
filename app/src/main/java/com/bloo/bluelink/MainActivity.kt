package com.bloo.bluelink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.bloo.bluelink.ui.AppViewModel
import com.bloo.bluelink.ui.BlooApp
import com.bloo.bluelink.ui.BlooTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlooTheme {
                BlooApp(viewModel)
            }
        }
    }
}
