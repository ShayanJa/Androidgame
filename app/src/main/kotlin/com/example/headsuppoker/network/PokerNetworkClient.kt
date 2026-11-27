package com.example.headsuppoker.network

import com.example.headsuppoker.model.OpenRoomSummaryDto
import com.example.headsuppoker.model.RoomStateDto
import com.example.headsuppoker.model.ServerStatsDto
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

/**
 * WebSocket Client for the Heads-Up Poker Room Server.
 * Connects to either the shared Multiplayer Room Server (e.g., ws://10.0.2.2:8080 or cloud/LAN IP)
 * or automatically falls back to the phone's EmbeddedRoomServer so rooms always work seamlessly.
 */
class PokerNetworkClient(
    private val scope: CoroutineScope,
    private val embeddedServer: EmbeddedRoomServer,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    val playerId: String = UUID.randomUUID().toString()
    private var webSocket: WebSocket? = null
    private var simulatedPhoneSocket: WebSocket? = null
    private var botTurnJob: Job? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _activeServerUrl = MutableStateFlow("ws://10.0.2.2:8080")
    val activeServerUrl: StateFlow<String> = _activeServerUrl.asStateFlow()

    private val _usingEmbeddedFallback = MutableStateFlow(false)
    val usingEmbeddedFallback: StateFlow<Boolean> = _usingEmbeddedFallback.asStateFlow()

    private val _serverStats = MutableStateFlow(ServerStatsDto())
    val serverStats: StateFlow<ServerStatsDto> = _serverStats.asStateFlow()

    private val _roomState = MutableStateFlow<RoomStateDto?>(null)
    val roomState: StateFlow<RoomStateDto?> = _roomState.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    private val _isSecondPhoneSimulated = MutableStateFlow(false)
    val isSecondPhoneSimulated: StateFlow<Boolean> = _isSecondPhoneSimulated.asStateFlow()

    private var pendingCommandAfterConnect: JsonObject? = null

    fun connect(serverUrl: String, forceEmbedded: Boolean = false) {
        disconnectSimulatedSecondPhone()
        webSocket?.cancel()
        _connectionState.value = ConnectionState.CONNECTING
        _errorBanner.value = null

        val targetUrl = if (forceEmbedded) {
            val port = embeddedServer.start()
            _usingEmbeddedFallback.value = true
            "ws://127.0.0.1:$port"
        } else {
            _usingEmbeddedFallback.value = false
            normalizeWsUrl(serverUrl)
        }
        _activeServerUrl.value = targetUrl

        val request = try {
            Request.Builder().url(targetUrl).build()
        } catch (e: Exception) {
            _errorBanner.value = "Invalid server URL: ${e.message}"
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        webSocket = okHttp.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (webSocket !== this@PokerNetworkClient.webSocket) return
                    _connectionState.value = ConnectionState.CONNECTED
                    pendingCommandAfterConnect?.let { cmd ->
                        pendingCommandAfterConnect = null
                        webSocket.send(cmd.toString())
                    } ?: run {
                        webSocket.send("""{"type":"GET_STATS"}""")
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (webSocket !== this@PokerNetworkClient.webSocket) return
                    handleIncomingMessage(text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (webSocket !== this@PokerNetworkClient.webSocket) return
                    // If external server wasn't reachable, transparently fall back to embedded room server
                    if (!_usingEmbeddedFallback.value) {
                        scope.launch(Dispatchers.IO) {
                            val localPort = embeddedServer.start()
                            _usingEmbeddedFallback.value = true
                            val lanIp = EmbeddedRoomServer.getDeviceLanIp() ?: "127.0.0.1"
                            _activeServerUrl.value = "ws://$lanIp:$localPort"
                            connect("ws://127.0.0.1:$localPort", forceEmbedded = true)
                        }
                    } else {
                        _connectionState.value = ConnectionState.DISCONNECTED
                        _errorBanner.value = "Connection lost: ${t.localizedMessage ?: "Unable to reach room server"}"
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (webSocket !== this@PokerNetworkClient.webSocket) return
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        )
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val obj = json.parseToJsonElement(text).jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "ROOM_STATE" -> {
                    val parsed = json.decodeFromString(RoomStateDto.serializer(), text)
                    _roomState.value = parsed
                    parsed.serverStats?.let { _serverStats.value = it }
                }
                "SERVER_STATS" -> {
                    val activeRooms = obj["activeRooms"]?.jsonPrimitive?.intOrNull ?: 0
                    val onlinePlayers = obj["onlinePlayers"]?.jsonPrimitive?.intOrNull ?: 0
                    val openRooms = obj["openRooms"]?.jsonArray?.mapNotNull { el ->
                        val item = el.jsonObject
                        val code = item["roomCode"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val host = item["hostName"]?.jsonPrimitive?.content ?: "Host"
                        val blinds = item["blinds"]?.jsonPrimitive?.content ?: "10/20"
                        val stack = item["startingStack"]?.jsonPrimitive?.intOrNull ?: 1000
                        OpenRoomSummaryDto(code, host, blinds, stack)
                    }.orEmpty()
                    _serverStats.value = ServerStatsDto(activeRooms, onlinePlayers, openRooms)
                }
                "ERROR" -> {
                    _errorBanner.value = obj["message"]?.jsonPrimitive?.content ?: "Server error"
                }
                "LEFT_ROOM" -> {
                    _roomState.value = null
                }
            }
        } catch (_: Exception) {
        }
    }

    fun clearError() {
        _errorBanner.value = null
    }

    fun createRoom(
        playerName: String,
        startingStack: Int,
        smallBlind: Int,
        bigBlind: Int,
        isPublic: Boolean,
        customRoomCode: String? = null,
    ) {
        disconnectSimulatedSecondPhone()
        val payload = buildJsonObject {
            put("type", "CREATE_ROOM")
            put("playerName", playerName.ifBlank { "Player 1" })
            put("playerId", playerId)
            put("startingStack", startingStack)
            put("smallBlind", smallBlind)
            put("bigBlind", bigBlind)
            put("isPublic", isPublic)
            if (!customRoomCode.isNullOrBlank()) {
                put("roomCode", customRoomCode.trim().uppercase())
            }
        }
        sendOrConnect(payload)
    }

    fun joinRoom(roomCode: String, playerName: String) {
        disconnectSimulatedSecondPhone()
        val cleanCode = roomCode.trim().uppercase()
        if (cleanCode.isEmpty()) {
            _errorBanner.value = "Please enter a valid 6-character Room Code."
            return
        }
        val payload = buildJsonObject {
            put("type", "JOIN_ROOM")
            put("roomCode", cleanCode)
            put("playerName", playerName.ifBlank { "Player 2" })
            put("playerId", playerId)
        }
        sendOrConnect(payload)
    }

    fun quickMatch(playerName: String) {
        disconnectSimulatedSecondPhone()
        val payload = buildJsonObject {
            put("type", "QUICK_MATCH")
            put("playerName", playerName.ifBlank { "Player" })
            put("playerId", playerId)
        }
        sendOrConnect(payload)
    }

    fun sendPlayerAction(action: String, amount: Int = 0) {
        val payload = buildJsonObject {
            put("type", "PLAYER_ACTION")
            put("action", action)
            put("amount", amount)
        }
        webSocket?.send(payload.toString())
    }

    fun requestNextHand() {
        webSocket?.send("""{"type":"NEXT_HAND"}""")
    }

    fun requestRematch() {
        webSocket?.send("""{"type":"REMATCH"}""")
    }

    fun sendChatEmoji(text: String) {
        val payload = buildJsonObject {
            put("type", "CHAT_EMOJI")
            put("text", text)
        }
        webSocket?.send(payload.toString())
    }

    fun leaveRoom() {
        disconnectSimulatedSecondPhone()
        webSocket?.send("""{"type":"LEAVE_ROOM"}""")
        _roomState.value = null
    }

    /**
     * Spawns a real second WebSocket client ("Phone 2") that connects to the active server URL
     * and joins the current room using its 6-character Room Code!
     * Allows testing the full two-phone room code flow on a single phone or while waiting for a friend.
     */
    fun connectSimulatedSecondPhoneToCurrentRoom(opponentName: String = "Phone 2 (Shark)") {
        val currentRoomCode = _roomState.value?.roomCode ?: return
        disconnectSimulatedSecondPhone()

        val wsUrl = if (_usingEmbeddedFallback.value) {
            "ws://127.0.0.1:${embeddedServer.port}"
        } else {
            _activeServerUrl.value
        }

        val req = Request.Builder().url(wsUrl).build()
        val simPlayerId = UUID.randomUUID().toString()
        _isSecondPhoneSimulated.value = true

        simulatedPhoneSocket = okHttp.newWebSocket(
            req,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val joinMsg = buildJsonObject {
                        put("type", "JOIN_ROOM")
                        put("roomCode", currentRoomCode)
                        put("playerName", opponentName)
                        put("playerId", simPlayerId)
                    }
                    webSocket.send(joinMsg.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val obj = json.parseToJsonElement(text).jsonObject
                        if (obj["type"]?.jsonPrimitive?.content == "ROOM_STATE") {
                            val state = json.decodeFromString(RoomStateDto.serializer(), text)
                            if (state.isMyTurn) {
                                scheduleSimulatedPhoneDecision(webSocket, state)
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        )
    }

    private fun scheduleSimulatedPhoneDecision(ws: WebSocket, state: RoomStateDto) {
        botTurnJob?.cancel()
        botTurnJob = scope.launch(Dispatchers.IO) {
            delay(750)
            val me = state.me ?: return@launch
            val toCall = state.toCall
            val highCard = me.holeCards.maxOfOrNull { it.rank } ?: 10
            val isPair = me.holeCards.size == 2 && me.holeCards[0].rank == me.holeCards[1].rank

            val actionJson = when {
                toCall == 0 -> {
                    if (isPair || (highCard >= 13 && state.pot <= state.bigBlind * 6)) {
                        val betTo = minOf(me.currentBet + me.chips, state.minRaiseTo)
                        if (betTo > state.currentBet) {
                            buildJsonObject {
                                put("type", "PLAYER_ACTION")
                                put("action", "RAISE")
                                put("amount", betTo)
                            }
                        } else {
                            buildJsonObject {
                                put("type", "PLAYER_ACTION")
                                put("action", "CHECK")
                            }
                        }
                    } else {
                        buildJsonObject {
                            put("type", "PLAYER_ACTION")
                            put("action", "CHECK")
                        }
                    }
                }
                toCall <= state.bigBlind * 2 || isPair || highCard >= 12 -> {
                    buildJsonObject {
                        put("type", "PLAYER_ACTION")
                        put("action", "CALL")
                    }
                }
                toCall > me.chips / 2 && highCard < 11 -> {
                    buildJsonObject {
                        put("type", "PLAYER_ACTION")
                        put("action", "FOLD")
                    }
                }
                else -> {
                    buildJsonObject {
                        put("type", "PLAYER_ACTION")
                        put("action", "CALL")
                    }
                }
            }
            ws.send(actionJson.toString())
        }
    }

    fun disconnectSimulatedSecondPhone() {
        botTurnJob?.cancel()
        botTurnJob = null
        simulatedPhoneSocket?.close(1000, "Leaving")
        simulatedPhoneSocket = null
        _isSecondPhoneSimulated.value = false
    }

    private fun sendOrConnect(payload: JsonObject) {
        if (_connectionState.value == ConnectionState.CONNECTED && webSocket != null) {
            webSocket?.send(payload.toString())
        } else {
            pendingCommandAfterConnect = payload
            connect(_activeServerUrl.value, forceEmbedded = _usingEmbeddedFallback.value)
        }
    }

    private fun normalizeWsUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("ws://") || trimmed.startsWith("wss://")) return trimmed
        if (trimmed.startsWith("http://")) return "ws://" + trimmed.removePrefix("http://")
        if (trimmed.startsWith("https://")) return "wss://" + trimmed.removePrefix("https://")
        return "ws://$trimmed"
    }
}
