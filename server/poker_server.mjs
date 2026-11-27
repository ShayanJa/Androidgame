#!/usr/bin/env node
/**
 * Scalable Heads-Up (2-Player) Texas Hold'em Multiplayer Room Server
 * Zero-dependency Node.js RFC 6455 WebSocket + HTTP server.
 * Supports thousands of concurrent 2-player rooms by 6-character Room Code,
 * Quick Matchmaking queue, reconnection via playerId token, anti-cheat card masking,
 * and full Heads-Up No-Limit Texas Hold'em rules.
 */

import http from 'node:http';
import crypto from 'node:crypto';

const PORT = Number(process.env.PORT || 8080);

// --- Poker Constants & Hand Evaluator ---
const SUITS = ['SPADES', 'HEARTS', 'DIAMONDS', 'CLUBS'];
const RANKS = [2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14]; // 11=J, 12=Q, 13=K, 14=A
const HAND_CATEGORY_NAMES = [
  'High Card',
  'One Pair',
  'Two Pair',
  'Three of a Kind',
  'Straight',
  'Flush',
  'Full House',
  'Four of a Kind',
  'Straight Flush',
  'Royal Flush',
];

function createDeck() {
  const deck = [];
  for (const suit of SUITS) {
    for (const rank of RANKS) {
      deck.push({ rank, suit, hidden: false });
    }
  }
  // Cryptographically strong Fisher-Yates shuffle
  for (let i = deck.length - 1; i > 0; i--) {
    const j = crypto.randomInt(0, i + 1);
    const tmp = deck[i];
    deck[i] = deck[j];
    deck[j] = tmp;
  }
  return deck;
}

function evaluate5CardHand(cards) {
  const ranks = cards.map((c) => c.rank).sort((a, b) => b - a);
  const suits = cards.map((c) => c.suit);
  const isFlush = suits.every((s) => s === suits[0]);

  const uniqueRanks = [...new Set(ranks)];
  let isStraight = false;
  let straightHigh = 0;
  if (uniqueRanks.length === 5) {
    if (uniqueRanks[0] - uniqueRanks[4] === 4) {
      isStraight = true;
      straightHigh = uniqueRanks[0];
    } else if (
      uniqueRanks[0] === 14 &&
      uniqueRanks[1] === 5 &&
      uniqueRanks[2] === 4 &&
      uniqueRanks[3] === 3 &&
      uniqueRanks[4] === 2
    ) {
      isStraight = true;
      straightHigh = 5; // 5-high wheel straight (A-2-3-4-5)
    }
  }

  const counts = new Map();
  for (const r of ranks) {
    counts.set(r, (counts.get(r) || 0) + 1);
  }
  // Sort by frequency desc, then rank desc
  const groups = [...counts.entries()].sort((a, b) => {
    if (b[1] !== a[1]) return b[1] - a[1];
    return b[0] - a[0];
  });

  let category = 0;
  let tiebreakers = [];

  if (isFlush && isStraight) {
    category = straightHigh === 14 ? 9 : 8;
    tiebreakers = [straightHigh];
  } else if (groups[0][1] === 4) {
    category = 7;
    tiebreakers = [groups[0][0], groups[1][0]];
  } else if (groups[0][1] === 3 && groups[1][1] === 2) {
    category = 6;
    tiebreakers = [groups[0][0], groups[1][0]];
  } else if (isFlush) {
    category = 5;
    tiebreakers = ranks;
  } else if (isStraight) {
    category = 4;
    tiebreakers = [straightHigh];
  } else if (groups[0][1] === 3) {
    category = 3;
    tiebreakers = [groups[0][0], groups[1][0], groups[2][0]];
  } else if (groups[0][1] === 2 && groups[1][1] === 2) {
    category = 2;
    tiebreakers = [groups[0][0], groups[1][0], groups[2][0]];
  } else if (groups[0][1] === 2) {
    category = 1;
    tiebreakers = [groups[0][0], groups[1][0], groups[2][0], groups[3][0]];
  } else {
    category = 0;
    tiebreakers = ranks;
  }

  let score = category;
  for (let i = 0; i < 5; i++) {
    score = score * 16 + (tiebreakers[i] || 0);
  }

  return {
    category,
    categoryName: HAND_CATEGORY_NAMES[category],
    score,
  };
}

