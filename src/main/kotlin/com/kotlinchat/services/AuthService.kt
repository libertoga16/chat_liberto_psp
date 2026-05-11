package com.kotlinchat.services

import com.kotlinchat.database.DatabaseFactory.dbQuery
import com.kotlinchat.database.Users
import com.kotlinchat.models.ChatUser
import com.kotlinchat.models.ProfileResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime
import java.util.*

class AuthService(private val environment: ApplicationEnvironment) {

    private val jwtSecret = environment.config.property("jwt.secret").getString()
    private val jwtIssuer = environment.config.property("jwt.issuer").getString()
    private val jwtAudience = environment.config.property("jwt.audience").getString()

    suspend fun register(username: String, password: String, email: String = "", address: String = ""): Result<ChatUser> {
        if (username.isBlank() || username.length < 3)
            return Result.failure(IllegalArgumentException("El usuario debe tener al menos 3 caracteres"))
        if (password.length < 6)
            return Result.failure(IllegalArgumentException("La contrasena debe tener al menos 6 caracteres"))

        val existing = dbQuery {
            Users.selectAll().where { Users.username eq username }.singleOrNull()
        }
        if (existing != null)
            return Result.failure(IllegalArgumentException("El usuario '$username' ya existe"))

        val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))

        val userId = dbQuery {
            Users.insert {
                it[Users.username] = username
                it[passwordHash] = hash
                it[Users.email] = email.ifBlank { null }
                it[Users.address] = address.ifBlank { null }
                it[createdAt] = LocalDateTime.now()
            } get Users.id
        }

        return Result.success(ChatUser(userId, username, hash, email, address))
    }

    suspend fun getProfile(userId: Int): ProfileResponse? {
        return dbQuery {
            Users.selectAll().where { Users.id eq userId }.singleOrNull()
        }?.let { row ->
            ProfileResponse(
                username = row[Users.username],
                email = row[Users.email] ?: "",
                address = row[Users.address] ?: ""
            )
        }
    }

    suspend fun updateProfile(userId: Int, email: String, address: String): Result<ProfileResponse> {
        return try {
            dbQuery {
                Users.update({ Users.id eq userId }) {
                    it[Users.email] = email.ifBlank { null }
                    it[Users.address] = address.ifBlank { null }
                }
            }
            val updated = getProfile(userId) ?: return Result.failure(Exception("Usuario no encontrado"))
            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<String> {
        val row = dbQuery {
            Users.selectAll().where { Users.username eq username }.singleOrNull()
        } ?: return Result.failure(IllegalArgumentException("Usuario o contrasena incorrectos"))

        val storedHash = row[Users.passwordHash]

        if (!BCrypt.checkpw(password, storedHash))
            return Result.failure(IllegalArgumentException("Usuario o contrasena incorrectos"))

        val token = generateToken(username, row[Users.id])
        return Result.success(token)
    }

    private fun generateToken(username: String, userId: Int): String {
        return JWT.create()
            .withAudience(jwtAudience)
            .withIssuer(jwtIssuer)
            .withClaim("username", username)
            .withClaim("userId", userId)
            .withExpiresAt(Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000))
            .sign(Algorithm.HMAC256(jwtSecret))
    }
}
