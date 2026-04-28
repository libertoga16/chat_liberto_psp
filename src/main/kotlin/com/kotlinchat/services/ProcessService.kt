package com.kotlinchat.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class ProcessService {

    @Serializable
    data class ProcessResult(
        val command: String,
        val exitCode: Int,
        val output: String,
        val error: String,
        val executionTimeMs: Long
    )

    private val allowedCommands = setOf(
        "uptime", "date", "whoami", "hostname",
        "df", "free", "uname", "cat"
    )

    suspend fun executeCommand(command: String): ProcessResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        val baseCommand = command.split(" ").firstOrNull() ?: ""
        if (baseCommand !in allowedCommands) {
            return@withContext ProcessResult(
                command = command,
                exitCode = -1,
                output = "",
                error = "Comando no permitido: $baseCommand. Permitidos: ${allowedCommands.joinToString()}",
                executionTimeMs = 0
            )
        }

        try {
            val processBuilder = ProcessBuilder(command.split(" "))
                .redirectErrorStream(false)

            val process = processBuilder.start()

            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()

            val completed = process.waitFor(10, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return@withContext ProcessResult(
                    command = command,
                    exitCode = -1,
                    output = "",
                    error = "Proceso terminado por timeout (10s)",
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }

            ProcessResult(
                command = command,
                exitCode = process.exitValue(),
                output = stdout.trim(),
                error = stderr.trim(),
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            ProcessResult(
                command = command,
                exitCode = -1,
                output = "",
                error = "Error ejecutando proceso: ${e.message}",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }
    }

    suspend fun getServerStats(): Map<String, String> {
        val stats = mutableMapOf<String, String>()

        stats["uptime"] = executeCommand("uptime").output
        stats["hostname"] = executeCommand("hostname").output
        stats["user"] = executeCommand("whoami").output
        stats["system"] = executeCommand("uname -a").output
        stats["disk"] = executeCommand("df -h").output
        stats["memory"] = executeCommand("free -h").output

        return stats
    }
}
