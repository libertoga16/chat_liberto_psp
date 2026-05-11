package com.kotlinchat.services

import com.kotlinchat.database.DatabaseFactory.dbQuery
import com.kotlinchat.database.Messages
import com.kotlinchat.models.WsMessage
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SortOrder
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

class ChatService {

    // Mapa de conexiones: room -> {username -> session}
    private val connections = ConcurrentHashMap<String, MutableMap<String, WebSocketSession>>()

    // Mutex para proteger escrituras en el mapa interno (no es thread-safe)
    private val connectionsMutex = Mutex()

    // SharedFlow para difusion de mensajes (patron pub/sub)
    private val _messageFlow = MutableSharedFlow<WsMessage>(
        replay = 0,
        extraBufferCapacity = 100
    )
    val messageFlow = _messageFlow.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun onJoin(username: String, room: String, session: WebSocketSession) {
        connectionsMutex.withLock {
            val roomConnections = connections.getOrPut(room) { mutableMapOf() }
            roomConnections[username] = session
        }

        val joinMessage = WsMessage(
            type = "join",
            sender = "sistema",
            content = "$username se ha unido a la sala",
            room = room
        )
        broadcast(joinMessage, room)

        println("$username conectado a sala '$room' | Usuarios: ${getUsersInRoom(room).size}")
    }

    suspend fun onLeave(username: String, room: String, session: WebSocketSession) {
        val removed = connectionsMutex.withLock {
            val current = connections[room]?.get(username)
            if (current === session) {
                connections[room]?.remove(username)
                if (connections[room]?.isEmpty() == true) connections.remove(room)
                true
            } else false
        }

        if (removed) {
            val leaveMessage = WsMessage(
                type = "leave",
                sender = "sistema",
                content = "$username ha salido de la sala",
                room = room
            )
            broadcast(leaveMessage, room)
            println("$username desconectado de sala '$room'")
        }
    }

    suspend fun broadcast(message: WsMessage, room: String) {
        val messageJson = json.encodeToString(message)

        _messageFlow.emit(message)

        val roomSessions = connectionsMutex.withLock {
            connections[room]?.toMap() ?: emptyMap()
        }

        roomSessions.forEach { (username, session) ->
            try {
                session.send(Frame.Text(messageJson))
            } catch (e: Exception) {
                println("Error enviando a $username: ${e.message}")
                onLeave(username, room, session)
            }
        }
    }

    suspend fun handleMessage(username: String, room: String, content: String) {
        saveMessage(username, room, content)

        val chatMessage = WsMessage(
            type = "chat",
            sender = username,
            content = content,
            room = room
        )
        broadcast(chatMessage, room)
    }

    suspend fun handleTyping(username: String, room: String) {
        val typingMessage = WsMessage(
            type = "typing",
            sender = username,
            content = "$username esta escribiendo...",
            room = room
        )

        val messageJson = json.encodeToString(typingMessage)

        val roomSessions = connectionsMutex.withLock {
            connections[room]?.filterKeys { it != username } ?: emptyMap()
        }

        roomSessions.forEach { (_, session) ->
            try {
                session.send(Frame.Text(messageJson))
            } catch (_: Exception) { }
        }
    }

    suspend fun getHistory(room: String, limit: Int = 50): List<WsMessage> = dbQuery {
        Messages.selectAll()
            .where { Messages.room eq room }
            .orderBy(Messages.timestamp, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                WsMessage(
                    type = "history",
                    sender = row[Messages.senderName],
                    content = row[Messages.content],
                    room = row[Messages.room],
                    timestamp = row[Messages.timestamp]
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
            }
            .reversed()
    }

    private suspend fun saveMessage(username: String, room: String, content: String) {
        dbQuery {
            Messages.insert {
                it[senderId] = 0
                it[senderName] = username
                it[Messages.room] = room
                it[Messages.content] = content
                it[timestamp] = LocalDateTime.now()
            }
        }
    }

    suspend fun getUsersInRoom(room: String): List<String> {
        return connectionsMutex.withLock {
            connections[room]?.keys?.toList() ?: emptyList()
        }
    }

    suspend fun getActiveRooms(): List<String> {
        return connectionsMutex.withLock {
            connections.keys.toList()
        }
    }
}
