package com.pantrypal.chatbot.data

import java.util.UUID

enum class Role { USER, ASSISTANT }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String,
    // Set on a USER message when sending it failed — the caller couldn't
    // reach the backend or got an error back. Drives the "Try again" caption.
    val failed: Boolean = false,
)
