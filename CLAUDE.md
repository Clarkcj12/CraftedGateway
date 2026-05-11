# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build        # Build the plugin (output: build/libs/CraftedGateway-*-all.jar)
./gradlew clean build  # Clean and rebuild
```

No test suite exists in this project.

## Architecture Overview

CraftedGateway is a Minecraft Paper plugin (Java 21, API 1.20.1) for SanctuaryMC that provides:
- Verse of the Day (VOTD) system with API integration
- Live radio now-playing announcements via AzuraCast WebSocket
- Dynamic tablist with placeholders
- Night vision command

### Key Dependencies
- **ACF (Aikar Command Framework)** - Command handling, relocated to `net.sanctuary.servers.craftedgateway.acf`
- **Adventure API** - Modern text formatting with MiniMessage support
- **Gson** - JSON parsing for API responses
- **Paper API** - Server platform API target for Paper 1.20.1

### Package Structure

| Package | Purpose |
|---------|---------|
| `command/` | ACF-based commands extending `BaseCommand` |
| `config/` | Configuration keys (`ConfigKeys`) and utilities (`ConfigUtils`) |
| `radio/` | WebSocket-based AzuraCast integration |
| `votd/` | Bible verse API service with caching |
| `tablist/` | Dynamic header/footer with placeholders |
| `text/` | MiniMessage template rendering (`MessageTemplate`) |
| `util/` | Scheduler helpers (`SchedulerSupport`) |
| `metrics/` | Internal performance tracking |

### Service Lifecycle Pattern

Services (`VotdService`, `RadioNowPlayingService`, `TablistService`) follow a consistent pattern:
1. Constructor initializes defaults and dependencies
2. `start()` calls `reload()` to load config and begin operation
3. `reload()` re-reads config and restarts scheduled tasks
4. `stop()` cancels tasks and clears state

### Command Pattern

Commands use ACF annotations and follow this structure:
```java
@CommandAlias("cmd|alias")
@Description("Command description.")
public final class ExampleCommand extends BaseCommand {
    @Default
    public void onDefault(CommandSender sender) { }

    @Subcommand("reload")
    @CommandPermission("craftedgateway.example.reload")
    public void onReload(CommandSender sender) { }
}
```

Use `CommandSupport` utilities for reload operations with user feedback.

### Text Formatting

Use `MessageTemplate.render()` for MiniMessage formatting with placeholders:
```java
MessageTemplate.render(template, "key1", value1, "key2", value2);
```

Supports both MiniMessage (`<gold>`) and legacy (`&6`) color codes.

For every project, write a detailed FOR[Clarkcj].md file that explains the whole project in plain language. This file should cover the technical architecture, the structure of the codebase and how the various parts are connected, the technologies used, and why we made these technical decisions. Include lessons learned from the project: bugs we ran into and how we fixed them, potential pitfalls and how to avoid them in the future, new technologies used, how good engineers think and work, and best practices. Make it very engaging to read—don't make it sound like boring technical documentation or a textbook. Where appropriate, use analogies and anecdotes to make it more understandable and memorable.

### Configuration

All config keys are centralized in `ConfigKeys` inner classes (`Votd`, `Radio`, `Tablist`, `Metrics`). Use `ConfigUtils` for normalized string retrieval with defaults.