export function evaluateBestHand(sevenCards) {
  if (sevenCards.length < 5) {
    return { category: 0, categoryName: 'High Card', score: 0 };
  }
  let best = null;
  const n = sevenCards.length;
  for (let a = 0; a < n - 4; a++) {
    for (let b = a + 1; b < n - 3; b++) {
      for (let c = b + 1; c < n - 2; c++) {
        for (let d = c + 1; d < n - 1; d++) {
          for (let e = d + 1; e < n; e++) {
            const res = evaluate5CardHand([
              sevenCards[a],
              sevenCards[b],
              sevenCards[c],
              sevenCards[d],
              sevenCards[e],
            ]);
            if (!best || res.score > best.score) {
              best = res;
            }
          }
        }
      }
    }
  }
  return best;
}

// --- Room Storage (Supports Thousands of Concurrent Rooms) ---
const rooms = new Map(); // roomCode -> RoomState
const clients = new Set(); // active WebSocket connections
const ROOM_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function generateRoomCode() {
  for (let attempt = 0; attempt < 100; attempt++) {
    let code = '';
    for (let i = 0; i < 6; i++) {
      code += ROOM_ALPHABET[crypto.randomInt(0, ROOM_ALPHABET.length)];
    }
    if (!rooms.has(code)) return code;
  }
  return crypto.randomBytes(3).toString('hex').toUpperCase();
}

function createRoom({ roomCode, hostName, hostId, startingStack = 1000, smallBlind = 10, bigBlind = 20, isPublic = false }) {
  const code = (roomCode || generateRoomCode()).toUpperCase();
  const room = {
    roomCode: code,
    isPublic: Boolean(isPublic),
    createdAt: Date.now(),
    updatedAt: Date.now(),
    startingStack: Math.max(100, Number(startingStack) || 1000),
    smallBlind: Math.max(5, Number(smallBlind) || 10),
    bigBlind: Math.max(10, Number(bigBlind) || 20),
    handNumber: 0,
    dealerSeat: 0,
    street: 'WAITING', // WAITING | PREFLOP | FLOP | TURN | RIVER | SHOWDOWN | MATCH_OVER
    pot: 0,
    currentBet: 0,
    minRaiseTo: 0,
    lastFullRaiseSize: 20,
    currentTurnSeat: -1,
    deck: [],
    communityCards: [],
    statusMessage: `Waiting for Player 2 to join room ${code}...`,
    winnerSeats: [],
    winningHandName: null,
    lastChat: null,
    players: [
      {
        seat: 0,
        playerId: hostId,
        name: hostName || 'Player 1',
        chips: Math.max(100, Number(startingStack) || 1000),
        currentBet: 0,
        totalHandContribution: 0,
        holeCards: [],
        folded: false,
        allIn: false,
        actedThisStreet: false,
        connected: true,
        lastActionText: 'Host',
        bestHandName: null,
      },
      null,
    ],
    sockets: [null, null],
  };
  rooms.set(code, room);
  return room;
}

