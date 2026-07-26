package com.pinecone.pinecone.ui.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pinecone.pinecone.ui.theme.*
import kotlinx.coroutines.delay

class GuardCountdownActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val lockReason = intent?.getStringExtra("lock_reason") ?: "DAILY_LIMIT_REACHED"
        val breakUsage = intent?.getIntExtra("break_usage_minutes", 0) ?: 0
        val breakDuration = intent?.getIntExtra("break_duration_minutes", 0) ?: 0

        setContent {
            PineconeTheme(darkTheme = true, dynamicColor = false) {
                var countdown by remember { mutableIntStateOf(10) }

                LaunchedEffect(Unit) {
                    while (countdown > 0) {
                        delay(1000L)
                        countdown--
                    }
                    // Launch lock screen and finish
                    startActivity(Intent(this@GuardCountdownActivity, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("lock_reason", lockReason)
                        putExtra("break_usage_minutes", breakUsage)
                        putExtra("break_duration_minutes", breakDuration)
                    })
                    finish()
                    overridePendingTransition(0, 0)
                }

                val progress = countdown / 10f
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, end = 32.dp)
                        .wrapContentWidth(Alignment.End)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PineSurface.copy(alpha = 0.75f))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("即将锁屏", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.width(260.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = PineWarning,
                        trackColor = PineSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("${countdown}s", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = PineWarning,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}
