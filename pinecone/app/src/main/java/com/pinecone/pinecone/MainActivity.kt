package com.pinecone.pinecone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pinecone.guard.service.GuardClientHolder
import com.pinecone.pinecone.ui.screen.MainScreen
import com.pinecone.pinecone.ui.theme.PineconeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        GuardClientHolder.initialize(this)

        enableEdgeToEdge()
        setContent {
            PineconeTheme(darkTheme = true, dynamicColor = false) {
                MainScreen()
            }
        }
    }
}
