package com.streamflow

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.streamflow.data.local.AppPreferences
import com.streamflow.ui.navigation.NavGraph
import com.streamflow.ui.theme.StreamFlowTheme
import com.streamflow.ui.theme.toAppTheme

class MainActivity : ComponentActivity() {

    var isInPip by mutableStateOf(false)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = AppPreferences.get(this)

        val sharedUrl = intent?.takeIf { it.action == Intent.ACTION_SEND }
            ?.getStringExtra(Intent.EXTRA_TEXT)
            ?.let { text ->
                Regex("https?://(?:www\\.)?(?:youtube\\.com|youtu\\.be)\\S+").find(text)?.value
            }

        enableEdgeToEdge()
        setContent {
            val themeStr by prefs.theme.collectAsState(initial = "DARK")
            StreamFlowTheme(theme = themeStr.toAppTheme()) {
                NavGraph(startUrl = sharedUrl)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
    }
}
