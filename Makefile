# Heads-Up Poker Makefile

ANDROID_SDK ?= $(HOME)/Library/Android/sdk
ADB         ?= $(shell command -v adb 2>/dev/null || echo "$(ANDROID_SDK)/platform-tools/adb")
ANDROID_CLI ?= $(shell command -v android 2>/dev/null)
AVD         ?= $(shell $(ANDROID_CLI) emulator list 2>/dev/null | head -n 1)

APP_ID      := com.example.headsuppoker
ACTIVITY    := $(APP_ID)/.MainActivity

.PHONY: help emulator stop-emulator build install launch run server test clean

help:
	@echo "Available targets:"
	@echo "  make run           - Ensure emulator is running, build, install, and launch the app"
	@echo "  make emulator      - Start the Android emulator (AVD=$(AVD)) and wait for boot"
	@echo "  make stop-emulator - Stop the running Android emulator"
	@echo "  make build         - Assemble the debug APK"
	@echo "  make install       - Build and install the debug APK on the running device"
	@echo "  make launch        - Launch $(ACTIVITY) on the running device"
	@echo "  make server        - Start the local Node.js multiplayer WebSocket server (port 8080)"
	@echo "  make test          - Run Android unit tests and server stress tests"
	@echo "  make clean         - Clean Gradle build artifacts"

emulator:
	@if $(ADB) devices | grep -q "emulator-.*device$$"; then \
		echo "Emulator is already running."; \
	else \
		if [ -z "$(AVD)" ]; then \
			echo "Error: No AVD found. Set AVD=<name> or create one via Android Studio / android emulator create."; \
			exit 1; \
		fi; \
		echo "Starting emulator '$(AVD)'..."; \
		$(ANDROID_CLI) emulator start "$(AVD)"; \
	fi
	@$(ADB) wait-for-device shell 'while [ -z "$$(getprop sys.boot_completed)" ]; do sleep 1; done'
	@echo "Emulator is ready."

stop-emulator:
	@if [ -n "$(AVD)" ] && [ -n "$(ANDROID_CLI)" ]; then \
		$(ANDROID_CLI) emulator stop "$(AVD)"; \
	else \
		$(ADB) emu kill; \
	fi

build:
	./gradlew assembleDebug

install:
	./gradlew installDebug

launch:
	$(ADB) shell am start -n $(ACTIVITY)

run: emulator install launch

server:
	node server/poker_server.mjs

test:
	./gradlew testDebugUnitTest
	node server/test_server.mjs

clean:
	./gradlew clean