// --- Heads-Up Texas Hold'em State Machine ---
function startNextHand(room) {
  const p0 = room.players[0];
  const p1 = room.players[1];
  if (!p0 || !p1) return;

  if (p0.chips <= 0 || p1.chips <= 0) {
    room.street = 'MATCH_OVER';
    const champ = p0.chips > 0 ? p0 : p1;
    room.statusMessage = `${champ.name} wins the match! Tap Rematch to play again.`;
    room.winnerSeats = [champ.seat];
    return;
  }

  room.handNumber += 1;
  room.updatedAt = Date.now();
  room.dealerSeat = room.handNumber === 1 ? 0 : 1 - room.dealerSeat;
  const sbSeat = room.dealerSeat; // In Heads-Up poker, Dealer = Small Blind and acts first preflop, second postflop
  const bbSeat = 1 - room.dealerSeat;

  room.deck = createDeck();
  room.communityCards = [];
  room.pot = 0;
  room.winnerSeats = [];
  room.winningHandName = null;
  room.street = 'PREFLOP';

  for (const p of room.players) {
    p.currentBet = 0;
    p.totalHandContribution = 0;
    p.holeCards = [room.deck.pop(), room.deck.pop()];
    p.folded = false;
    p.allIn = false;
    p.actedThisStreet = false;
    p.lastActionText = null;
    p.bestHandName = null;
  }

  const sbPlayer = room.players[sbSeat];
  const bbPlayer = room.players[bbSeat];

  const sbAmount = Math.min(room.smallBlind, sbPlayer.chips);
  const bbAmount = Math.min(room.bigBlind, bbPlayer.chips);

  postChipAmount(room, sbPlayer, sbAmount);
  sbPlayer.lastActionText = sbPlayer.allIn ? `SB All-In (${sbAmount})` : `SB ${sbAmount}`;

  postChipAmount(room, bbPlayer, bbAmount);
  bbPlayer.lastActionText = bbPlayer.allIn ? `BB All-In (${bbAmount})` : `BB ${bbAmount}`;

  room.currentBet = Math.max(sbPlayer.currentBet, bbPlayer.currentBet);
  room.lastFullRaiseSize = room.bigBlind;
  room.minRaiseTo = room.currentBet + room.lastFullRaiseSize;

  // If either player is already all-in from posting blinds and the other has matched or also all-in
  if ((sbPlayer.allIn && bbPlayer.currentBet >= sbPlayer.currentBet) || (sbPlayer.allIn && bbPlayer.allIn)) {
    runoutToShowdown(room);
    return;
  }

  room.currentTurnSeat = sbSeat;
  room.statusMessage = `Hand #${room.handNumber}: ${sbPlayer.name}'s turn (Dealer / SB)`;
}

function postChipAmount(room, player, amount) {
  const actual = Math.max(0, Math.min(amount, player.chips));
  player.chips -= actual;
  player.currentBet += actual;
  player.totalHandContribution += actual;
  room.pot += actual;
  if (player.chips === 0) {
    player.allIn = true;
  }
  return actual;
}

function handlePlayerAction(room, seat, action, amount = 0) {
  if (!['PREFLOP', 'FLOP', 'TURN', 'RIVER'].includes(room.street)) {
    return { error: 'Hand is not currently in an active betting street.' };
  }
  if (room.currentTurnSeat !== seat) {
    return { error: 'It is not your turn.' };
  }

  const player = room.players[seat];
  const opponent = room.players[1 - seat];
  if (!player || !opponent) return { error: 'Both players must be seated.' };

  const toCall = Math.max(0, room.currentBet - player.currentBet);
  const act = String(action || '').toUpperCase();

  if (act === 'FOLD') {
    player.folded = true;
    player.actedThisStreet = true;
    player.lastActionText = 'Fold';
    opponent.chips += room.pot;
    room.street = 'SHOWDOWN';
    room.currentTurnSeat = -1;
    room.winnerSeats = [opponent.seat];
    room.winningHandName = 'Opponent Folded';
    room.statusMessage = `${player.name} folded. ${opponent.name} wins ${room.pot} chips!`;
    checkMatchOverOrHold(room);
    return { ok: true };
  }

  if (act === 'CHECK') {
    if (toCall > 0) {
      return { error: `Cannot check; ${toCall} to call.` };
    }
    player.actedThisStreet = true;
    player.lastActionText = 'Check';
    advanceTurnOrStreet(room, seat);
    return { ok: true };
  }

  if (act === 'CALL') {
    if (toCall === 0) {
      player.actedThisStreet = true;
      player.lastActionText = 'Check';
      advanceTurnOrStreet(room, seat);
      return { ok: true };
    }
    const added = postChipAmount(room, player, toCall);
    player.actedThisStreet = true;
    player.lastActionText = player.allIn ? `All-In Call (${added})` : `Call ${added}`;
    advanceTurnOrStreet(room, seat);
    return { ok: true };
  }

  if (act === 'RAISE' || act === 'ALL_IN') {
    const maxTotalBet = player.currentBet + player.chips;
    let targetTotalBet = act === 'ALL_IN' ? maxTotalBet : Number(amount);
    if (!Number.isFinite(targetTotalBet)) targetTotalBet = room.minRaiseTo;
    targetTotalBet = Math.min(maxTotalBet, Math.max(targetTotalBet, room.currentBet));

    const needed = targetTotalBet - player.currentBet;
    if (needed <= toCall && targetTotalBet < maxTotalBet) {
      return { error: `Raise must be at least ${room.minRaiseTo} (or All-In).` };
    }
    if (targetTotalBet < room.minRaiseTo && targetTotalBet < maxTotalBet) {
      targetTotalBet = Math.min(maxTotalBet, room.minRaiseTo);
    }

    const previousBetLevel = room.currentBet;
    const actualAdded = postChipAmount(room, player, targetTotalBet - player.currentBet);
    const newBetLevel = player.currentBet;
    const raiseDelta = newBetLevel - previousBetLevel;

    if (newBetLevel > previousBetLevel) {
      room.currentBet = newBetLevel;
      if (raiseDelta >= room.lastFullRaiseSize) {
        room.lastFullRaiseSize = raiseDelta;
      }
      room.minRaiseTo = room.currentBet + room.lastFullRaiseSize;
      opponent.actedThisStreet = false;
    }

    player.actedThisStreet = true;
    player.lastActionText = player.allIn
      ? `All-In (${newBetLevel})`
      : previousBetLevel === 0
        ? `Bet ${newBetLevel}`
        : `Raise to ${newBetLevel}`;

    advanceTurnOrStreet(room, seat);
    return { ok: true };
  }

  return { error: `Unknown action: ${action}` };
}

