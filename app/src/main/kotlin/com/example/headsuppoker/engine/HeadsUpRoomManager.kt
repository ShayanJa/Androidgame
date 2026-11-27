package com.example.headsuppoker.engine

import com.example.headsuppoker.model.CardDto
import com.example.headsuppoker.model.ChatMessageDto
import com.example.headsuppoker.model.OpenRoomSummaryDto
import com.example.headsuppoker.model.PlayerViewDto
import com.example.headsuppoker.model.RoomStateDto
import com.example.headsuppoker.model.ServerStatsDto
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Authoritative Heads-Up No-Limit Texas Hold'em Room Manager in Kotlin.
 * Powers both:
 * 1. The embedded on-device Multiplayer Room Server (so any Android phone can host rooms for other phones on LAN / Hotspot or local connections)
 * 2. Instant multi-room unit tests and simulated 2nd-phone opponent sessions.
 */
class HeadsUpRoomManager {
    private val random = SecureRandom()
    private val rooms = ConcurrentHashMap<String, InternalRoom>()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    data class InternalPlayer(
        val seat: Int,
        var playerId: String,
        var name: String,
        var chips: Int,
        var currentBet: Int = 0,
        var totalHandContribution: Int = 0,
        var holeCards: List<CardDto> = emptyList(),
        var folded: Boolean = false,
        var allIn: Boolean = false,
        var actedThisStreet: Boolean = false,
        var connected: Boolean = true,
        var lastActionText: String? = null,
        var bestHandName: String? = null,
    )

    class InternalRoom(
        val roomCode: String,
        val isPublic: Boolean,
        val startingStack: Int,
        val smallBlind: Int,
        val bigBlind: Int,
    ) {
        var handNumber: Int = 0
        var dealerSeat: Int = 0
        var street: String = "WAITING"
        var pot: Int = 0
        var currentBet: Int = 0
        var minRaiseTo: Int = bigBlind * 2
        var lastFullRaiseSize: Int = bigBlind
        var currentTurnSeat: Int = -1
        val deck: MutableList<CardDto> = mutableListOf()
        val communityCards: MutableList<CardDto> = mutableListOf()
        var statusMessage: String = "Waiting for Player 2 to join room $roomCode..."
        var winnerSeats: List<Int> = emptyList()
        var winningHandName: String? = null
        var lastChat: ChatMessageDto? = null
        val players: Array<InternalPlayer?> = arrayOfNulls(2)
    }

    fun generateRoomCode(): String {
        repeat(100) {
            val code = buildString(6) {
                repeat(6) { append(alphabet[random.nextInt(alphabet.length)]) }
            }
            if (!rooms.containsKey(code)) return code
        }
        return "PKR" + (100..999).random()
    }

    @Synchronized
    fun createRoom(
        playerName: String,
        playerId: String,
        customCode: String? = null,
        startingStack: Int = 1000,
        smallBlind: Int = 10,
        bigBlind: Int = 20,
        isPublic: Boolean = false,
    ): InternalRoom {
        val code = (customCode?.takeIf { it.isNotBlank() } ?: generateRoomCode()).uppercase()
        val cleanStack = startingStack.coerceAtLeast(100)
        val cleanSb = smallBlind.coerceAtLeast(5)
        val cleanBb = bigBlind.coerceAtLeast(cleanSb * 2)

        val room = InternalRoom(
            roomCode = code,
            isPublic = isPublic,
            startingStack = cleanStack,
            smallBlind = cleanSb,
            bigBlind = cleanBb,
        )
        room.players[0] = InternalPlayer(
            seat = 0,
            playerId = playerId,
            name = playerName.ifBlank { "Player 1" }.take(20),
            chips = cleanStack,
            lastActionText = "Host",
        )
        rooms[code] = room
        return room
    }

