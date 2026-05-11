package com.kotlinchat.models

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class RegisterRequest(
    val username: String,
    val password: String,
    val email: String = "",
    val address: String = ""
)

@Serializable
data class ProfileResponse(
    val username: String,
    val email: String,
    val address: String
)

@Serializable
data class UpdateProfileRequest(
    val email: String = "",
    val address: String = ""
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String,
    val username: String,
    val message: String
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class WsMessage(
    val type: String,
    val sender: String = "",
    val content: String = "",
    val room: String = "general",
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatUser(
    val id: Int,
    val username: String,
    val passwordHash: String,
    val email: String = "",
    val address: String = "",
    val createdAt: LocalDateTime = LocalDateTime.now()
)

data class ChatMessage(
    val id: Int,
    val senderId: Int,
    val senderName: String,
    val room: String,
    val content: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)
