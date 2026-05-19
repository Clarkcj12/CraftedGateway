# CraftedGateway

CraftedGateway is a lightweight Minecraft plugin for SanctuaryMC running on a Paper-compatible hybrid server environment (Paper 1.20.1 + NeoForge), focused on clean commands and a configurable Verse of the Day system.

## Features
- `/cg` status command with Adventure output.
- Verse of the Day command and join message.
- Random verse announcements on a configurable interval.
- Live radio now-playing announcements via AzuraCast WebSocket.
- MiniMessage formatting with legacy `&` fallback.
- Configurable Bible version and API endpoints.

## Requirements
- Java 21
- Paper API 1.20.1
- Server runtime: SanctuaryMC's hybrid Paper 1.20.1 + NeoForge environment
- Internet access for verse API calls

## Installation
1. Build the jar with `./gradlew build`.
2. Copy `build/libs/CraftedGateway-*-all.jar` to your server `plugins/` folder.
3. Start the server to generate `config.yml`.
4. Edit `config.yml` as desired and run `/votd reload`.

## Commands
- `/cg` - show plugin status.
- `/votd` - show the verse of the day.
- `/votd reload` - reload VOTD configuration.

## Permissions
- `craftedgateway.votd.reload` (default: op)

## Configuration
See `docs/README.md` for the full configuration and formatting reference.
