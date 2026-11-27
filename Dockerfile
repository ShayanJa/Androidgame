# Lightweight production image for the Heads-Up Poker WebSocket Room Server
FROM node:22-alpine

WORKDIR /app

# Copy the zero-dependency WebSocket server
COPY server/poker_server.mjs ./server/poker_server.mjs

# Run as non-root user for security
USER node

ENV NODE_ENV=production
ENV PORT=8080

EXPOSE 8080

# Built-in healthcheck against the HTTP /status endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/status || exit 1

CMD ["node", "server/poker_server.mjs"]
