package com.kotlinchat

import com.kotlinchat.database.DatabaseFactory
import com.kotlinchat.plugins.*
import com.kotlinchat.routes.authRoutes
import com.kotlinchat.routes.chatRoutes
import com.kotlinchat.services.AuthService
import com.kotlinchat.services.ChatService
import com.kotlinchat.services.ProcessService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    DatabaseFactory.init()
    configureSerialization()
    configureSecurity()
    configureSockets()

    val authService = AuthService(environment)
    val chatService = ChatService()
    val processService = ProcessService()

    routing {
        get("/") {
            call.respondText("KotlinChat Server v1.0", ContentType.Text.Plain)
        }
        authRoutes(authService)
        chatRoutes(chatService, processService)
    }

    println("KotlinChat Server iniciado en http://localhost:8080")
}
