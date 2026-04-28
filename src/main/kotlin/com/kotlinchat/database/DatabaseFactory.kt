package com.kotlinchat.database

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.datetime
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object Users : Table("users") {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val createdAt = datetime("created_at").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object Messages : Table("messages") {
    val id = integer("id").autoIncrement()
    val senderId = integer("sender_id").references(Users.id)
    val senderName = varchar("sender_name", 50)
    val room = varchar("room", 100).default("general")
    val content = text("content")
    val timestamp = datetime("timestamp").default(LocalDateTime.now())

    override val primaryKey = PrimaryKey(id)
}

object DatabaseFactory {

    fun init() {
        val database = Database.connect(
            url = "jdbc:sqlite:./kotlinchat.db",
            driver = "org.sqlite.JDBC"
        )

        transaction(database) {
            SchemaUtils.create(Users, Messages)
        }

        println("Base de datos inicializada")
    }

    // Ejecuta queries en Dispatchers.IO para no bloquear el hilo principal
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
