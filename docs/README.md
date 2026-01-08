# CraftedGateway Documentation

## Commands
- `/cg` - show plugin status.
- `/votd` - show the verse of the day.
- `/votd reload` - reload VOTD configuration.

## Permissions
- `craftedgateway.votd.reload` (default: op)

## VOTD Behavior
- `/votd` returns the daily verse and caches it per day.
- Join messages use the daily verse when `votd.join-enabled` is true.
- Announcements pull a random verse every interval when `votd.announcement-enabled` is true.

## Radio Now Playing
- Connects to AzuraCast WebSocket updates.
- Broadcasts the current song when it changes.
- Includes the station URL in the announcement.
- Restart the server to apply radio configuration changes.

## Configuration
The defaults live in `config.yml` and can be reloaded with `/votd reload`.

```yaml
votd:
  announcement-enabled: true
  announcement-interval-minutes: 10
  bible-version: KJV
  debug-logging: false
  join-enabled: true
  join-format: "<gold>[VOTD] <yellow><reference> (<version>) <white><text>"
  message-format: "<gold>[VOTD] <yellow><reference> (<version>) <white><text>"
  random-announcement-format: "<gold>[Verse] <yellow><reference> (<version>) <white><text>"
  api-url: "https://beta.ourmanna.com/api/v1/get/?format=json&order=daily&version=%s"
  random-api-url: "https://beta.ourmanna.com/api/v1/get/?format=json&order=random&version=%s"
radio:
  enabled: false
  debug-logging: false
  websocket-url: "wss://radio.sanctuaryunited.net/api/live/nowplaying/sanctuary_radio"
  station-url: "https://radio.sanctuaryunited.net/public/sanctuary_radio"
  message-format: "<gold>[Radio]</gold> <yellow>{song}</yellow> <gray>-</gray> <aqua>{url}</aqua>"
  reconnect-delay-seconds: 10
```

### Formatting
- Templates accept MiniMessage tags (recommended).
- The placeholders `{reference}`, `{version}`, and `{text}` are supported in all templates.
- Legacy `&` color codes are supported when the template does not contain MiniMessage tags.
See `docs/MINIMESSAGE.md` for a MiniMessage primer.

### Radio Placeholders
- `{song}` - combined song text (ex: `Artist - Title`).
- `{artist}` - song artist.
- `{title}` - song title.
- `{url}` - station URL.

### API URLs
- `api-url` and `random-api-url` accept `%s` for the Bible version.
- If `%s` is omitted, the URL is used as-is.

### Debug Logging
- Set `votd.debug-logging: true` to log detailed fetch errors at `FINE` level.

## Build
Run `./gradlew build` and use the shaded jar in `build/libs`.
