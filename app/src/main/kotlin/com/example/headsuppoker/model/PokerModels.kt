package com.example.headsuppoker.model

import kotlinx.serialization.Serializable

@Serializable
data class CardDto(
    val rank: Int = 0, // 2..14 (11=J, 12=Q, 13=K, 14=A), 0 when hidden
    val suit: String = "HIDDEN", // SPADES, HEARTS, DIAMONDS, CLUBS, or HIDDEN
    val hidden: Boolean = false,
) {
    val rankLabel: String
        get() = when (rank) {
            14 -> "A"
            13 -> "K"
            12 -> "Q"
            11 -> "J"
            10 -> "10"
            in 2..9 -> rank.toString()
            else -> "?"
        }

    val suitSymbol: String
        get() = when (suit.uppercase()) {
            "SPADES" -> "♠"
            "HEARTS" -> "♥"
            "DIAMONDS" -> "♦"
            "CLUBS" -> "♣"
            else -> "★"
        }

    val isRed: Boolean
        get() = suit.equals("HEARTS", ignoreCase = true) || suit.equals("DIAMONDS", ignoreCase = true)
}

@Serializable
data class PlayerViewDto(
    val seat: Int,
    val name: String,
    val chips: Int,
    val currentBet: Int = 0,
    val holeCards: List<CardDto> = emptyList(),
    val folded: Boolean = false,
    val allIn: Boolean = false,
    val connected: Boolean = true,
    val lastActionText: String? = null,
    val bestHandName: String? = null,
)

@Serializable
data class ChatMessageDto(
    val seat: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long = 0L,
)

@Serializable
data class OpenRoomSummaryDto(
    val roomCode: String,
    val hostName: String,
    val blinds: String = "10/20",
    val startingStack: Int = 1000,
)

@Serializable
data class ServerStatsDto(
    val activeRooms: Int = 0,
    val onlinePlayers: Int = 0,
    val openRooms: List<OpenRoomSummaryDto> = emptyList(),
)

@Serializable
data class RoomStateDto(
    val type: String = "ROOM_STATE",
    val roomCode: String = "",
    val isPublic: Boolean = false,
    val mySeat: Int = 0,
    val handNumber: Int = 0,
    val dealerSeat: Int = 0,
    val smallBlind: Int = 10,
    val bigBlind: Int = 20,
    val startingStack: Int = 1000,
    val street: String = "WAITING", // WAITING, PREFLOP, FLOP, TURN, RIVER, SHOWDOWN, MATCH_OVER
    val pot: Int = 0,
    val currentBet: Int = 0,
    val minRaiseTo: Int = 40,
    val toCall: Int = 0,
    val currentTurnSeat: Int = -1,
    val communityCards: List<CardDto> = emptyList(),
    val statusMessage: String = "",
    val winnerSeats: List<Int> = emptyList(),
    val winningHandName: String? = null,
    val lastChat: ChatMessageDto? = null,
    val players: List<PlayerViewDto?> = listOf(null, null),
    val serverStats: ServerStatsDto? = null,
) {
    val me: PlayerViewDto?
        get() = players.getOrNull(mySeat)

    val opponent: PlayerViewDto?
        get() = players.getOrNull(1 - mySeat)

    val isMyTurn: Boolean
        get() = currentTurnSeat == mySeat && street in listOf("PREFLOP", "FLOP", "TURN", "RIVER")

    val isActiveBettingStreet: Boolean
        get() = street in listOf("PREFLOP", "FLOP", "TURN", "RIVER")
}