function advanceTurnOrStreet(room, lastActorSeat) {
  const p0 = room.players[0];
  const p1 = room.players[1];
  const other = room.players[1 - lastActorSeat];

  // Refund any uncallable excess bet immediately if one player is all-in
  if (p0.allIn || p1.allIn) {
    const bothActedOrMatched =
      (p0.allIn && p1.allIn) ||
      (p0.allIn && p1.currentBet >= p0.currentBet) ||
      (p1.allIn && p0.currentBet >= p1.currentBet);

    if (bothActedOrMatched) {
      refundUncalledExcess(room);
      runoutToShowdown(room);
      return;
    }
  }

  if (p0.actedThisStreet && p1.actedThisStreet && p0.currentBet === p1.currentBet) {
    moveToNextStreet(room);
  } else {
    room.currentTurnSeat = other.seat;
    room.statusMessage = `${other.name}'s turn`;
  }
}

function refundUncalledExcess(room) {
  const p0 = room.players[0];
  const p1 = room.players[1];
  if (p0.currentBet > p1.currentBet) {
    const excess = p0.currentBet - p1.currentBet;
    p0.chips += excess;
    p0.currentBet -= excess;
    p0.totalHandContribution -= excess;
    room.pot -= excess;
  } else if (p1.currentBet > p0.currentBet) {
    const excess = p1.currentBet - p0.currentBet;
    p1.chips += excess;
    p1.currentBet -= excess;
    p1.totalHandContribution -= excess;
    room.pot -= excess;
  }
}

function moveToNextStreet(room) {
  for (const p of room.players) {
    p.currentBet = 0;
    p.actedThisStreet = false;
  }
  room.currentBet = 0;
  room.lastFullRaiseSize = room.bigBlind;
  room.minRaiseTo = room.bigBlind;

  if (room.street === 'PREFLOP') {
    room.street = 'FLOP';
    room.communityCards.push(room.deck.pop(), room.deck.pop(), room.deck.pop());
  } else if (room.street === 'FLOP') {
    room.street = 'TURN';
    room.communityCards.push(room.deck.pop());
  } else if (room.street === 'TURN') {
    room.street = 'RIVER';
    room.communityCards.push(room.deck.pop());
  } else if (room.street === 'RIVER') {
    resolveShowdown(room);
    return;
  }

  // Post-flop in Heads-Up: non-dealer (Big Blind) acts first!
  const firstPostflopSeat = 1 - room.dealerSeat;
  room.currentTurnSeat = firstPostflopSeat;
  room.statusMessage = `${room.street}: ${room.players[firstPostflopSeat].name}'s turn`;
}

function runoutToShowdown(room) {
  while (room.communityCards.length < 5) {
    room.communityCards.push(room.deck.pop());
  }
  resolveShowdown(room);
}

