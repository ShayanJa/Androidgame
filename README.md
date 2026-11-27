# Heads-Up Poker (2-Player Android Multiplayer Texas Hold'em)

A full-stack multiplayer **Heads-Up (2-Player) No-Limit Texas Hold'em Poker** game for Android built with Jetpack Compose, OkHttp WebSockets, and a scalable multi-room WebSocket server.

## Key Features
- **6-Character Room Code Lobby**: One phone taps **Create Room** to generate a unique 6-character alphanumeric room code (e.g. `PKR7X2`), and the second phone enters that code in **Join Room by Code** to join the exact same table immediately.
- **High-Concurrency Multi-Room Architecture**: Thousands of independent 2-player rooms can run simultaneously on the room server (`server/poker_server.mjs`), plus a **Quick Match** queue and live **Open Public Lobbies** list.
- **Anti-Cheat Hole-Card Masking**: The server never transmits an opponent's hole cards over the network until **Showdown** (when neither player folded).
- **Dual Server Support (Cloud/LAN Server + On-Device Embedded Server)**:
  - **Shared Multiplayer Server (`server/poker_server.mjs`)**: Zero-dependency Node.js RFC 6455 WebSocket server on port `8080` (`ws://10.0.2.2:8080` from Android Emulators, or `ws://<LAN-IP>:8080` from physical Android phones).
  - **Embedded On-Device Room Server (`EmbeddedRoomServer.kt`)**: Built directly into the Android app so a phone can also host a room server locally on Wi-Fi/Hotspot without an external server.
  - **Simulated 2nd Phone Client**: In any waiting lobby, tap **"Simulate 2nd Phone Joining Room"** to spawn a real second WebSocket client that joins via the room code so you can test 2-phone multiplayer gameplay even on a single device.

## Running the Scalable Room Server

```bash
# Start the multiplayer room server on port 8080
node server/poker_server.mjs

# Run the automated 25-room (50 concurrent phones) stress & gameplay test
node server/test_server.mjs
```

## Building & Testing the Android App

```bash
./gradlew testDebugUnitTest assembleDebug
```