    @Synchronized
    fun joinRoom(
        roomCode: String,
        playerName: String,
        playerId: String,
    ): Result<Pair<InternalRoom, Int>> {
        val code = roomCode.trim().uppercase()
        val room = rooms[code]
            ?: return Result.failure(IllegalArgumentException("Room \"$code\" not found. Check the 6-character code."))

        val cleanName = playerName.ifBlank { "Player 2" }.take(20)

        // Reconnect support if playerId matches or seat disconnected with same name
        for (seat in 0..1) {
            val existing = room.players[seat]
            if (existing != null && (existing.playerId == playerId || (!existing.connected && existing.name == cleanName))) {
                existing.connected = true
                existing.playerId = playerId
                room.statusMessage = "${existing.name} reconnected!"
                return Result.success(room to seat)
            }
        }

        if (room.players[1] != null) {
            return Result.failure(IllegalStateException("Room \"$code\" is already full (2/2 players)."))
        }

        room.players[1] = InternalPlayer(
            seat = 1,
            playerId = playerId,
            name = cleanName,
            chips = room.startingStack,
            lastActionText = "Joined",
        )
        startNextHand(room)
        return Result.success(room to 1)
    }

    @Synchronized
    fun quickMatch(
        playerName: String,
        playerId: String,
        startingStack: Int = 1000,
        smallBlind: Int = 10,
        bigBlind: Int = 20,
    ): Pair<InternalRoom, Int> {
        val waitingRoom = rooms.values.firstOrNull {
            it.isPublic && it.players[0]?.connected == true && it.players[1] == null
        }
        if (waitingRoom != null) {
            val joined = joinRoom(waitingRoom.roomCode, playerName, playerId).getOrNull()
            if (joined != null) return joined
        }
        val created = createRoom(
            playerName = playerName,
            playerId = playerId,
            startingStack = startingStack,
            smallBlind = smallBlind,
            bigBlind = bigBlind,
            isPublic = true,
        )
        return created to 0
    }

    @Synchronized
    fun getRoom(roomCode: String): InternalRoom? = rooms[roomCode.trim().uppercase()]

    @Synchronized
    fun startNextHand(room: InternalRoom) {
        val p0 = room.players[0] ?: return
        val p1 = room.players[1] ?: return

        if (p0.chips <= 0 || p1.chips <= 0) {
            room.street = "MATCH_OVER"
            val winner = if (p0.chips > 0) p0 else p1
            room.winnerSeats = listOf(winner.seat)
            room.statusMessage = "${winner.name} wins the match! Tap Rematch to play again."
            return
        }

        room.handNumber += 1
        room.dealerSeat = if (room.handNumber == 1) 0 else 1 - room.dealerSeat
        val sbSeat = room.dealerSeat
        val bbSeat = 1 - room.dealerSeat

        room.deck.clear()
        room.deck.addAll(createShuffledDeck())
        room.communityCards.clear()
        room.pot = 0
        room.winnerSeats = emptyList()
        room.winningHandName = null
        room.street = "PREFLOP"

        for (p in listOf(p0, p1)) {
            p.currentBet = 0
            p.totalHandContribution = 0
            p.holeCards = listOf(room.deck.removeAt(room.deck.lastIndex), room.deck.removeAt(room.deck.lastIndex))
            p.folded = false
            p.allIn = false
            p.actedThisStreet = false
            p.lastActionText = null
            p.bestHandName = null
        }

        val sbPlayer = room.players[sbSeat]!!
        val bbPlayer = room.players[bbSeat]!!

        val sbAmount = minOf(room.smallBlind, sbPlayer.chips)
        val bbAmount = minOf(room.bigBlind, bbPlayer.chips)

        postChips(room, sbPlayer, sbAmount)
        sbPlayer.lastActionText = if (sbPlayer.allIn) "SB All-In ($sbAmount)" else "SB $sbAmount"

        postChips(room, bbPlayer, bbAmount)
        bbPlayer.lastActionText = if (bbPlayer.allIn) "BB All-In ($bbAmount)" else "BB $bbAmount"

        room.currentBet = maxOf(sbPlayer.currentBet, bbPlayer.currentBet)
        room.lastFullRaiseSize = room.bigBlind
        room.minRaiseTo = room.currentBet + room.lastFullRaiseSize

        if ((sbPlayer.allIn && bbPlayer.currentBet >= sbPlayer.currentBet) || (sbPlayer.allIn && bbPlayer.allIn)) {
            refundUncalledExcess(room)
            runoutToShowdown(room)
            return
        }

        room.currentTurnSeat = sbSeat
        room.statusMessage = "Hand #${room.handNumber}: ${sbPlayer.name}'s turn (Dealer / SB)"
    }

