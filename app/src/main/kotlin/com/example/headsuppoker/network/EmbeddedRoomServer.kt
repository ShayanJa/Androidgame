package com.example.headsuppoker.network

import com.example.headsuppoker.engine.HeadsUpRoomManager
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Embedded RFC 6455 WebSocket Multiplayer Room Server that runs directly on an Android device.
 * Allows any phone to host multiple 2-player rooms over Wi-Fi / Hotspot, or act as an automatic
 * fallback when the external cloud/LAN server is offline.
 */
class EmbeddedRoomServer(
    private val roomManager: HeadsUpRoomManager = HeadsUpRoomManager(),
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val executor = Executors.newCachedThreadPool()
    private val clients = Collections.newSetFromMap(ConcurrentHashMap<ClientConnection, Boolean>())
    private val roomSockets = ConcurrentHashMap<String, Array<ClientConnection?>>()

    @Volatile
    private var serverSocket: ServerSocket? = null

    var port: Int = 0
        private set

    val isRunning: Boolean
        get() = serverSocket != null && serverSocket?.isClosed == false

    @Synchronized
    fun start(preferredPort: Int = 18080): Int {
        if (isRunning) return port
        val socket = try {
            ServerSocket(preferredPort)
        } catch (_: Exception) {
            ServerSocket(0)
        }
        serverSocket = socket
        port = socket.localPort

        executor.execute {
            while (!socket.isClosed) {
                try {
                    val raw = socket.accept()
                    raw.tcpNoDelay = true
                    executor.execute { handleSocket(raw) }
                } catch (_: Exception) {
                    if (socket.isClosed) break
                }
            }
        }
        return port
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        clients.forEach { it.close() }
        clients.clear()
        roomSockets.clear()
    }

    private inner class ClientConnection(
        val socket: Socket,
        val input: InputStream,
        val output: OutputStream,
    ) {
        var roomCode: String? = null
        var seat: Int? = null
        var playerId: String = UUID.randomUUID().toString()

        @Synchronized
        fun sendText(payloadText: String) {
            try {
                val bytes = payloadText.toByteArray(Charsets.UTF_8)
                val len = bytes.size
                output.write(0x81)
                when {
                    len < 126 -> output.write(len)
                    len < 65536 -> {
                        output.write(126)
                        output.write((len shr 8) and 0xFF)
                        output.write(len and 0xFF)
                    }
                    else -> {
                        output.write(127)
                        val l = len.toLong()
                        for (shift in 56 downTo 0 step 8) {
                            output.write(((l shr shift) and 0xFF).toInt())
                        }
                    }
                }
                output.write(bytes)
                output.flush()
            } catch (_: Exception) {
            }
        }

        fun close() {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun handleSocket(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val output = socket.getOutputStream()

            val requestHeaders = readHttpHeaders(input)
            val wsKey = requestHeaders["sec-websocket-key"]
            if (wsKey == null) {
                socket.close()
                return
            }

            val acceptSeed = wsKey.trim() + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
            val sha1 = MessageDigest.getInstance("SHA-1").digest(acceptSeed.toByteArray(Charsets.UTF_8))
            val acceptKey = Base64.getEncoder().encodeToString(sha1)

            val handshake = buildString {
                append("HTTP/1.1 101 Switching Protocols\r\n")
                append("Upgrade: websocket\r\n")
                append("Connection: Upgrade\r\n")
                append("Sec-WebSocket-Accept: $acceptKey\r\n\r\n")
            }
            output.write(handshake.toByteArray(Charsets.UTF_8))
            output.flush()

            val conn = ClientConnection(socket, input, output)
            clients.add(conn)
            sendStats(conn)

            while (!socket.isClosed) {
                val frame = readFrame(input) ?: break
                when (frame.first) {
                    0x08 -> break // Close
                    0x09 -> {
                        // Ping -> Pong
                        synchronized(conn) {
                            output.write(0x8A)
                            output.write(frame.second.size)
                            output.write(frame.second)
                            output.flush()
                        }
                    }
                    0x01 -> {
                        val text = String(frame.second, Charsets.UTF_8)
                        handleClientMessage(conn, text)
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            clients.find { it.socket == socket }?.let { conn ->
                clients.remove(conn)
                leaveRoom(conn)
                broadcastStats()
            }
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun handleClientMessage(conn: ClientConnection, rawJson: String) {
        val obj = try {
            json.parseToJsonElement(rawJson) as? JsonObject ?: return
        } catch (_: Exception) {
            return
        }

        val type = obj["type"]?.jsonPrimitive?.content?.uppercase() ?: return
        when (type) {
            "GET_STATS", "PING" -> sendStats(conn)

            "CREATE_ROOM" -> {
                leaveRoom(conn)
                val playerName = obj["playerName"]?.jsonPrimitive?.content ?: "Player 1"
                val playerId = obj["playerId"]?.jsonPrimitive?.content ?: conn.playerId
                val startingStack = obj["startingStack"]?.jsonPrimitive?.intOrNull ?: 1000
                val smallBlind = obj["smallBlind"]?.jsonPrimitive?.intOrNull ?: 10
                val bigBlind = obj["bigBlind"]?.jsonPrimitive?.intOrNull ?: 20
                val isPublic = obj["isPublic"]?.jsonPrimitive?.booleanOrNull ?: false
                val customCode = obj["roomCode"]?.jsonPrimitive?.content

                val room = roomManager.createRoom(
                    playerName = playerName,
                    playerId = playerId,
                    customCode = customCode,
                    startingStack = startingStack,
                    smallBlind = smallBlind,
                    bigBlind = bigBlind,
                    isPublic = isPublic,
                )
                conn.roomCode = room.roomCode
                conn.seat = 0
                conn.playerId = playerId
                val arr = roomSockets.getOrPut(room.roomCode) { arrayOfNulls(2) }
                arr[0] = conn

                broadcastRoom(room)
                broadcastStats()
            }

            "JOIN_ROOM" -> {
                val roomCode = obj["roomCode"]?.jsonPrimitive?.content?.trim()?.uppercase().orEmpty()
                val playerName = obj["playerName"]?.jsonPrimitive?.content ?: "Player 2"
                val playerId = obj["playerId"]?.jsonPrimitive?.content ?: conn.playerId

                val res = roomManager.joinRoom(roomCode, playerName, playerId)
                res.onFailure { err ->
                    sendError(conn, err.message ?: "Unable to join room.")
                }.onSuccess { (room, seat) ->
                    leaveRoom(conn)
                    conn.roomCode = room.roomCode
                    conn.seat = seat
                    conn.playerId = playerId
                    val arr = roomSockets.getOrPut(room.roomCode) { arrayOfNulls(2) }
                    arr[seat] = conn

                    broadcastRoom(room)
                    broadcastStats()
                }
            }

            "QUICK_MATCH" -> {
                leaveRoom(conn)
                val playerName = obj["playerName"]?.jsonPrimitive?.content ?: "Player"
                val playerId = obj["playerId"]?.jsonPrimitive?.content ?: conn.playerId
                val (room, seat) = roomManager.quickMatch(playerName = playerName, playerId = playerId)
                conn.roomCode = room.roomCode
                conn.seat = seat
                conn.playerId = playerId
                val arr = roomSockets.getOrPut(room.roomCode) { arrayOfNulls(2) }
                arr[seat] = conn

                broadcastRoom(room)
                broadcastStats()
            }

            "PLAYER_ACTION" -> {
                val code = conn.roomCode ?: return
                val seat = conn.seat ?: return
                val room = roomManager.getRoom(code) ?: return
                val action = obj["action"]?.jsonPrimitive?.content ?: return
                val amount = obj["amount"]?.jsonPrimitive?.intOrNull ?: 0

                val res = roomManager.handleAction(room, seat, action, amount)
                res.onFailure { err ->
                    sendError(conn, err.message ?: "Illegal action.")
                }.onSuccess {
                    broadcastRoom(room)
                }
            }

            "NEXT_HAND" -> {
                val code = conn.roomCode ?: return
                val room = roomManager.getRoom(code) ?: return
                roomManager.startNextHand(room)
                broadcastRoom(room)
            }

            "REMATCH" -> {
                val code = conn.roomCode ?: return
                val room = roomManager.getRoom(code) ?: return
                roomManager.rematch(room)
                broadcastRoom(room)
            }

            "CHAT_EMOJI" -> {
                val code = conn.roomCode ?: return
                val seat = conn.seat ?: return
                val room = roomManager.getRoom(code) ?: return
                val text = obj["text"]?.jsonPrimitive?.content ?: return
                roomManager.addChat(room, seat, text)
                broadcastRoom(room)
            }

            "LEAVE_ROOM" -> {
                leaveRoom(conn)
                conn.sendText("""{"type":"LEFT_ROOM"}""")
                broadcastStats()
            }
        }
    }

    private fun leaveRoom(conn: ClientConnection) {
        val code = conn.roomCode ?: return
        val seat = conn.seat
        conn.roomCode = null
        conn.seat = null

        val room = roomManager.getRoom(code) ?: return
        if (seat != null && seat in 0..1) {
            room.players[seat]?.connected = false
            roomSockets[code]?.set(seat, null)
            room.statusMessage = "${room.players[seat]?.name ?: "Player"} disconnected."
            broadcastRoom(room)
        }
    }

    private fun broadcastRoom(room: HeadsUpRoomManager.InternalRoom) {
        val sockets = roomSockets[room.roomCode] ?: return
        for (seat in 0..1) {
            val client = sockets[seat] ?: continue
            val state = roomManager.serializeForSeat(room, seat, clients.size)
            client.sendText(json.encodeToString(com.example.headsuppoker.model.RoomStateDto.serializer(), state))
        }
    }

    private fun sendStats(conn: ClientConnection) {
        val stats = roomManager.getServerStats(clients.size)
        val openRoomsJson = stats.openRooms.joinToString(",") {
            """{"roomCode":"${it.roomCode}","hostName":"${it.hostName}","blinds":"${it.blinds}","startingStack":${it.startingStack}}"""
        }
        conn.sendText(
            """{"type":"SERVER_STATS","activeRooms":${stats.activeRooms},"onlinePlayers":${stats.onlinePlayers},"openRooms":[$openRoomsJson]}"""
        )
    }

    private fun broadcastStats() {
        clients.forEach { sendStats(it) }
    }

    private fun sendError(conn: ClientConnection, message: String) {
        val escaped = message.replace("\"", "\\\"")
        conn.sendText("""{"type":"ERROR","message":"$escaped"}""")
    }

    private fun readHttpHeaders(input: InputStream): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val lineBuffer = StringBuilder()
        var prev = -1
        while (true) {
            val b = input.read()
            if (b == -1) break
            if (prev == '\r'.code && b == '\n'.code) {
                val line = lineBuffer.toString().trimEnd('\r')
                lineBuffer.setLength(0)
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                }
            } else {
                lineBuffer.append(b.toChar())
            }
            prev = b
        }
        return headers
    }

    private fun readFrame(input: InputStream): Pair<Int, ByteArray>? {
        val b0 = input.read()
        if (b0 == -1) return null
        val b1 = input.read()
        if (b1 == -1) return null

        val opcode = b0 and 0x0F
        val masked = (b1 and 0x80) != 0
        var payloadLen = b1 and 0x7F

        if (payloadLen == 126) {
            val hi = input.read()
            val lo = input.read()
            if (hi == -1 || lo == -1) return null
            payloadLen = (hi shl 8) or lo
        } else if (payloadLen == 127) {
            var lenLong = 0L
            repeat(8) {
                val byteVal = input.read()
                if (byteVal == -1) return null
                lenLong = (lenLong shl 8) or byteVal.toLong()
            }
            payloadLen = lenLong.toInt()
        }

        val maskKey = ByteArray(4)
        if (masked) {
            var read = 0
            while (read < 4) {
                val r = input.read(maskKey, read, 4 - read)
                if (r == -1) return null
                read += r
            }
        }

        val payload = ByteArray(payloadLen)
        var totalRead = 0
        while (totalRead < payloadLen) {
            val r = input.read(payload, totalRead, payloadLen - totalRead)
            if (r == -1) return null
            totalRead += r
        }

        if (masked) {
            for (i in 0 until payloadLen) {
                payload[i] = (payload[i].toInt() xor maskKey[i and 3].toInt()).toByte()
            }
        }
        return opcode to payload
    }

    companion object {
        fun getDeviceLanIp(): String? {
            return try {
                NetworkInterface.getNetworkInterfaces()?.toList()
                    ?.flatMap { it.inetAddresses.toList() }
                    ?.firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                    ?.hostAddress
            } catch (_: Exception) {
                null
            }
        }
    }
}