function resolveShowdown(room) {
  room.street = 'SHOWDOWN';
  room.currentTurnSeat = -1;
  const p0 = room.players[0];
  const p1 = room.players[1];

  const eval0 = evaluateBestHand([...p0.holeCards, ...room.communityCards]);
  const eval1 = evaluateBestHand([...p1.holeCards, ...room.communityCards]);
  p0.bestHandName = eval0.categoryName;
  p1.bestHandName = eval1.categoryName;

  if (eval0.score > eval1.score) {
    p0.chips += room.pot;
    room.winnerSeats = [0];
    room.winningHandName = eval0.categoryName;
    room.statusMessage = `${p0.name} wins ${room.pot} chips with ${eval0.categoryName}!`;
  } else if (eval1.score > eval0.score) {
    p1.chips += room.pot;
    room.winnerSeats = [1];
    room.winningHandName = eval1.categoryName;
    room.statusMessage = `${p1.name} wins ${room.pot} chips with ${eval1.categoryName}!`;
  } else {
    const half = Math.floor(room.pot / 2);
    const remainder = room.pot - half * 2;
    p0.chips += half + (room.dealerSeat === 1 ? remainder : 0);
    p1.chips += half + (room.dealerSeat === 0 ? remainder : 0);
    room.winnerSeats = [0, 1];
    room.winningHandName = `Split Pot (${eval0.categoryName})`;
    room.statusMessage = `Split pot (${room.pot} chips) — both have ${eval0.categoryName}!`;
  }

  checkMatchOverOrHold(room);
}

function checkMatchOverOrHold(room) {
  const p0 = room.players[0];
  const p1 = room.players[1];
  if (p0.chips <= 0 || p1.chips <= 0) {
    room.street = 'MATCH_OVER';
    const winner = p0.chips > 0 ? p0 : p1;
    room.statusMessage = `${room.statusMessage} ${winner.name} wins the match!`;
  }
}

// --- View Serialization (Masks Opponent Hole Cards Until Showdown) ---
function serializeRoomForSeat(room, viewerSeat) {
  const revealAll =
    (room.street === 'SHOWDOWN' || room.street === 'MATCH_OVER') &&
    room.players[0] &&
    room.players[1] &&
    !room.players[0].folded &&
    !room.players[1].folded;

  const playersView = room.players.map((p, idx) => {
    if (!p) return null;
    const canSeeCards = idx === viewerSeat || revealAll;
    return {
      seat: p.seat,
      name: p.name,
      chips: p.chips,
      currentBet: p.currentBet,
      holeCards: p.holeCards.map((c) =>
        canSeeCards ? { rank: c.rank, suit: c.suit, hidden: false } : { rank: 0, suit: 'HIDDEN', hidden: true }
      ),
      folded: p.folded,
      allIn: p.allIn,
      connected: p.connected,
      lastActionText: p.lastActionText,
      bestHandName: canSeeCards ? p.bestHandName : null,
    };
  });

  const me = viewerSeat >= 0 ? room.players[viewerSeat] : null;
  const toCall = me ? Math.max(0, room.currentBet - me.currentBet) : 0;
  const maxBet = me ? me.currentBet + me.chips : 0;

  return {
    type: 'ROOM_STATE',
    roomCode: room.roomCode,
    isPublic: room.isPublic,
    mySeat: viewerSeat,
    handNumber: room.handNumber,
    dealerSeat: room.dealerSeat,
    smallBlind: room.smallBlind,
    bigBlind: room.bigBlind,
    startingStack: room.startingStack,
    street: room.street,
    pot: room.pot,
    currentBet: room.currentBet,
    minRaiseTo: Math.min(maxBet, Math.max(room.minRaiseTo, room.currentBet + room.bigBlind)),
    toCall,
    currentTurnSeat: room.currentTurnSeat,
    communityCards: room.communityCards,
    statusMessage: room.statusMessage,
    winnerSeats: room.winnerSeats,
    winningHandName: room.winningHandName,
    lastChat: room.lastChat,
    players: playersView,
    serverStats: getServerStatsSnapshot(),
  };
}

