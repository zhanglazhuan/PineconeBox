package com.pinecone.pinecone.ui.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pinecone.pinecone.ui.theme.PineconeTheme

class PinSetupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isVerifyMode = intent?.getStringExtra("mode") == "verify"
        setContent {
            PineconeTheme {
                PinSetupScreen(isVerifyMode = isVerifyMode) { pin ->
                    if (isVerifyMode && pin != null) {
                        intent.putExtra("pin", pin)
                        setResult(RESULT_OK, intent)
                    }
                    finish()
                }
            }
        }
    }
}
