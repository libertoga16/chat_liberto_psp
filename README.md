# KotlinChat

Aplicacion de mensajeria en tiempo real construida con **Ktor** en el backend y **Jetpack Compose** en Android. Los mensajes se transmiten via WebSockets, la autenticacion se gestiona con JWT y los datos persisten en PostgreSQL.

> Trabajo de subida de nota — Programacion de Servicios y Procesos

---

## Arquitectura

```
┌─────────────────────────────────┐        ┌──────────────────────────────────┐
│        Android (Cliente)        │        │         Ktor Server              │
│                                 │        │                                  │
│  AuthScreen  → AuthViewModel    │  HTTP  │  POST /api/register              │
│  RoomScreen  → ChatViewModel    │◄──────►│  POST /api/login                 │
│  ChatScreen  → ProfileViewModel │        │  GET/PUT /api/profile            │
│                                 │        │                                  │
│  WebSocketRepository            │  WSS   │  WS /chat/{room}                 │
│  ApiRepository                  │◄──────►│                                  │
└─────────────────────────────────┘        │  ChatService (pub/sub)           │
                                           │  AuthService (JWT + bcrypt)      │
                                           │  ProcessService                  │
                                           │                                  │
                                           │  Exposed ORM → PostgreSQL        │
                                           └──────────────────────────────────┘
```

**Servidor:** los mensajes entran por WebSocket, se persisten en PostgreSQL via Exposed y se difunden a todos los usuarios de la sala mediante un `MutableSharedFlow` protegido por `Mutex`.

**Android:** patron MVVM con `StateFlow`. El `ChatViewModel` colecciona el `SharedFlow` del `WebSocketRepository` y actualiza la UI de forma reactiva.

---

## Tecnologias

| Capa | Tecnologia |
|------|-----------|
| Servidor | Kotlin 1.9 · Ktor 2.3 · Netty |
| Autenticacion | JWT (Auth0) · BCrypt |
| Base de datos | PostgreSQL · Jetbrains Exposed 0.52 |
| Mensajeria | WebSockets (Ktor) |
| Android | Jetpack Compose · Material3 |
| HTTP cliente | Ktor Client (OkHttp) |
| Estado Android | ViewModel · StateFlow · Coroutines |
| Despliegue | Docker · Render.com |

---

## Funcionalidades

- **Registro e inicio de sesion** con validacion y contrasena hasheada con BCrypt (cost 12)
- **Autenticacion JWT** con expiracion de 24 horas; todas las rutas protegidas la verifican
- **Chat en tiempo real** por WebSocket con soporte de multiples salas simultaneas
- **Salas predefinidas** (general, kotlin, android, random) y salas personalizadas
- **Indicador de escritura** — se emite al servidor cuando el usuario escribe y se muestra al resto
- **Historial de mensajes** — al entrar a una sala se recuperan los ultimos 50 mensajes de la base de datos
- **Contador de usuarios** conectados visible en la cabecera del chat
- **Perfil de usuario** con email y direccion editables via API REST
- **Reconexion automatica** con sesion limpia al volver a la sala

---

## Estructura del proyecto

```
kotlinchat-server/
├── src/main/kotlin/com/kotlinchat/
│   ├── Application.kt              # Punto de entrada, instalacion de plugins
│   ├── database/
│   │   └── DatabaseFactory.kt     # Configuracion de Exposed + esquema de tablas
│   ├── models/
│   │   └── Models.kt              # Data classes y DTOs serializables
│   ├── plugins/
│   │   ├── Security.kt            # Configuracion JWT
│   │   ├── Serialization.kt       # Kotlinx serialization
│   │   └── Sockets.kt             # Configuracion de WebSockets
│   ├── routes/
│   │   ├── AuthRoutes.kt          # /api/register, /api/login, /api/profile
│   │   └── ChatRoutes.kt          # WS /chat/{room}, /api/chat/*, /api/system/*
│   └── services/
│       ├── AuthService.kt         # Logica de registro, login y perfil
│       ├── ChatService.kt         # Conexiones, broadcast, historial
│       └── ProcessService.kt      # Comandos y estadisticas del servidor
├── Dockerfile                     # Multistage build (gradle → JRE alpine)
├── render.yaml                    # Despliegue automatico en Render.com
└── src/main/resources/
    └── application.conf           # Puerto, JWT config
```

---

## API

### Autenticacion

| Metodo | Ruta | Descripcion | Auth |
|--------|------|-------------|------|
| `POST` | `/api/register` | Registro de nuevo usuario | — |
| `POST` | `/api/login` | Login, devuelve JWT | — |
| `GET` | `/api/profile` | Obtener perfil del usuario | JWT |
| `PUT` | `/api/profile` | Actualizar email / direccion | JWT |

### Chat

| Metodo | Ruta | Descripcion | Auth |
|--------|------|-------------|------|
| `WS` | `/chat/{room}?username=X` | Conexion WebSocket al chat | — |
| `GET` | `/api/chat/rooms` | Salas activas en memoria | JWT |
| `GET` | `/api/chat/rooms/{room}/users` | Usuarios conectados en sala | JWT |
| `GET` | `/api/chat/rooms/{room}/history` | Historial de mensajes | JWT |

### Protocolo WebSocket

Los mensajes se intercambian como JSON con la estructura `WsMessage`:

```json
{
  "type": "chat|typing|join|leave|history|system",
  "sender": "usuario",
  "content": "texto",
  "room": "general",
  "timestamp": 1715000000000
}
```

---

## Ejecucion local

### Prerequisitos
- JDK 17+
- PostgreSQL local o variable de entorno `DATABASE_URL`

### Con Gradle

```bash
git clone https://github.com/tu-usuario/kotlinchat-server
cd kotlinchat-server
./gradlew run
```

El servidor arranca en `http://localhost:8080`.

### Con Docker

```bash
docker build -t kotlinchat-server .
docker run -p 8080:8080 -e DATABASE_URL=postgresql://... kotlinchat-server
```

### Probar con cURL

```bash
# Registro
curl -X POST http://localhost:8080/api/register \
  -H "Content-Type: application/json" \
  -d '{"username":"liberto","password":"123456"}'

# Login
curl -X POST http://localhost:8080/api/login \
  -H "Content-Type: application/json" \
  -d '{"username":"liberto","password":"123456"}'

# Stats del servidor (requiere token)
curl -H "Authorization: Bearer TOKEN_AQUI" http://localhost:8080/api/system/stats

# WebSocket
websocat ws://localhost:8080/chat/general?username=liberto
```

---

## Despliegue en Render.com

El fichero `render.yaml` define el servicio web (Docker) y la base de datos PostgreSQL. Basta con conectar el repositorio en Render para que el despliegue sea completamente automatico.

Variables de entorno necesarias en produccion:

| Variable | Descripcion |
|----------|-------------|
| `PORT` | Puerto del servidor (por defecto 8080) |
| `DATABASE_URL` | URL de conexion PostgreSQL |

> **Nota de seguridad:** cambiar `jwt.secret` en `application.conf` antes de desplegar en produccion.

---

## Cliente Android

El cliente se encuentra en el repositorio `kotlinchat-android`. Requiere configurar la URL del servidor en `ApiRepository` y `WebSocketRepository` antes de compilar.

Pantallas principales:
- **AuthScreen** — registro e inicio de sesion
- **RoomScreen** — listado de salas y perfil de usuario
- **ChatScreen** — chat en tiempo real con burbujas de mensaje, indicador de escritura y contador de usuarios
- **ProfileScreen** — visualizacion y edicion del perfil

---

## Licencia

MIT
