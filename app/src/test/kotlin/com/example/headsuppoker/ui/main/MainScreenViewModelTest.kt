package com.example.headsuppoker.ui.main

import com.example.headsuppoker.engine.HandEvaluator
import com.example.headsuppoker.engine.HeadsUpRoomManager
import com.example.headsuppoker.model.CardDto
import com.example.headsuppoker.model.RoomStateDto
import com.example.headsuppoker.network.EmbeddedRoomServer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun handEvaluator_ranksAllPokerHandsCorrectly() {
        val royalFlush = HandEvaluator.evaluateBestHand(
            listOf(
                CardDto(14, "SPADES"),
                CardDto(13, "SPADES"),
                CardDto(12, "SPADES"),
                CardDto(11, "SPADES"),
                CardDto(10, "SPADES"),
                CardDto(2, "HEARTS"),
                CardDto(3, "CLUBS"),
            )
        )
        assertEquals("Royal Flush", royalFlush.categoryName)

        val wheelStraight = HandEvaluator.evaluateBestHand(
            listOf(
                CardDto(14, "SPADES"),
                CardDto(2, "HEARTS"),
                CardDto(3, "DIAMONDS"),
                CardDto(4, "CLUBS"),
                CardDto(5, "SPADES"),
                CardDto(9, "HEARTS"),
                CardDto(11, "CLUBS"),
            )
        )
        assertEquals("Straight", wheelStraight.categoryName)

        val fullHouse = HandEvaluator.evaluateBestHand(
            listOf(
                CardDto(13, "SPADES"),
                CardDto(13, "HEARTS"),
                CardDto(13, "DIAMONDS"),
                CardDto(9, "CLUBS"),
                CardDto(9, "SPADES"),
                CardDto(2, "HEARTS"),
                CardDto(4, "CLUBS"),
            )
        )
        assertEquals("Full House", fullHouse.categoryName)
        assertTrue(royalFlush > fullHouse)
        assertTrue(fullHouse > wheelStraight)
    }

    @Test
    fun roomManager_supportsManyConcurrentTwoPlayerRoomsAndMasksHoleCards() {
        val manager = HeadsUpRoomManager()

        // Create 200 concurrent 2-player rooms and verify unique 6-char room codes and gameplay
        val codes = mutableSetOf<String>()
        for (i in 1..200) {
            val room = manager.createRoom(
                playerName = "Host_$i",
                playerId = "host_id_$i",
                startingStack = 1000,
                smallBlind = 10,
                bigBlind = 20,
            )
            assertEquals(6, room.roomCode.length)
            assertTrue(codes.add(room.roomCode))

            // Second phone joins with the room code
            val joinResult = manager.joinRoom(
                roomCode = room.roomCode,
                playerName = "Joiner_$i",
                playerId = "joiner_id_$i",
            )
            assertTrue(joinResult.isSuccess)
            assertEquals("PREFLOP", room.street)
            assertEquals(30, room.pot) // 10 SB + 20 BB

            // Verify anti-cheat hole card masking before showdown
            val seat0View = manager.serializeForSeat(room, 0)
            val seat1View = manager.serializeForSeat(room, 1)
            assertFalse(seat0View.players[0]!!.holeCards[0].hidden)
            assertTrue(seat0View.players[1]!!.holeCards[0].hidden)
            assertFalse(seat1View.players[1]!!.holeCards[0].hidden)
            assertTrue(seat1View.players[0]!!.holeCards[0].hidden)

            // Play a full hand to showdown:
            // Preflop: Seat 0 (Dealer/SB) calls 10 -> Seat 1 (BB) checks
            assertTrue(manager.handleAction(room, 0, "CALL").isSuccess)
            assertTrue(manager.handleAction(room, 1, "CHECK").isSuccess)
            assertEquals("FLOP", room.street)
            assertEquals(3, room.communityCards.size)

            // Flop: Seat 1 acts first post-flop -> checks, Seat 0 bets 40, Seat 1 calls 40
            assertTrue(manager.handleAction(room, 1, "CHECK").isSuccess)
            assertTrue(manager.handleAction(room, 0, "RAISE", 40).isSuccess)
            assertTrue(manager.handleAction(room, 1, "CALL").isSuccess)
            assertEquals("TURN", room.street)
            assertEquals(4, room.communityCards.size)

            // Turn: Check / Check
            assertTrue(manager.handleAction(room, 1, "CHECK").isSuccess)
            assertTrue(manager.handleAction(room, 0, "CHECK").isSuccess)
            assertEquals("RIVER", room.street)
            assertEquals(5, room.communityCards.size)

            // River: Check / Check -> Showdown
            assertTrue(manager.handleAction(room, 1, "CHECK").isSuccess)
            assertTrue(manager.handleAction(room, 0, "CHECK").isSuccess)
            assertEquals("SHOWDOWN", room.street)

            val showdownView0 = manager.serializeForSeat(room, 0)
            assertFalse(showdownView0.players[1]!!.holeCards[0].hidden)
            assertEquals(2000, room.players[0]!!.chips + room.players[1]!!.chips)
        }
    }

    @Test
    fun embeddedRoomServer_twoPhonesJoinSameRoomByCodeOverWebSocket() {
        val server = EmbeddedRoomServer()
        val port = server.start(0)
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient()

        val roomCreatedLatch = CountDownLatch(1)
        val handStartedLatch = CountDownLatch(2)
        var createdRoomCode = ""
        var phone1PreflopState: RoomStateDto? = null
        var phone2PreflopState: RoomStateDto? = null

        val phone1Ws = client.newWebSocket(
            Request.Builder().url("ws://127.0.0.1:$port").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"CREATE_ROOM","playerName":"Phone1","startingStack":1000,"smallBlind":10,"bigBlind":20}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = json.parseToJsonElement(text).jsonObject
                    if (obj["type"]?.jsonPrimitive?.content == "ROOM_STATE") {
                        val state = json.decodeFromString(RoomStateDto.serializer(), text)
                        if (state.street == "WAITING") {
                            createdRoomCode = state.roomCode
                            roomCreatedLatch.countDown()
                        } else if (state.street == "PREFLOP") {
                            phone1PreflopState = state
                            handStartedLatch.countDown()
                        }
                    }
                }
            }
        )

        assertTrue(roomCreatedLatch.await(5, TimeUnit.SECONDS))
        assertEquals(6, createdRoomCode.length)

        val phone2Ws = client.newWebSocket(
            Request.Builder().url("ws://127.0.0.1:$port").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"JOIN_ROOM","roomCode":"$createdRoomCode","playerName":"Phone2"}""")
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val obj = json.parseToJsonElement(text).jsonObject
                    if (obj["type"]?.jsonPrimitive?.content == "ROOM_STATE") {
                        val state = json.decodeFromString(RoomStateDto.serializer(), text)
                        if (state.street == "PREFLOP") {
                            phone2PreflopState = state
                            handStartedLatch.countDown()
                        }
                    }
                }
            }
        )

        assertTrue(handStartedLatch.await(5, TimeUnit.SECONDS))
        assertNotNull(phone1PreflopState)
        assertNotNull(phone2PreflopState)
        assertEquals(createdRoomCode, phone1PreflopState!!.roomCode)
        assertEquals(createdRoomCode, phone2PreflopState!!.roomCode)
        assertEquals(0, phone1PreflopState!!.mySeat)
        assertEquals(1, phone2PreflopState!!.mySeat)

        phone1Ws.close(1000, null)
        phone2Ws.close(1000, null)
        server.stop()
    }
}