    @Synchronized
    fun handleAction(room: InternalRoom, seat: Int, action: String, amount: Int = 0): Result<Unit> {
        if (room.street !in listOf("PREFLOP", "FLOP", "TURN", "RIVER")) {
            return Result.failure(IllegalStateException("Hand is not in an active betting street."))
        }
        if (room.currentTurnSeat != seat) {
            return Result.failure(IllegalStateException("It is not your turn."))
        }

        val player = room.players[seat] ?: return Result.failure(IllegalStateException("Player not seated."))
        val opponent = room.players[1 - seat] ?: return Result.failure(IllegalStateException("Opponent not seated."))
        val toCall = (room.currentBet - player.currentBet).coerceAtLeast(0)

        when (action.uppercase()) {
            "FOLD" -> {
                player.folded = true
                player.actedThisStreet = true
                player.lastActionText = "Fold"
                opponent.chips += room.pot
                room.street = "SHOWDOWN"
                room.currentTurnSeat = -1
                room.winnerSeats = listOf(opponent.seat)
                room.winningHandName = "Opponent Folded"
                room.statusMessage = "${player.name} folded. ${opponent.name} wins ${room.pot} chips!"
                checkMatchOver(room)
            }
            "CHECK" -> {
                if (toCall > 0) {
                    return Result.failure(IllegalArgumentException("Cannot check; $toCall to call."))
                }
                player.actedThisStreet = true
                player.lastActionText = "Check"
                advanceTurnOrStreet(room, seat)
            }
            "CALL" -> {
                if (toCall == 0) {
                    player.actedThisStreet = true
                    player.lastActionText = "Check"
                    advanceTurnOrStreet(room, seat)
                } else {
                    val added = postChips(room, player, toCall)
                    player.actedThisStreet = true
                    player.lastActionText = if (player.allIn) "All-In Call ($added)" else "Call $added"
                    advanceTurnOrStreet(room, seat)
                }
            }
            "RAISE", "ALL_IN" -> {
                val maxTotalBet = player.currentBet + player.chips
                var targetTotalBet = if (action.equals("ALL_IN", ignoreCase = true)) {
                    maxTotalBet
                } else {
                    amount.coerceIn(room.currentBet, maxTotalBet)
                }
                if (targetTotalBet < room.minRaiseTo && targetTotalBet < maxTotalBet) {
                    targetTotalBet = minOf(maxTotalBet, room.minRaiseTo)
                }
                val needed = targetTotalBet - player.currentBet
                if (needed <= toCall && targetTotalBet < maxTotalBet) {
                    return Result.failure(IllegalArgumentException("Raise must be at least ${room.minRaiseTo}."))
                }

                val previousBetLevel = room.currentBet
                postChips(room, player, needed)
                val newBetLevel = player.currentBet
                val raiseDelta = newBetLevel - previousBetLevel

                if (newBetLevel > previousBetLevel) {
                    room.currentBet = newBetLevel
                    if (raiseDelta >= room.lastFullRaiseSize) {
                        room.lastFullRaiseSize = raiseDelta
                    }
                    room.minRaiseTo = room.currentBet + room.lastFullRaiseSize
                    opponent.actedThisStreet = false
                }

                player.actedThisStreet = true
                player.lastActionText = when {
                    player.allIn -> "All-In ($newBetLevel)"
                    previousBetLevel == 0 -> "Bet $newBetLevel"
                    else -> "Raise to $newBetLevel"
                }
                advanceTurnOrStreet(room, seat)
            }
            else -> return Result.failure(IllegalArgumentException("Unknown action: $action"))
        }
        return Result.success(Unit)
    }

    @Synchronized
    fun rematch(room: InternalRoom) {
        val p0 = room.players[0] ?: return
        val p1 = room.players[1] ?: return
        p0.chips = room.startingStack
        p1.chips = room.startingStack
        room.handNumber = 0
        startNextHand(room)
    }

    @Synchronized
    fun addChat(room: InternalRoom, seat: Int, text: String) {
        val sender = room.players.getOrNull(seat) ?: return
        room.lastChat = ChatMessageDto(
            seat = seat,
            senderName = sender.name,
            text = text.take(60),
            timestamp = System.currentTimeMillis(),
        )
    }

