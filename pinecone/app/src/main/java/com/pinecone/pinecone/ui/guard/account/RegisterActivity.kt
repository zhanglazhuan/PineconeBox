package com.pinecone.pinecone.ui.guard.account

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pinecone.pinecone.ui.theme.PineconeTheme

class RegisterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                RegisterScreen(
                    onComplete = { success ->
                        setResult(if (success) RESULT_OK else RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}
