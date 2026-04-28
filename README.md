# KotlinChat Server

Servidor de chat en tiempo real desarrollado con Ktor y Kotlin.
Trabajo de subida de nota para Programacion de Servicios y Procesos.

## Requisitos

- JDK 17 o superior
- Gradle 8.x

## Como ejecutar

```bash
cd kotlinchat-server
gradle wrapper
./gradlew run
```

El servidor arranca en http://localhost:8080

## Probar con cURL

Registro:
```bash
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"username":"liberto","password":"123456"}'
```

Login:
```bash
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"liberto","password":"123456"}'
```

Stats del servidor (requiere token):
```bash
curl -H "Authorization: Bearer TOKEN_AQUI" http://localhost:8080/api/system/stats
```

WebSocket:
```bash
websocat ws://localhost:8080/chat/general?username=liberto
```

## Estructura

```
src/main/kotlin/com/kotlinchat/
├── Application.kt
├── plugins/
│   ├── Security.kt
│   ├── Sockets.kt
│   └── Serialization.kt
├── models/
│   └── Models.kt
├── database/
│   └── DatabaseFactory.kt
├── routes/
│   ├── AuthRoutes.kt
│   └── ChatRoutes.kt
└── services/
    ├── AuthService.kt
    ├── ChatService.kt
    └── ProcessService.kt
```