    @Synchronized
    fun serializeForSeat(room: InternalRoom, viewerSeat: Int, onlinePlayersCount: Int = 2): RoomStateDto {
        val p0 = room.players[0]
        val p1 = room.players[1]
        val revealAll = (room.street == "SHOWDOWN" || room.street == "MATCH_OVER") &&
            p0 != null && p1 != null && !p0.folded && !p1.folded

        val playerViews = room.players.mapIndexed { idx, p ->
            if (p == null) null
            else {
                val canSee = idx == viewerSeat || revealAll
                PlayerViewDto(
                    seat = p.seat,
                    name = p.name,
                    chips = p.chips,
                    currentBet = p.currentBet,
                    holeCards = p.holeCards.map { card ->
                        if (canSee) card.copy(hidden = false)
                        else CardDto(rank = 0, suit = "HIDDEN", hidden = true)
                    },
                    folded = p.folded,
                    allIn = p.allIn,
                    connected = p.connected,
                    lastActionText = p.lastActionText,
                    bestHandName = if (canSee) p.bestHandName else null,
                )
            }
        }

        val me = room.players.getOrNull(viewerSeat)
        val toCall = if (me != null) (room.currentBet - me.currentBet).coerceAtLeast(0) else 0
        val maxBet = if (me != null) me.currentBet + me.chips else 0

        return RoomStateDto(
            roomCode = room.roomCode,
            isPublic = room.isPublic,
            mySeat = viewerSeat,
            handNumber = room.handNumber,
            dealerSeat = room.dealerSeat,
            smallBlind = room.smallBlind,
            bigBlind = room.bigBlind,
            startingStack = room.startingStack,
            street = room.street,
            pot = room.pot,
            currentBet = room.currentBet,
            minRaiseTo = minOf(maxBet, maxOf(room.minRaiseTo, room.currentBet + room.bigBlind)),
            toCall = toCall,
            currentTurnSeat = room.currentTurnSeat,
            communityCards = room.communityCards.toList(),
            statusMessage = room.statusMessage,
            winnerSeats = room.winnerSeats,
            winningHandName = room.winningHandName,
            lastChat = room.lastChat,
            players = playerViews,
            serverStats = getServerStats(onlinePlayersCount),
        )
    }

    @Synchronized
    fun getServerStats(onlinePlayersCount: Int): ServerStatsDto {
        val openPublic = rooms.values
            .filter { it.isPublic && it.players[0]?.connected == true && it.players[1] == null }
            .take(20)
            .map {
                OpenRoomSummaryDto(
                    roomCode = it.roomCode,
                    hostName = it.players[0]?.name ?: "Host",
                    blinds = "${it.smallBlind}/${it.bigBlind}",
                    startingStack = it.startingStack,
                )
            }
        return ServerStatsDto(
            activeRooms = rooms.size,
            onlinePlayers = onlinePlayersCount,
            openRooms = openPublic,
        )
    }

    private fun postChips(room: InternalRoom, player: InternalPlayer, amount: Int): Int {
        val actual = amount.coerceIn(0, player.chips)
        player.chips -= actual
        player.currentBet += actual
        player.totalHandContribution += actual
        room.pot += actual
        if (player.chips == 0) {
            player.allIn = true
        }
        return actual
    }

    private fun advanceTurnOrStreet(room: InternalRoom, lastActorSeat: Int) {
        val p0 = room.players[0] ?: return
        val p1 = room.players[1] ?: return
        val other = room.players[1 - lastActorSeat] ?: return

        if (p0.allIn || p1.allIn) {
            val bothDone = (p0.allIn && p1.allIn) ||
                (p0.allIn && p1.currentBet >= p0.currentBet) ||
                (p1.allIn && p0.currentBet >= p1.currentBet)
            if (bothDone) {
                refundUncalledExcess(room)
                runoutToShowdown(room)
                return
            }
        }

        if (p0.actedThisStreet && p1.actedThisStreet && p0.currentBet == p1.currentBet) {
            moveToNextStreet(room)
        } else {
            room.currentTurnSeat = other.seat
            room.statusMessage = "${other.name}'s turn"
        }
    }

