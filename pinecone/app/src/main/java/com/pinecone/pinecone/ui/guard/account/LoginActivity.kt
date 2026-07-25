package com.pinecone.pinecone.ui.guard.account

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pinecone.pinecone.ui.guard.GuardSettingsScaffold
import com.pinecone.pinecone.ui.theme.PineconeTheme

class LoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                GuardSettingsScaffold(title = "家长登录", onBack = { finish() }) {
                    LoginScreen(
                        onLoginSuccess = {
                            setResult(RESULT_OK)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
