package com.pantrypal.chatbot

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.pantrypal.chatbot.ui.ChatScreen
import com.pantrypal.chatbot.ui.theme.PantryPalTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PantryPalTheme {
                ChatScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
