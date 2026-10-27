package com.yadavard.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Yadimo's wardrobe as a screen of its own. A full-screen dialog did not always fit the window on some phones,
 * which pushed the buy bar off the bottom of the screen.
 */
class YadimoWardrobeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppDisplay.load(this)
        setContent {
            YadarTheme {
                CompositionLocalProvider(LocalLayoutDirection provides
                    if (AppDisplay.language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(Modifier.fillMaxSize().statusBarsPadding()) {
                            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { finish() }) { Icon(Icons.Rounded.Close, t("بستن", "Close")) }
                                Text(t("کمد یادیمو", "Yadimo's wardrobe"), style = MaterialTheme.typography.titleLarge)
                            }
                            YadimoCustomizer(Modifier.weight(1f), onLeave = { finish() }, onTopUp = {
                                // Token packs live on the account card in Settings.
                                startActivity(Intent(this@YadimoWardrobeActivity, MainActivity::class.java).setAction(WidgetCommands.BUY)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                                finish()
                            })
                        }
                    }
                }
            }
        }
    }
}
