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
    val email = varchar("email", 255).nullable()
    val address = varchar("address", 500).nullable()
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
        val rawUrl = System.getenv("DATABASE_URL")
            ?: throw IllegalStateException("DATABASE_URL no configurada")

        val jdbcUrl = if (rawUrl.startsWith("jdbc:")) rawUrl
                      else "jdbc:" + rawUrl.replace("postgres://", "postgresql://")

        val database = Database.connect(
            url = jdbcUrl,
            driver = "org.postgresql.Driver"
        )

        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Users, Messages)
        }

        println("Base de datos inicializada")
    }

    // Ejecuta queries en Dispatchers.IO para no bloquear el hilo principal
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
