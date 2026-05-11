package com.kotlinchat.routes

import com.kotlinchat.models.WsMessage
import com.kotlinchat.services.ChatService
import com.kotlinchat.services.ProcessService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json

fun Route.chatRoutes(chatService: ChatService, processService: ProcessService) {
    val json = Json { ignoreUnknownKeys = true }

    webSocket("/chat/{room}") {
        val username = call.request.queryParameters["username"]
            ?: return@webSocket close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Falta username"))
        val room = call.parameters["room"] ?: "general"

        chatService.onJoin(username, room, this)

        val history = chatService.getHistory(room)
        history.forEach { msg ->
            send(Frame.Text(json.encodeToString(WsMessage.serializer(), msg)))
        }

        try {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val wsMessage = json.decodeFromString<WsMessage>(frame.readText())
                    when (wsMessage.type) {
                        "chat" -> chatService.handleMessage(username, room, wsMessage.content)
                        "typing" -> chatService.handleTyping(username, room)
                    }
                }
            }
        } catch (e: Exception) {
            println("Error en WebSocket de $username: ${e.message}")
        } finally {
            chatService.onLeave(username, room, this)
        }
    }

    authenticate("auth-jwt") {
        route("/api/chat") {
            get("/rooms") {
                call.respond(mapOf("rooms" to chatService.getActiveRooms()))
            }
            get("/rooms/{room}/users") {
                val room = call.parameters["room"] ?: "general"
                call.respond(mapOf("room" to room, "users" to chatService.getUsersInRoom(room)))
            }
            get("/rooms/{room}/history") {
                val room = call.parameters["room"] ?: "general"
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                call.respond(mapOf("room" to room, "messages" to chatService.getHistory(room, limit)))
            }
        }

        route("/api/system") {
            get("/exec/{command}") {
                val command = call.parameters["command"] ?: ""
                call.respond(processService.executeCommand(command))
            }
            get("/stats") {
                call.respond(processService.getServerStats())
            }
        }
    }
}
