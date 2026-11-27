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
# Run unit tests and assemble the debug APK
./gradlew testDebugUnitTest assembleDebug

# Install and launch on a running emulator or connected device
./gradlew installDebug
adb shell am start -n com.example.headsuppoker/.MainActivity
```

## Production Deployment Guide

Deploying the project consists of hosting the stateful Node.js WebSocket server and distributing the Android client configured to connect to it over `wss://`.

### 1. Deploying the Multiplayer WebSocket Server (`server/poker_server.mjs`)

`server/poker_server.mjs` is a zero-dependency Node.js HTTP + RFC 6455 WebSocket server. It automatically binds to `process.env.PORT || 8080` and exposes a JSON health-check endpoint at `GET /status`.

> **Important (Stateful Architecture):** Active rooms and matchmaking queues are stored **in-memory** inside the Node.js process. Deploy the server as a **single stateful instance** (do not use serverless functions like Vercel/Lambda, and disable scale-to-zero or multi-instance horizontal scaling unless sticky sessions/Redis are added).

#### Option A: Container PaaS (Fly.io / Railway / Render)
1. **Dockerfile** (example):
   ```dockerfile
   FROM node:22-alpine
   WORKDIR /app
   COPY server/poker_server.mjs ./server/poker_server.mjs
   ENV PORT=8080
   EXPOSE 8080
   CMD ["node", "server/poker_server.mjs"]
   ```
2. **Configuration**:
   - **Start Command**: `node server/poker_server.mjs`
   - **Health Check Path**: `/status`
   - **Scaling**: Keep `1` persistent instance running (`min_machines_running = 1` on Fly.io).
   - **TLS**: Your host will terminate TLS automatically and provide a `wss://<your-domain>` WebSocket endpoint.

#### Option B: VPS (DigitalOcean / Hetzner / Linode)
1. Run `node server/poker_server.mjs` as a persistent service using `pm2` or `systemd`.
2. Place **Caddy** or **Nginx** in front as a reverse proxy to terminate HTTPS/TLS and forward `wss://` upgrade requests to `127.0.0.1:8080`.

---

### 2. Preparing & Distributing the Android App

1. **Update the Default Server URL**:
   - In `app/src/main/kotlin/com/example/headsuppoker/ui/poker/PokerViewModel.kt`, update `_serverUrlInput` from `ws://10.0.2.2:8080` (emulator loopback) to your deployed `wss://<your-domain>` endpoint.
2. **Harden Network Security for Release**:
   - Once using `wss://`, remove `android:usesCleartextTraffic="true"` from `app/src/main/AndroidManifest.xml` (or move it to `app/src/debug/AndroidManifest.xml` so cleartext `ws://` is only permitted in debug builds).
3. **Configure Release Signing & Application ID**:
   - In `app/build.gradle.kts`, update `applicationId` from `com.example.headsuppoker` to your own package name and enable code shrinking (`isMinifyEnabled = true`) in the `release` build type.
   - Generate a release keystore:
     ```bash
     keytool -genkey -v -keystore release.keystore -alias poker -keyalg RSA -keysize 2048 -validity 10000
     ```
4. **Build & Distribute**:
   - **Direct APK Distribution (GitHub Releases / Firebase App Distribution)**:
     ```bash
     ./gradlew assembleRelease
     ```
   - **Google Play Store (Android App Bundle)**:
     ```bash
     ./gradlew bundleRelease
     ```
     *(When submitting to Google Play Console, specify in the content rating questionnaire that the game uses free virtual chips with no real-money gambling or cash-out).*
