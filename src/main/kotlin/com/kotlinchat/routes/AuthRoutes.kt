package com.kotlinchat.routes

import com.kotlinchat.models.*
import com.kotlinchat.services.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/api") {
        post("/register") {
            val request = call.receive<RegisterRequest>()
            authService.register(request.username, request.password)
                .onSuccess { user ->
                    call.respond(HttpStatusCode.Created, AuthResponse(
                        token = "",
                        username = user.username,
                        message = "Usuario registrado correctamente"
                    ))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(error.message ?: "Error"))
                }
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            authService.login(request.username, request.password)
                .onSuccess { token ->
                    call.respond(HttpStatusCode.OK, AuthResponse(
                        token = token,
                        username = request.username,
                        message = "Login correcto"
                    ))
                }
                .onFailure { error ->
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse(error.message ?: "Error"))
                }
        }
    }
}
