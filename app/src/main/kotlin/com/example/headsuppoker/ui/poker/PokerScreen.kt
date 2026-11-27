package com.example.headsuppoker.ui.poker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.headsuppoker.model.CardDto
import com.example.headsuppoker.model.OpenRoomSummaryDto
import com.example.headsuppoker.model.PlayerViewDto
import com.example.headsuppoker.model.RoomStateDto
import com.example.headsuppoker.model.ServerStatsDto
import com.example.headsuppoker.network.ConnectionState
import com.example.headsuppoker.ui.theme.HeadsUpPokerTheme

// Casino Palette
private val CasinoDarkBg = Color(0xFF0A1410)
private val FeltGreenCenter = Color(0xFF145A38)
private val FeltGreenEdge = Color(0xFF0B3520)
private val GoldAccent = Color(0xFFE5B84B)
private val GoldBright = Color(0xFFFFD86B)
private val CardRed = Color(0xFFD32F2F)
private val CardBlack = Color(0xFF1B1F23)
private val CardBackBlue = Color(0xFF1E3A5F)
private val ChipCrimson = Color(0xFFC62828)
private val ChipEmerald = Color(0xFF2E7D32)
private val SurfaceSlate = Color(0xFF13221C)

@Composable
fun PokerAppScreen(
    viewModel: PokerViewModel = viewModel(),
) {
    val roomState by viewModel.roomState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val activeServerUrl by viewModel.activeServerUrl.collectAsStateWithLifecycle()
    val usingEmbedded by viewModel.usingEmbeddedFallback.collectAsStateWithLifecycle()
    val serverStats by viewModel.serverStats.collectAsStateWithLifecycle()
    val playerName by viewModel.playerName.collectAsStateWithLifecycle()
    val roomCodeInput by viewModel.roomCodeInput.collectAsStateWithLifecycle()
    val serverUrlInput by viewModel.serverUrlInput.collectAsStateWithLifecycle()
    val startingStack by viewModel.startingStackInput.collectAsStateWithLifecycle()
    val smallBlind by viewModel.smallBlindInput.collectAsStateWithLifecycle()
    val isPublicRoom by viewModel.isPublicRoomInput.collectAsStateWithLifecycle()
    val errorBanner by viewModel.errorBanner.collectAsStateWithLifecycle()
    val isSecondPhoneSimulated by viewModel.isSecondPhoneSimulated.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CasinoDarkBg)
            .safeDrawingPadding()
    ) {
        val currentRoom = roomState
        if (currentRoom == null) {
            PokerLobbyContent(
                playerName = playerName,
                onPlayerNameChange = viewModel::updatePlayerName,
                roomCodeInput = roomCodeInput,
                onRoomCodeChange = viewModel::updateRoomCodeInput,
                serverUrlInput = serverUrlInput,
                onServerUrlChange = viewModel::updateServerUrlInput,
                activeServerUrl = activeServerUrl,
                connectionState = connectionState,
                usingEmbedded = usingEmbedded,
                serverStats = serverStats,
                startingStack = startingStack,
                onStartingStackChange = viewModel::updateStartingStack,
                smallBlind = smallBlind,
                onSmallBlindChange = viewModel::updateBlinds,
                isPublicRoom = isPublicRoom,
                onPublicRoomChange = viewModel::updatePublicRoom,
                errorBanner = errorBanner,
                onDismissError = viewModel::dismissError,
                onConnectServer = { viewModel.reconnectToServer(forceEmbedded = false) },
                onUseOnDeviceServer = { viewModel.reconnectToServer(forceEmbedded = true) },
                onCreateRoom = viewModel::createRoom,
                onJoinRoom = { viewModel.joinRoomByCode() },
                onQuickMatch = viewModel::quickMatch,
                onJoinOpenRoom = { code -> viewModel.joinRoomByCode(code) },
            )
        } else {
            PokerTableContent(
                state = currentRoom,
                myHandStrength = viewModel.evaluateMyCurrentHandStrength(currentRoom),
                isSecondPhoneSimulated = isSecondPhoneSimulated,
                activeServerUrl = activeServerUrl,
                errorBanner = errorBanner,
                onDismissError = viewModel::dismissError,
                onSpawnSecondPhone = viewModel::spawnSecondPhoneInRoom,
                onFold = viewModel::fold,
                onCheck = viewModel::check,
                onCall = viewModel::call,
                onRaiseTo = viewModel::raiseTo,
                onAllIn = viewModel::allIn,
                onNextHand = viewModel::nextHand,
                onRematch = viewModel::rematch,
                onSendEmoji = viewModel::sendQuickEmoji,
                onLeaveRoom = viewModel::leaveRoom,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PokerLobbyContent(
    playerName: String,
    onPlayerNameChange: (String) -> Unit,
    roomCodeInput: String,
    onRoomCodeChange: (String) -> Unit,
    serverUrlInput: String,
    onServerUrlChange: (String) -> Unit,
    activeServerUrl: String,
    connectionState: ConnectionState,
    usingEmbedded: Boolean,
    serverStats: ServerStatsDto,
    startingStack: Int,
    onStartingStackChange: (Int) -> Unit,
    smallBlind: Int,
    onSmallBlindChange: (Int) -> Unit,
    isPublicRoom: Boolean,
    onPublicRoomChange: (Boolean) -> Unit,
    errorBanner: String?,
    onDismissError: () -> Unit,
    onConnectServer: () -> Unit,
    onUseOnDeviceServer: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onQuickMatch: () -> Unit,
    onJoinOpenRoom: (String) -> Unit,
) {
    var showServerSettings by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "♠ ♥ HEADS-UP POKER ♦ ♣",
                        color = GoldBright,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                    )
                }
                Text(
                    text = "2-Player Texas Hold'em • Multi-Room Code Lobby",
                    color = Color(0xFFB5C9BE),
                    fontSize = 13.sp,
                )
            }
            IconButton(
                onClick = { showServerSettings = !showServerSettings },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(SurfaceSlate)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Server Settings",
                    tint = GoldAccent,
                )
            }
        }

        // Live Multiplayer Server Capacity Pill
        Surface(
            color = SurfaceSlate,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                when (connectionState) {
                                    ConnectionState.CONNECTED -> Color(0xFF4CAF50)
                                    ConnectionState.CONNECTING -> Color(0xFFFFC107)
                                    ConnectionState.DISCONNECTED -> Color(0xFFE53935)
                                }
                            )
                    )
                    Column {
                        Text(
                            text = when {
                                connectionState == ConnectionState.CONNECTED && usingEmbedded ->
                                    "On-Device Room Server ($activeServerUrl)"
                                connectionState == ConnectionState.CONNECTED ->
                                    "Multiplayer Server ($activeServerUrl)"
                                connectionState == ConnectionState.CONNECTING ->
                                    "Connecting to $activeServerUrl..."
                                else -> "Disconnected"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${serverStats.activeRooms} Active Rooms • ${serverStats.onlinePlayers} Players Online",
                            color = GoldAccent,
                            fontSize = 11.sp,
                        )
                    }
                }
                TextButton(onClick = { showServerSettings = !showServerSettings }) {
                    Text(
                        text = if (showServerSettings) "Hide" else "Server",
                        color = GoldBright,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        // Expandable Server URL Configuration (for connecting two physical phones over LAN/Cloud)
        AnimatedVisibility(visible = showServerSettings) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Multiplayer Room Server Endpoint",
                        color = GoldBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    Text(
                        text = "Emulators use ws://10.0.2.2:8080 to share the host computer's room server. For physical phones on Wi-Fi, enter your host IP (e.g. ws://192.168.1.x:8080) or host directly on one phone.",
                        color = Color(0xFFB5C9BE),
                        fontSize = 12.sp,
                    )
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = onServerUrlChange,
                        label = { Text("WebSocket Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = pokerTextFieldColors(),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = onConnectServer,
                            colors = ButtonDefaults.buttonColors(containerColor = ChipEmerald),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Connect Shared", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onUseOnDeviceServer,
                            border = BorderStroke(1.dp, GoldAccent),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Lan, contentDescription = null, tint = GoldBright, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Host On Phone", color = GoldBright, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Error Banner if any
        if (errorBanner != null) {
            Surface(
                color = Color(0xFF5D1212),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFFFF6B6B)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = errorBanner,
                        color = Color.White,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismissError) {
                        Text("OK", color = GoldBright, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Player Name Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Your Poker Alias",
                    color = GoldBright,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = playerName,
                    onValueChange = onPlayerNameChange,
                    placeholder = { Text("Enter player name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("player_name_input"),
                    colors = pokerTextFieldColors(),
                )
            }
        }

        // JOIN ROOM WITH CODE Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            border = BorderStroke(1.5.dp, GoldAccent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Join Room by Code",
                    color = GoldBright,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "Have a 6-character code from the other phone? Enter it below to join the same table.",
                    color = Color(0xFFB5C9BE),
                    fontSize = 12.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = roomCodeInput,
                        onValueChange = onRoomCodeChange,
                        placeholder = { Text("e.g. PKR7X2") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("room_code_input"),
                        colors = pokerTextFieldColors(),
                    )
                    Button(
                        onClick = onJoinRoom,
                        enabled = roomCodeInput.length >= 4,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("join_room_button"),
                    ) {
                        Text(
                            text = "JOIN ROOM",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // CREATE NEW ROOM OR QUICK MATCH Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Create New 2-Player Room",
                    color = GoldBright,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )

                Text(
                    text = "Starting Chip Stack",
                    color = Color(0xFFB5C9BE),
                    fontSize = 12.sp,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(500, 1000, 2500, 5000).forEach { stackOption ->
                        FilterChip(
                            selected = startingStack == stackOption,
                            onClick = { onStartingStackChange(stackOption) },
                            label = { Text("$stackOption") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldAccent,
                                selectedLabelColor = Color.Black,
                                labelColor = Color.White,
                            ),
                        )
                    }
                }

                Text(
                    text = "Blinds (SB / BB)",
                    color = Color(0xFFB5C9BE),
                    fontSize = 12.sp,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(5, 10, 25, 50).forEach { sbOption ->
                        FilterChip(
                            selected = smallBlind == sbOption,
                            onClick = { onSmallBlindChange(sbOption) },
                            label = { Text("$sbOption / ${sbOption * 2}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GoldAccent,
                                selectedLabelColor = Color.Black,
                                labelColor = Color.White,
                            ),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "List in Public Open Lobbies",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Allows Quick Match players or anyone with the Room Code to join",
                            color = Color(0xFF9AB0A4),
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = isPublicRoom,
                        onCheckedChange = onPublicRoomChange,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = onCreateRoom,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ChipEmerald,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("create_room_button"),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("CREATE ROOM", fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = onQuickMatch,
                        border = BorderStroke(1.5.dp, GoldAccent),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("quick_match_button"),
                    ) {
                        Icon(Icons.Default.Groups, contentDescription = null, tint = GoldBright)
                        Spacer(Modifier.width(6.dp))
                        Text("QUICK MATCH", color = GoldBright, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        // Open Public Rooms List (when players are waiting in lobbies)
        if (serverStats.openRooms.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceSlate),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Open Lobbies Waiting for Opponent (${serverStats.openRooms.size})",
                        color = GoldBright,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                    serverStats.openRooms.forEach { openRoom ->
                        Surface(
                            color = Color(0xFF1B3027),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "Code: ${openRoom.roomCode} • Host: ${openRoom.hostName}",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                    )
                                    Text(
                                        text = "Blinds ${openRoom.blinds} • Stack ${openRoom.startingStack}",
                                        color = GoldAccent,
                                        fontSize = 11.sp,
                                    )
                                }
                                Button(
                                    onClick = { onJoinOpenRoom(openRoom.roomCode) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldAccent,
                                        contentColor = Color.Black,
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                ) {
                                    Text("Join", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PokerTableContent(
    state: RoomStateDto,
    myHandStrength: String?,
    isSecondPhoneSimulated: Boolean,
    activeServerUrl: String,
    errorBanner: String?,
    onDismissError: () -> Unit,
    onSpawnSecondPhone: () -> Unit,
    onFold: () -> Unit,
    onCheck: () -> Unit,
    onCall: () -> Unit,
    onRaiseTo: (Int) -> Unit,
    onAllIn: () -> Unit,
    onNextHand: () -> Unit,
    onRematch: () -> Unit,
    onSendEmoji: (String) -> Unit,
    onLeaveRoom: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val me = state.me
    val opponent = state.opponent

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top Bar: Room Code badge + Hand # + Blinds + Leave button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = SurfaceSlate,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, GoldAccent),
                modifier = Modifier.clickable {
                    clipboard.setText(AnnotatedString(state.roomCode))
                },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "ROOM: ${state.roomCode}",
                        color = GoldBright,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.testTag("active_room_code"),
                    )
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Room Code",
                        tint = GoldAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Text(
                text = if (state.handNumber > 0) {
                    "Hand #${state.handNumber} • Blinds ${state.smallBlind}/${state.bigBlind}"
                } else {
                    "Blinds ${state.smallBlind}/${state.bigBlind}"
                },
                color = Color(0xFFB5C9BE),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )

            IconButton(
                onClick = onLeaveRoom,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceSlate)
                    .testTag("leave_room_button"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Leave Room",
                    tint = Color(0xFFFF8A80),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (errorBanner != null) {
            Surface(
                color = Color(0xFF5D1212),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(errorBanner, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismissError) {
                        Text("Dismiss", color = GoldBright, fontSize = 11.sp)
                    }
                }
            }
        }

        // Opponent Seat (Top)
        PlayerSeatBanner(
            player = opponent,
            isDealer = opponent != null && state.dealerSeat == opponent.seat,
            isTurn = opponent != null && state.currentTurnSeat == opponent.seat && state.isActiveBettingStreet,
            isWinner = opponent != null && state.winnerSeats.contains(opponent.seat),
            chatBubble = state.lastChat?.takeIf { opponent != null && it.seat == opponent.seat }?.text,
            isTopSeat = true,
        )

        // Center Casino Felt Table
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(36.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(FeltGreenCenter, FeltGreenEdge)
                    )
                )
                .border(3.dp, GoldAccent.copy(alpha = 0.7f), RoundedCornerShape(36.dp))
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (state.street == "WAITING" || opponent == null) {
                WaitingLobbyOverlay(
                    roomCode = state.roomCode,
                    activeServerUrl = activeServerUrl,
                    onCopyCode = { clipboard.setText(AnnotatedString(state.roomCode)) },
                    onSpawnSecondPhone = onSpawnSecondPhone,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Street + Pot Pill
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                        ) {
                            Text(
                                text = state.street,
                                color = GoldBright,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            )
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.5.dp, GoldAccent),
                        ) {
                            Text(
                                text = "POT: ${state.pot}",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 5.dp)
                                    .testTag("pot_amount_text"),
                            )
                        }
                    }

                    // 5 Community Cards
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        for (i in 0 until 5) {
                            val card = state.communityCards.getOrNull(i)
                            if (card != null) {
                                PlayingCardView(
                                    card = card,
                                    width = 54.dp,
                                    height = 76.dp,
                                )
                            } else {
                                EmptyCardSlot(
                                    width = 54.dp,
                                    height = 76.dp,
                                )
                            }
                        }
                    }

                    // Status message / Showdown result
                    Surface(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = state.statusMessage,
                            color = if (state.winnerSeats.isNotEmpty()) GoldBright else Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }

        // My Seat (Bottom) + Hand Strength + Quick Emoji Bar
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PlayerSeatBanner(
                player = me,
                isDealer = me != null && state.dealerSeat == me.seat,
                isTurn = state.isMyTurn,
                isWinner = me != null && state.winnerSeats.contains(me.seat),
                chatBubble = state.lastChat?.takeIf { me != null && it.seat == me.seat }?.text,
                handStrengthLabel = myHandStrength,
                isTopSeat = false,
            )

            // Action Controls or Next Hand / Rematch Bar
            PokerActionControls(
                state = state,
                isSecondPhoneSimulated = isSecondPhoneSimulated,
                onFold = onFold,
                onCheck = onCheck,
                onCall = onCall,
                onRaiseTo = onRaiseTo,
                onAllIn = onAllIn,
                onNextHand = onNextHand,
                onRematch = onRematch,
                onSendEmoji = onSendEmoji,
            )
        }
    }
}

@Composable
private fun WaitingLobbyOverlay(
    roomCode: String,
    activeServerUrl: String,
    onCopyCode: () -> Unit,
    onSpawnSecondPhone: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.padding(8.dp),
    ) {
        Text(
            text = "SHARE ROOM CODE WITH PHONE 2",
            color = GoldAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )

        Surface(
            color = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, GoldBright),
            modifier = Modifier.clickable { onCopyCode() },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = roomCode,
                    color = GoldBright,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 4.sp,
                )
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy Code",
                    tint = GoldBright,
                )
            }
        }

        Text(
            text = "Open the app on the second phone and enter code $roomCode\n(Server: $activeServerUrl)",
            color = Color(0xFFD7E6DF),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )

        Button(
            onClick = onSpawnSecondPhone,
            colors = ButtonDefaults.buttonColors(
                containerColor = GoldAccent,
                contentColor = Color.Black,
            ),
            modifier = Modifier.testTag("spawn_second_phone_button"),
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Simulate 2nd Phone Joining Room",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun PlayerSeatBanner(
    player: PlayerViewDto?,
    isDealer: Boolean,
    isTurn: Boolean,
    isWinner: Boolean,
    chatBubble: String?,
    handStrengthLabel: String? = null,
    isTopSeat: Boolean,
) {
    val borderColor = when {
        isWinner -> GoldBright
        isTurn -> Color(0xFF4CAF50)
        else -> Color(0xFF2B463B)
    }

    Surface(
        color = SurfaceSlate,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (isTurn || isWinner) 2.dp else 1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (player == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Seat Open — Waiting for Player 2 to enter Room Code...",
                    color = Color(0xFF9AB0A4),
                    fontSize = 13.sp,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Player Info & Chips
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (player.connected) Color(0xFF4CAF50) else Color(0xFFE53935))
                        )
                        Text(
                            text = player.name,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isDealer) {
                            Surface(
                                color = GoldBright,
                                shape = CircleShape,
                            ) {
                                Text(
                                    text = "D",
                                    color = Color.Black,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                        if (isTurn) {
                            Surface(
                                color = Color(0xFF2E7D32),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = "ACTING",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Chips: ${player.chips}",
                            color = GoldAccent,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                        if (player.currentBet > 0) {
                            Surface(
                                color = Color(0xFF1F3B30),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, GoldAccent.copy(alpha = 0.5f)),
                            ) {
                                Text(
                                    text = "Bet: ${player.currentBet}",
                                    color = GoldBright,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }

                    val badgeText = player.bestHandName ?: handStrengthLabel ?: player.lastActionText
                    if (!badgeText.isNullOrBlank()) {
                        Text(
                            text = badgeText,
                            color = if (isWinner) GoldBright else Color(0xFFA7C4B6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (!chatBubble.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFF264538),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = "💬 $chatBubble",
                                color = Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                // Player's 2 Hole Cards
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val cards = player.holeCards
                    if (cards.isEmpty()) {
                        EmptyCardSlot(width = if (isTopSeat) 46.dp else 56.dp, height = if (isTopSeat) 64.dp else 78.dp)
                        EmptyCardSlot(width = if (isTopSeat) 46.dp else 56.dp, height = if (isTopSeat) 64.dp else 78.dp)
                    } else {
                        cards.forEach { c ->
                            PlayingCardView(
                                card = c,
                                width = if (isTopSeat) 46.dp else 56.dp,
                                height = if (isTopSeat) 64.dp else 78.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PokerActionControls(
    state: RoomStateDto,
    isSecondPhoneSimulated: Boolean,
    onFold: () -> Unit,
    onCheck: () -> Unit,
    onCall: () -> Unit,
    onRaiseTo: (Int) -> Unit,
    onAllIn: () -> Unit,
    onNextHand: () -> Unit,
    onRematch: () -> Unit,
    onSendEmoji: (String) -> Unit,
) {
    val me = state.me

    Surface(
        color = SurfaceSlate,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Quick Table Emoji Reactions Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isSecondPhoneSimulated) "Mode: 2nd Phone Connected" else "Live 2-Phone Room",
                    color = Color(0xFF9AB0A4),
                    fontSize = 11.sp,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("🔥", "😎", "🤔", "👏", "💸", "GG").forEach { emoji ->
                        Surface(
                            color = Color(0xFF1E352B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.clickable { onSendEmoji(emoji) },
                        ) {
                            Text(
                                text = emoji,
                                fontSize = 12.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            when (state.street) {
                "SHOWDOWN" -> {
                    Button(
                        onClick = onNextHand,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("next_hand_button"),
                    ) {
                        Text(
                            text = "DEAL NEXT HAND",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                    }
                }

                "MATCH_OVER" -> {
                    Button(
                        onClick = onRematch,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GoldAccent,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("rematch_button"),
                    ) {
                        Text(
                            text = "REMATCH (RESET STACKS)",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp,
                        )
                    }
                }

                "PREFLOP", "FLOP", "TURN", "RIVER" -> {
                    if (me != null) {
                        val maxTotalBet = (me.currentBet + me.chips).coerceAtLeast(state.currentBet)
                        val minRaiseTarget = minOf(maxTotalBet, maxOf(state.minRaiseTo, state.currentBet + state.bigBlind))
                        var sliderBet by remember(state.handNumber, state.street, state.currentBet, me.chips) {
                            mutableFloatStateOf(minRaiseTarget.toFloat())
                        }
                        LaunchedEffect(minRaiseTarget, maxTotalBet) {
                            sliderBet = sliderBet.coerceIn(minRaiseTarget.toFloat(), maxTotalBet.toFloat())
                        }

                        val canRaise = me.chips > state.toCall && maxTotalBet > state.currentBet
                        val targetRaiseInt = sliderBet.toInt().coerceIn(minRaiseTarget, maxTotalBet)

                        if (canRaise && state.isMyTurn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                listOf(
                                    "Min" to minRaiseTarget,
                                    "1/2 Pot" to (state.currentBet + maxOf(state.bigBlind, state.pot / 2)).coerceIn(minRaiseTarget, maxTotalBet),
                                    "Pot" to (state.currentBet + maxOf(state.bigBlind, state.pot)).coerceIn(minRaiseTarget, maxTotalBet),
                                    "All-In" to maxTotalBet,
                                ).forEach { (label, preset) ->
                                    Surface(
                                        color = if (targetRaiseInt == preset) GoldAccent else Color(0xFF1F382E),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { sliderBet = preset.toFloat() },
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (targetRaiseInt == preset) Color.Black else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 5.dp),
                                        )
                                    }
                                }
                            }

                            if (maxTotalBet > minRaiseTarget) {
                                Slider(
                                    value = sliderBet,
                                    onValueChange = { sliderBet = it },
                                    valueRange = minRaiseTarget.toFloat()..maxTotalBet.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = GoldBright,
                                        activeTrackColor = GoldAccent,
                                    ),
                                    modifier = Modifier.height(26.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // FOLD
                            Button(
                                onClick = onFold,
                                enabled = state.isMyTurn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChipCrimson,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("fold_button"),
                            ) {
                                Text("FOLD", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }

                            // CHECK or CALL
                            val checkOrCallLabel = when {
                                state.toCall == 0 -> "CHECK"
                                state.toCall >= me.chips -> "ALL-IN (${me.chips})"
                                else -> "CALL ${state.toCall}"
                            }
                            Button(
                                onClick = { if (state.toCall == 0) onCheck() else onCall() },
                                enabled = state.isMyTurn,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ChipEmerald,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier
                                    .weight(1.2f)
                                    .height(48.dp)
                                    .testTag("check_call_button"),
                            ) {
                                Text(checkOrCallLabel, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                            }

                            // RAISE / BET / ALL-IN
                            if (canRaise) {
                                val raiseLabel = when {
                                    targetRaiseInt >= maxTotalBet -> "ALL-IN $maxTotalBet"
                                    state.currentBet == 0 -> "BET $targetRaiseInt"
                                    else -> "RAISE $targetRaiseInt"
                                }
                                Button(
                                    onClick = {
                                        if (targetRaiseInt >= maxTotalBet) onAllIn()
                                        else onRaiseTo(targetRaiseInt)
                                    },
                                    enabled = state.isMyTurn,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GoldAccent,
                                        contentColor = Color.Black,
                                    ),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(48.dp)
                                        .testTag("raise_button"),
                                ) {
                                    Text(raiseLabel, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayingCardView(
    card: CardDto,
    width: Dp = 54.dp,
    height: Dp = 76.dp,
) {
    if (card.hidden || card.rank == 0) {
        // Classic Casino Patterned Card Back
        Surface(
            color = CardBackBlue,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.5.dp, Color.White),
            shadowElevation = 4.dp,
            modifier = Modifier.size(width, height),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .border(1.dp, GoldAccent.copy(alpha = 0.6f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♠♥\n♦♣",
                    color = GoldAccent.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp,
                )
            }
        }
    } else {
        val suitColor = if (card.isRed) CardRed else CardBlack
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color(0xFFD0D7DE)),
            shadowElevation = 4.dp,
            modifier = Modifier.size(width, height),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Text(
                    text = card.rankLabel,
                    color = suitColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                Text(
                    text = card.suitSymbol,
                    color = suitColor,
                    fontSize = 22.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Text(
                    text = card.rankLabel,
                    color = suitColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }
    }
}

@Composable
private fun EmptyCardSlot(
    width: Dp = 54.dp,
    height: Dp = 76.dp,
) {
    Box(
        modifier = Modifier
            .size(width, height)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
    )
}

@Composable
private fun pokerTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = GoldBright,
    unfocusedBorderColor = GoldAccent.copy(alpha = 0.5f),
    focusedLabelColor = GoldBright,
    unfocusedLabelColor = Color(0xFFB5C9BE),
    cursorColor = GoldBright,
)

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
fun PokerLobbyPreview() {
    HeadsUpPokerTheme {
        Box(modifier = Modifier.background(CasinoDarkBg)) {
            PokerLobbyContent(
                playerName = "AceHigh99",
                onPlayerNameChange = {},
                roomCodeInput = "PKR7X2",
                onRoomCodeChange = {},
                serverUrlInput = "ws://10.0.2.2:8080",
                onServerUrlChange = {},
                activeServerUrl = "ws://10.0.2.2:8080",
                connectionState = ConnectionState.CONNECTED,
                usingEmbedded = false,
                serverStats = ServerStatsDto(
                    activeRooms = 42,
                    onlinePlayers = 84,
                    openRooms = listOf(
                        OpenRoomSummaryDto("PKR7X2", "VegasShark", "10/20", 1000),
                        OpenRoomSummaryDto("HOLD99", "LuckyRiver", "25/50", 2500),
                    ),
                ),
                startingStack = 1000,
                onStartingStackChange = {},
                smallBlind = 10,
                onSmallBlindChange = {},
                isPublicRoom = true,
                onPublicRoomChange = {},
                errorBanner = null,
                onDismissError = {},
                onConnectServer = {},
                onUseOnDeviceServer = {},
                onCreateRoom = {},
                onJoinRoom = {},
                onQuickMatch = {},
                onJoinOpenRoom = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 820)
@Composable
fun PokerTablePreview() {
    HeadsUpPokerTheme {
        Box(modifier = Modifier.background(CasinoDarkBg)) {
            PokerTableContent(
                state = RoomStateDto(
                    roomCode = "PKR7X2",
                    mySeat = 0,
                    handNumber = 3,
                    dealerSeat = 0,
                    smallBlind = 10,
                    bigBlind = 20,
                    street = "FLOP",
                    pot = 120,
                    currentBet = 40,
                    minRaiseTo = 80,
                    toCall = 40,
                    currentTurnSeat = 0,
                    communityCards = listOf(
                        CardDto(14, "SPADES"),
                        CardDto(13, "HEARTS"),
                        CardDto(10, "SPADES"),
                    ),
                    statusMessage = "FLOP: AceHigh99's turn",
                    players = listOf(
                        PlayerViewDto(
                            seat = 0,
                            name = "AceHigh99",
                            chips = 940,
                            currentBet = 0,
                            holeCards = listOf(CardDto(14, "HEARTS"), CardDto(13, "SPADES")),
                            lastActionText = "Check",
                        ),
                        PlayerViewDto(
                            seat = 1,
                            name = "VegasShark",
                            chips = 900,
                            currentBet = 40,
                            holeCards = listOf(CardDto(0, "HIDDEN", true), CardDto(0, "HIDDEN", true)),
                            lastActionText = "Bet 40",
                        ),
                    ),
                ),
                myHandStrength = "Two Pair",
                isSecondPhoneSimulated = false,
                activeServerUrl = "ws://10.0.2.2:8080",
                errorBanner = null,
                onDismissError = {},
                onSpawnSecondPhone = {},
                onFold = {},
                onCheck = {},
                onCall = {},
                onRaiseTo = {},
                onAllIn = {},
                onNextHand = {},
                onRematch = {},
                onSendEmoji = {},
                onLeaveRoom = {},
            )
        }
    }
}
