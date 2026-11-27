package com.example.headsuppoker.ui.poker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.headsuppoker.engine.HandEvaluator
import com.example.headsuppoker.model.RoomStateDto
import com.example.headsuppoker.model.ServerStatsDto
import com.example.headsuppoker.network.ConnectionState
import com.example.headsuppoker.network.EmbeddedRoomServer
import com.example.headsuppoker.network.PokerNetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PokerViewModel : ViewModel() {
    val embeddedServer = EmbeddedRoomServer()
    val client = PokerNetworkClient(viewModelScope, embeddedServer)

    private val _playerName = MutableStateFlow("Player ${(100..999).random()}")
    val playerName: StateFlow<String> = _playerName.asStateFlow()

    private val _roomCodeInput = MutableStateFlow("")
    val roomCodeInput: StateFlow<String> = _roomCodeInput.asStateFlow()

    private val _serverUrlInput = MutableStateFlow("ws://10.0.2.2:8080")
    val serverUrlInput: StateFlow<String> = _serverUrlInput.asStateFlow()

    private val _startingStackInput = MutableStateFlow(1000)
    val startingStackInput: StateFlow<Int> = _startingStackInput.asStateFlow()

    private val _smallBlindInput = MutableStateFlow(10)
    val smallBlindInput: StateFlow<Int> = _smallBlindInput.asStateFlow()

    private val _isPublicRoomInput = MutableStateFlow(true)
    val isPublicRoomInput: StateFlow<Boolean> = _isPublicRoomInput.asStateFlow()

    val connectionState: StateFlow<ConnectionState> = client.connectionState
    val activeServerUrl: StateFlow<String> = client.activeServerUrl
    val usingEmbeddedFallback: StateFlow<Boolean> = client.usingEmbeddedFallback
    val serverStats: StateFlow<ServerStatsDto> = client.serverStats
    val roomState: StateFlow<RoomStateDto?> = client.roomState
    val errorBanner: StateFlow<String?> = client.errorBanner
    val isSecondPhoneSimulated: StateFlow<Boolean> = client.isSecondPhoneSimulated

    init {
        client.connect(_serverUrlInput.value)
    }

    fun updatePlayerName(name: String) {
        _playerName.value = name.take(20)
    }

    fun updateRoomCodeInput(code: String) {
        _roomCodeInput.value = code.uppercase().filter { it.isLetterOrDigit() }.take(6)
    }

    fun updateServerUrlInput(url: String) {
        _serverUrlInput.value = url
    }

    fun updateStartingStack(stack: Int) {
        _startingStackInput.value = stack
    }

    fun updateBlinds(sb: Int) {
        _smallBlindInput.value = sb
    }

    fun updatePublicRoom(isPublic: Boolean) {
        _isPublicRoomInput.value = isPublic
    }

    fun reconnectToServer(forceEmbedded: Boolean = false) {
        client.connect(_serverUrlInput.value, forceEmbedded = forceEmbedded)
    }

    fun createRoom() {
        val sb = _smallBlindInput.value
        client.createRoom(
            playerName = _playerName.value,
            startingStack = _startingStackInput.value,
            smallBlind = sb,
            bigBlind = sb * 2,
            isPublic = _isPublicRoomInput.value,
        )
    }

    fun joinRoomByCode(code: String = _roomCodeInput.value) {
        client.joinRoom(code, _playerName.value)
    }

    fun quickMatch() {
        client.quickMatch(_playerName.value)
    }

    fun spawnSecondPhoneInRoom() {
        client.connectSimulatedSecondPhoneToCurrentRoom()
    }

    fun fold() = client.sendPlayerAction("FOLD")
    fun check() = client.sendPlayerAction("CHECK")
    fun call() = client.sendPlayerAction("CALL")
    fun raiseTo(amount: Int) = client.sendPlayerAction("RAISE", amount)
    fun allIn() = client.sendPlayerAction("ALL_IN")
    fun nextHand() = client.requestNextHand()
    fun rematch() = client.requestRematch()
    fun sendQuickEmoji(text: String) = client.sendChatEmoji(text)
    fun leaveRoom() = client.leaveRoom()
    fun dismissError() = client.clearError()

    fun evaluateMyCurrentHandStrength(state: RoomStateDto): String? {
        val myCards = state.me?.holeCards?.filter { !it.hidden }.orEmpty()
        if (myCards.size < 2) return null
        val allVisible = myCards + state.communityCards
        if (allVisible.size >= 5) {
            return HandEvaluator.evaluateBestHand(allVisible).categoryName
        }
        return if (myCards[0].rank == myCards[1].rank) {
            "Pocket Pair (${myCards[0].rankLabel}s)"
        } else if (myCards[0].suit == myCards[1].suit) {
            "${myCards.maxBy { it.rank }.rankLabel}-High Suited"
        } else {
            "${myCards.maxBy { it.rank }.rankLabel}-High"
        }
    }

    override fun onCleared() {
        super.onCleared()
        client.disconnectSimulatedSecondPhone()
        embeddedServer.stop()
    }
}
