package com.rutg3r.rawmover

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.rutg3r.rawmover.ui.RawMoverTheme
import com.rutg3r.rawmover.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RawMoverTheme {
                SettingsScreen()
            }
        }
    }
}
