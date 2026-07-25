package com.pinecone.pinecone.ui.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.pinecone.pinecone.ui.theme.PineconeTheme

class ParentSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PineconeTheme {
                ParentSettingsScreen(onBack = { finish() })
            }
        }
    }
}