function getServerStatsSnapshot() {
  let activeRooms = 0;
  let waitingPublicRooms = [];
  for (const room of rooms.values()) {
    const p0Connected = room.players[0]?.connected;
    const p1Connected = room.players[1]?.connected;
    if (p0Connected || p1Connected) {
      activeRooms += 1;
      if (room.isPublic && room.players[0] && !room.players[1]) {
        waitingPublicRooms.push({
          roomCode: room.roomCode,
          hostName: room.players[0].name,
          blinds: `${room.smallBlind}/${room.bigBlind}`,
          startingStack: room.startingStack,
        });
      }
    }
  }
  return {
    activeRooms,
    onlinePlayers: clients.size,
    openRooms: waitingPublicRooms.slice(0, 20),
  };
}

function broadcastRoomState(room) {
  for (let seat = 0; seat <= 1; seat++) {
    const sock = room.sockets[seat];
    if (sock && !sock.destroyed) {
      sendJson(sock, serializeRoomForSeat(room, seat));
    }
  }
}

function broadcastServerStats() {
  const payload = {
    type: 'SERVER_STATS',
    ...getServerStatsSnapshot(),
  };
  for (const sock of clients) {
    if (!sock.destroyed) {
      sendJson(sock, payload);
    }
  }
}

// --- RFC 6455 WebSocket Framing Helpers ---
function sendJson(socket, obj) {
  try {
    const json = JSON.stringify(obj);
    const payload = Buffer.from(json, 'utf8');
    const len = payload.length;
    let header;
    if (len < 126) {
      header = Buffer.alloc(2);
      header[0] = 0x81; // FIN + text frame
      header[1] = len;
    } else if (len < 65536) {
      header = Buffer.alloc(4);
      header[0] = 0x81;
      header[1] = 126;
      header.writeUInt16BE(len, 2);
    } else {
      header = Buffer.alloc(10);
      header[0] = 0x81;
      header[1] = 127;
      header.writeBigUInt64BE(BigInt(len), 2);
    }
    socket.write(Buffer.concat([header, payload]));
  } catch {
    // Ignore socket write errors on disconnecting peers
  }
}

function decodeWebSocketFrames(buffer) {
  const messages = [];
  let offset = 0;

  while (offset + 2 <= buffer.length) {
    const byte0 = buffer[offset];
    const byte1 = buffer[offset + 1];
    const opcode = byte0 & 0x0f;
    const masked = (byte1 & 0x80) !== 0;
    let payloadLen = byte1 & 0x7f;
    let headerLen = 2;

    if (payloadLen === 126) {
      if (offset + 4 > buffer.length) break;
      payloadLen = buffer.readUInt16BE(offset + 2);
      headerLen = 4;
    } else if (payloadLen === 127) {
      if (offset + 10 > buffer.length) break;
      payloadLen = Number(buffer.readBigUInt64BE(offset + 2));
      headerLen = 10;
    }

    const maskLen = masked ? 4 : 0;
    const frameTotal = headerLen + maskLen + payloadLen;
    if (offset + frameTotal > buffer.length) break;

    const payloadStart = offset + headerLen + maskLen;
    const payload = Buffer.from(buffer.subarray(payloadStart, payloadStart + payloadLen));

    if (masked) {
      const mask = buffer.subarray(offset + headerLen, offset + headerLen + 4);
      for (let i = 0; i < payload.length; i++) {
        payload[i] ^= mask[i & 3];
      }
    }

    messages.push({ opcode, payload });
    offset += frameTotal;
  }

  return { messages, remaining: buffer.subarray(offset) };
}