    private fun refundUncalledExcess(room: InternalRoom) {
        val p0 = room.players[0] ?: return
        val p1 = room.players[1] ?: return
        if (p0.currentBet > p1.currentBet) {
            val excess = p0.currentBet - p1.currentBet
            p0.chips += excess
            p0.currentBet -= excess
            p0.totalHandContribution -= excess
            room.pot -= excess
        } else if (p1.currentBet > p0.currentBet) {
            val excess = p1.currentBet - p0.currentBet
            p1.chips += excess
            p1.currentBet -= excess
            p1.totalHandContribution -= excess
            room.pot -= excess
        }
    }

    private fun moveToNextStreet(room: InternalRoom) {
        room.players.filterNotNull().forEach {
            it.currentBet = 0
            it.actedThisStreet = false
        }
        room.currentBet = 0
        room.lastFullRaiseSize = room.bigBlind
        room.minRaiseTo = room.bigBlind

        when (room.street) {
            "PREFLOP" -> {
                room.street = "FLOP"
                repeat(3) { room.communityCards.add(room.deck.removeAt(room.deck.lastIndex)) }
            }
            "FLOP" -> {
                room.street = "TURN"
                room.communityCards.add(room.deck.removeAt(room.deck.lastIndex))
            }
            "TURN" -> {
                room.street = "RIVER"
                room.communityCards.add(room.deck.removeAt(room.deck.lastIndex))
            }
            "RIVER" -> {
                resolveShowdown(room)
                return
            }
        }

        val firstPostflopSeat = 1 - room.dealerSeat
        room.currentTurnSeat = firstPostflopSeat
        room.statusMessage = "${room.street}: ${room.players[firstPostflopSeat]?.name}'s turn"
    }

    private fun runoutToShowdown(room: InternalRoom) {
        while (room.communityCards.size < 5) {
            room.communityCards.add(room.deck.removeAt(room.deck.lastIndex))
        }
        resolveShowdown(room)
    }

    private fun resolveShowdown(room: InternalRoom) {
        room.street = "SHOWDOWN"
        room.currentTurnSeat = -1
        val p0 = room.players[0] ?: return
        val p1 = room.players[1] ?: return

        val eval0 = HandEvaluator.evaluateBestHand(p0.holeCards + room.communityCards)
        val eval1 = HandEvaluator.evaluateBestHand(p1.holeCards + room.communityCards)
        p0.bestHandName = eval0.categoryName
        p1.bestHandName = eval1.categoryName

        when {
            eval0.score > eval1.score -> {
                p0.chips += room.pot
                room.winnerSeats = listOf(0)
                room.winningHandName = eval0.categoryName
                room.statusMessage = "${p0.name} wins ${room.pot} chips with ${eval0.categoryName}!"
            }
            eval1.score > eval0.score -> {
                p1.chips += room.pot
                room.winnerSeats = listOf(1)
                room.winningHandName = eval1.categoryName
                room.statusMessage = "${p1.name} wins ${room.pot} chips with ${eval1.categoryName}!"
            }
            else -> {
                val half = room.pot / 2
                val rem = room.pot - half * 2
                p0.chips += half + if (room.dealerSeat == 1) rem else 0
                p1.chips += half + if (room.dealerSeat == 0) rem else 0
                room.winnerSeats = listOf(0, 1)
                room.winningHandName = "Split Pot (${eval0.categoryName})"
                room.statusMessage = "Split pot (${room.pot} chips) — both have ${eval0.categoryName}!"
            }
        }

        checkMatchOver(room)
    }

    private fun checkMatchOver(room: InternalRoom) {
        val p0 = room.players[0] ?: return
        val p1 = room.players[1] ?: return
        if (p0.chips <= 0 || p1.chips <= 0) {
            room.street = "MATCH_OVER"
            val champ = if (p0.chips > 0) p0 else p1
            room.statusMessage = "${room.statusMessage} ${champ.name} wins the match!"
        }
    }

    private fun createShuffledDeck(): List<CardDto> {
        val suits = listOf("SPADES", "HEARTS", "DIAMONDS", "CLUBS")
        val deck = ArrayList<CardDto>(52)
        for (s in suits) {
            for (r in 2..14) {
                deck.add(CardDto(rank = r, suit = s, hidden = false))
            }
        }
        for (i in deck.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = deck[i]
            deck[i] = deck[j]
            deck[j] = tmp
        }
        return deck
    }
}