// --- Client Message Handler ---
function handleClientMessage(socket, msg) {
  const type = String(msg.type || '').toUpperCase();

  if (type === 'GET_STATS' || type === 'PING') {
    sendJson(socket, { type: 'SERVER_STATS', ...getServerStatsSnapshot() });
    return;
  }

  if (type === 'CREATE_ROOM') {
    leaveCurrentRoom(socket);
    const playerId = msg.playerId || crypto.randomUUID();
    const room = createRoom({
      roomCode: msg.roomCode,
      hostName: (msg.playerName || 'Player 1').trim().slice(0, 20),
      hostId: playerId,
      startingStack: msg.startingStack || 1000,
      smallBlind: msg.smallBlind || 10,
      bigBlind: msg.bigBlind || 20,
      isPublic: Boolean(msg.isPublic),
    });
    room.sockets[0] = socket;
    socket.roomCode = room.roomCode;
    socket.seat = 0;
    socket.playerId = playerId;

    broadcastRoomState(room);
    broadcastServerStats();
    return;
  }

  if (type === 'JOIN_ROOM') {
    const code = String(msg.roomCode || '')
      .trim()
      .toUpperCase();
    const room = rooms.get(code);
    if (!room) {
      sendJson(socket, { type: 'ERROR', message: `Room "${code}" not found. Check the 6-character code.` });
      return;
    }

    const playerId = msg.playerId || crypto.randomUUID();
    const playerName = (msg.playerName || 'Player 2').trim().slice(0, 20);

    // Check if reconnecting to an existing seat
    for (let s = 0; s <= 1; s++) {
      const existing = room.players[s];
      if (existing && (existing.playerId === playerId || (!existing.connected && existing.name === playerName))) {
        leaveCurrentRoom(socket);
        existing.connected = true;
        existing.playerId = playerId;
        room.sockets[s] = socket;
        socket.roomCode = room.roomCode;
        socket.seat = s;
        socket.playerId = playerId;
        room.statusMessage = `${existing.name} reconnected!`;
        broadcastRoomState(room);
        broadcastServerStats();
        return;
      }
    }

    if (room.players[1] !== null) {
      sendJson(socket, { type: 'ERROR', message: `Room "${code}" already has 2 players.` });
      return;
    }

    leaveCurrentRoom(socket);
    room.players[1] = {
      seat: 1,
      playerId,
      name: playerName,
      chips: room.startingStack,
      currentBet: 0,
      totalHandContribution: 0,
      holeCards: [],
      folded: false,
      allIn: false,
      actedThisStreet: false,
      connected: true,
      lastActionText: 'Joined',
      bestHandName: null,
    };
    room.sockets[1] = socket;
    socket.roomCode = room.roomCode;
    socket.seat = 1;
    socket.playerId = playerId;

    // Automatically start Hand #1 as soon as both phones are in the lobby!
    startNextHand(room);
    broadcastRoomState(room);
    broadcastServerStats();
    return;
  }

  if (type === 'QUICK_MATCH') {
    // Find an open public room waiting for Player 2, or create one
    let openRoom = null;
    for (const candidate of rooms.values()) {
      if (
        candidate.isPublic &&
        candidate.players[0] &&
        candidate.players[0].connected &&
        candidate.players[1] === null
      ) {
        openRoom = candidate;
        break;
      }
    }

    if (openRoom) {
      handleClientMessage(socket, {
        type: 'JOIN_ROOM',
        roomCode: openRoom.roomCode,
        playerName: msg.playerName || 'Player 2',
        playerId: msg.playerId,
      });
    } else {
      handleClientMessage(socket, {
        type: 'CREATE_ROOM',
        playerName: msg.playerName || 'Player 1',
        playerId: msg.playerId,
        startingStack: msg.startingStack || 1000,
        smallBlind: msg.smallBlind || 10,
        bigBlind: msg.bigBlind || 20,
        isPublic: true,
      });
    }
    return;
  }

  // All remaining actions require being seated in a room
  const room = rooms.get(socket.roomCode);
  if (!room || typeof socket.seat !== 'number') {
    sendJson(socket, { type: 'ERROR', message: 'You are not currently in a room.' });
    return;
  }

  if (type === 'PLAYER_ACTION') {
    const result = handlePlayerAction(room, socket.seat, msg.action, msg.amount);
    if (result.error) {
      sendJson(socket, { type: 'ERROR', message: result.error });
      return;
    }
    broadcastRoomState(room);
    return;
  }

  if (type === 'NEXT_HAND') {
    if (room.street === 'SHOWDOWN' || room.street === 'WAITING') {
      startNextHand(room);
      broadcastRoomState(room);
    }
    return;
  }

  if (type === 'REMATCH') {
    if (room.players[0] && room.players[1]) {
      room.players[0].chips = room.startingStack;
      room.players[1].chips = room.startingStack;
      room.handNumber = 0;
      startNextHand(room);
      broadcastRoomState(room);
    }
    return;
  }

  if (type === 'CHAT_EMOJI') {
    const sender = room.players[socket.seat];
    if (sender) {
      room.lastChat = {
        seat: socket.seat,
        senderName: sender.name,
        text: String(msg.text || '').slice(0, 60),
        timestamp: Date.now(),
      };
      broadcastRoomState(room);
    }
    return;
  }

  if (type === 'LEAVE_ROOM') {
    leaveCurrentRoom(socket);
    sendJson(socket, { type: 'LEFT_ROOM' });
    broadcastServerStats();
  }
}

function leaveCurrentRoom(socket) {
  if (!socket.roomCode) return;
  const room = rooms.get(socket.roomCode);
  const seat = socket.seat;
  socket.roomCode = null;
  socket.seat = null;

  if (!room) return;
  if (typeof seat === 'number' && room.players[seat]) {
    room.players[seat].connected = false;
    room.sockets[seat] = null;
    room.statusMessage = `${room.players[seat].name} disconnected. Waiting for reconnect...`;
    broadcastRoomState(room);
  }

  const anyConnected = room.players.some((p) => p && p.connected);
  if (!anyConnected) {
    // Keep room alive for 5 minutes for reconnects, or clean up immediately if still in WAITING
    if (room.street === 'WAITING') {
      rooms.delete(room.roomCode);
    }
  }
}

// Periodic cleanup of abandoned rooms older than 30 minutes
setInterval(() => {
  const cutoff = Date.now() - 30 * 60 * 1000;
  for (const [code, room] of rooms.entries()) {
    const anyConnected = room.players.some((p) => p && p.connected);
    if (!anyConnected && room.updatedAt < cutoff) {
      rooms.delete(code);
    }
  }
}, 60_000).unref();

// --- HTTP & WebSocket Upgrade Server ---
export function startPokerServer(port = PORT) {
  const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    if (req.url === '/status' || req.url === '/health' || req.url === '/') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(
        JSON.stringify({
          service: 'Heads-Up Poker Multiplayer Room Server',
          status: 'ok',
          ...getServerStatsSnapshot(),
        })
      );
      return;
    }
    res.writeHead(404);
    res.end();
  });

  server.on('upgrade', (req, socket) => {
    const key = req.headers['sec-websocket-key'];
    if (!key) {
      socket.destroy();
      return;
    }

    const acceptKey = crypto
      .createHash('sha1')
      .update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
      .digest('base64');

    socket.write(
      [
        'HTTP/1.1 101 Switching Protocols',
        'Upgrade: websocket',
        'Connection: Upgrade',
        `Sec-WebSocket-Accept: ${acceptKey}`,
        '\r\n',
      ].join('\r\n')
    );

    socket.setNoDelay(true);
    clients.add(socket);
    sendJson(socket, { type: 'SERVER_STATS', ...getServerStatsSnapshot() });

    let recvBuffer = Buffer.alloc(0);

    socket.on('data', (chunk) => {
      recvBuffer = Buffer.concat([recvBuffer, chunk]);
      const { messages, remaining } = decodeWebSocketFrames(recvBuffer);
      recvBuffer = remaining;

      for (const frame of messages) {
        if (frame.opcode === 0x08) {
          // Close frame
          socket.end();
          return;
        }
        if (frame.opcode === 0x09) {
          // Ping -> Pong
          const pong = Buffer.concat([Buffer.from([0x8a, frame.payload.length]), frame.payload]);
          socket.write(pong);
          continue;
        }
        if (frame.opcode === 0x01) {
          try {
            const parsed = JSON.parse(frame.payload.toString('utf8'));
            handleClientMessage(socket, parsed);
          } catch (err) {
            sendJson(socket, { type: 'ERROR', message: `Invalid JSON payload: ${err.message}` });
          }
        }
      }
    });

    const cleanup = () => {
      if (clients.has(socket)) {
        clients.delete(socket);
        leaveCurrentRoom(socket);
        broadcastServerStats();
      }
    };

    socket.on('close', cleanup);
    socket.on('error', cleanup);
  });

  return new Promise((resolve) => {
    server.listen(port, '0.0.0.0', () => {
      const addr = server.address();
      console.log(`♠️♥️ Heads-Up Poker Multiplayer Server listening on ws://0.0.0.0:${addr.port} (HTTP /status)`);
      resolve(server);
    });
  });
}

if (import.meta.url === `file://${process.argv[1]}`) {
  startPokerServer(PORT);
}
