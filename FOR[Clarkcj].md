# CraftedGateway: The Story Behind the Code

Welcome to the behind-the-scenes tour of CraftedGateway! This isn't your typical dry documentation—think of it more like a conversation about how this plugin came to be, what makes it tick, and what you can learn from building it.

## What Is This Thing, Anyway?

CraftedGateway is the "Swiss Army knife" plugin for SanctuaryMC. It handles a grab bag of features that don't really belong in separate plugins but are essential for making the server feel alive:

- **Verse of the Day** — Fetches Bible verses from an API and shares them with players
- **Live Radio Integration** — Announces what song is playing on the server's internet radio station
- **Dynamic Tablist** — That player list you see when you press Tab? We make it show useful info like the current time and what's playing on the radio
- **Night Vision** — A simple quality-of-life command for toggling night vision

None of these features are groundbreaking on their own, but together they create atmosphere. It's like the difference between a house and a home—the little touches matter.

---

## The Architecture: How Everything Connects

### The Plugin as an Orchestra Conductor

Think of `CraftedGatewayPlugin.java` as an orchestra conductor. It doesn't play any instruments itself—it just makes sure everyone starts at the right time, plays together, and stops when the concert ends.

```
CraftedGatewayPlugin (the conductor)
    ├── VotdService (the strings section)
    ├── RadioNowPlayingService (the brass section)
    ├── TablistService (the percussion)
    ├── MetricsService (the sound engineer)
    └── Commands (the soloists)
```

When the plugin starts (`onEnable`), it:
1. Loads the config file
2. Creates all the services and hands them their dependencies
3. Starts each service
4. Registers the commands

When it stops (`onDisable`), it does the reverse—gracefully shutting everything down. This is important! If you don't clean up properly, you can leave zombie tasks running or connections hanging open.

### The Service Pattern: Start, Reload, Stop

Every service follows the same lifecycle dance:

```java
public void start() {
    reload();  // Load config and begin
}

public void stop() {
    // Cancel tasks, close connections, clear state
}

public void reload() {
    reloadFromConfig();  // Read fresh config values
    // Restart whatever needs restarting
}
```

**Why this pattern?** Because Minecraft server admins love to tweak configs without restarting the server. The `reload()` method lets them run `/cg reload` and have changes take effect immediately. Without this, they'd have to restart the whole server just to change a message format.

This is a form of the **Strategy Pattern** in disguise—the behavior (message formats, intervals, URLs) is configurable at runtime rather than hardcoded.

---

## The Technologies: Our Toolbox

### ACF (Aikar Command Framework)

**What it is:** A library that makes writing Minecraft commands much less painful.

**Why we use it:** Have you ever written a command parser by hand? It's miserable. You end up with a giant `if-else` tree checking arguments, validating permissions, handling tab completion... ACF lets you write commands like this instead:

```java
@CommandAlias("nv|nightvision")
@CommandPermission("craftedgateway.nightvision.self")
public class NightVisionCommand extends BaseCommand {

    @Default
    public void onDefault(CommandSender sender) {
        // Toggle night vision
    }

    @Subcommand("on")
    public void onEnable(CommandSender sender) {
        // Enable night vision
    }
}
```

The annotations do all the heavy lifting. `@CommandAlias` sets up the command and its aliases. `@CommandPermission` handles permission checks. `@Subcommand` creates subcommands. It's declarative rather than imperative—you say *what* you want, not *how* to do it.

**The relocation trick:** In `build.gradle`, we relocate ACF to our own package:

```groovy
relocate 'co.aikar.commands', 'net.sanctuary.servers.craftedgateway.acf'
```

Why? Because if another plugin also uses ACF but a different version, they'd conflict. By relocating, our ACF is completely isolated. It's like putting your food in a labeled container in a shared fridge—no mix-ups.

### Adventure API

**What it is:** The modern way to handle text in Minecraft plugins.

**The old way was terrible:** Minecraft's original chat API uses raw strings with `§` color codes. Want to make text clickable? Good luck. Want hover text? Even more pain. And don't get me started on RGB colors.

**Adventure fixes this:** Instead of strings, you work with `Component` objects that can have colors, click events, hover text, and more:

```java
Component message = Component.text("Click me!")
    .color(NamedTextColor.GREEN)
    .clickEvent(ClickEvent.openUrl("https://example.com"));
```

**MiniMessage is the cherry on top:** Instead of building components in code, you can use a mini-language:

```
<gold>[Radio]</gold> <yellow>{song}</yellow> <gray>-</gray> <aqua>{url}</aqua>
```

This string gets parsed into a proper Component. Admins can customize messages in the config without touching code.

### Java's Built-in HttpClient and WebSocket

**For VOTD:** We use `HttpClient` to fetch verses from an API. It's built into Java 11+, so no extra dependencies needed.

**For Radio:** We use Java's `WebSocket` API to maintain a persistent connection to the AzuraCast server. When a new song starts playing, the server pushes the update to us instantly.

**Why WebSocket instead of polling?** Imagine you're waiting for a package. You could call the delivery company every 5 minutes asking "Is it here yet?" (polling), or you could just wait for them to call you when it arrives (WebSocket). The second approach is more efficient and gets you the news faster.

---

## Technical Decisions: The "Why" Behind the "What"

### Why Separate Services Instead of One Big Class?

We could have put everything in `CraftedGatewayPlugin.java`. It would work. It would also be a nightmare to maintain.

**Single Responsibility Principle:** Each service does one thing well. `VotdService` handles verses. `RadioNowPlayingService` handles radio. If there's a bug in the radio code, you know exactly where to look.

**Easier testing:** (Even though we don't have tests yet—more on that later.) If you wanted to test the verse parsing logic, you could test `VotdService` in isolation without worrying about radio or tablist.

**Parallel development:** If two people are working on the plugin, one can modify VOTD while the other works on Radio without stepping on each other's toes.

### Why Cache API Responses?

The VOTD service caches the daily verse:

```java
private volatile VotdEntry cachedVerse;
private volatile LocalDate cachedDate;
```

**Reason 1: Be a good API citizen.** The verse API is free. If 50 players join and each triggers a verse fetch, that's 50 API calls in seconds. The API provider might rate-limit us or, worse, block us entirely. By caching, we make one call per day maximum.

**Reason 2: Speed.** Network calls are slow (tens to hundreds of milliseconds). Cache hits are fast (microseconds). Players get their verse instantly instead of waiting.

**Reason 3: Resilience.** If the API goes down, we still have yesterday's verse cached. Better to show something than nothing.

### Why `volatile` Everywhere?

You'll see `volatile` sprinkled throughout the services:

```java
private volatile boolean enabled;
private volatile String websocketUrl;
```

**The problem:** Minecraft runs on multiple threads. The main server thread handles game logic, but we do network calls on async threads. If one thread writes a variable and another reads it, without `volatile`, the reader might see stale data due to CPU caching.

**The solution:** `volatile` ensures that writes are immediately visible to all threads. It's like posting an announcement on a bulletin board instead of whispering to one person.

---

## Lessons Learned: The School of Hard Knocks

### Lesson 1: Always Run Network Calls Asynchronously

**The bug:** Early versions might have made API calls on the main thread. The main thread handles *everything*—player movement, block updates, chat, you name it. If it blocks for even 100ms waiting for an API response, the whole server freezes. Players rubberband. Blocks don't break. It's bad.

**The fix:** Always use `Bukkit.getScheduler().runTaskAsynchronously()` for network operations:

```java
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    // Network call here - safe because we're off the main thread
    VotdEntry verse = fetchVerse(apiUrlTemplate);

    // But to update the game, we must go back to the main thread!
    Bukkit.getScheduler().runTask(plugin, () -> {
        audiences.sender(sender).sendMessage(formatMessage(verse));
    });
});
```

**The golden rule:** Network calls off the main thread, game state changes on the main thread.

### Lesson 2: WebSocket Connections Need Babysitting

**The problem:** WebSocket connections can drop for many reasons—network hiccups, server restarts, cosmic rays. If you don't handle disconnections, your feature just silently stops working.

**The solution:** The `RadioNowPlayingService` has reconnection logic:

```java
private void scheduleReconnect() {
    if (!enabled) return;
    reconnectTask = SchedulerSupport.rescheduleAsyncLater(plugin, reconnectTask, () -> {
        connect();
    }, delayTicks);
}
```

When the connection drops, we wait a bit (configurable `reconnect-delay-seconds`) and try again. We also clear the "last song" state so we don't show stale information.

**Analogy:** It's like your phone automatically reconnecting to WiFi when you come home. You don't have to manually connect every time—it just handles it.

### Lesson 3: Configuration Migration is Tricky

Look at this code in `RadioNowPlayingService`:

```java
String migratedWebsocketUrl = migrateLegacyWebsocketUrl(normalizedWebsocketUrl);
if (!Objects.equals(normalizedWebsocketUrl, migratedWebsocketUrl)) {
    plugin.getLogger().info("Updating legacy radio websocket URL...");
    config.set(ConfigKeys.Radio.WEBSOCKET_URL, migratedWebsocketUrl);
    configUpdated = true;
}
```

**The story:** At some point, AzuraCast changed their WebSocket URL format. Old configs had the outdated URL. Instead of making admins manually update, we detect the old format and automatically migrate it.

**The lesson:** When you change how something works, think about existing users. Can you migrate them automatically? Can you at least give them a helpful error message? Breaking changes without migration paths make people angry.

### Lesson 4: Centralize Your Configuration Keys

All config paths live in `ConfigKeys.java`:

```java
public final class ConfigKeys {
    public static final class Votd {
        public static final String ANNOUNCEMENT_ENABLED = "votd.announcement-enabled";
        public static final String BIBLE_VERSION = "votd.bible-version";
        // ...
    }
}
```

**Why this matters:** Typos in config paths are silent killers. If you write `"votd.anouncement-enabled"` (note the typo), Java won't complain. You'll just get the default value and wonder why your setting isn't working.

By centralizing keys as constants, typos become compile errors. Your IDE can also autocomplete them.

### Lesson 5: The `synchronized` Keyword is Your Friend (and Enemy)

The radio service has careful locking:

```java
private final Object connectionLock = new Object();

private void connect() {
    synchronized (connectionLock) {
        if (!enabled || connecting || webSocket != null) {
            return;
        }
        connecting = true;
    }
    // ... actual connection logic
}
```

**The problem it solves:** What if `connect()` is called twice simultaneously? Without the lock, you might end up with two WebSocket connections, which causes all sorts of chaos.

**The trap:** It's tempting to just slap `synchronized` on every method. Don't. Locks are expensive and can cause deadlocks if you're not careful. Only lock what needs to be locked, and keep the locked sections as short as possible.

---

## Best Practices on Display

### 1. Fail Gracefully

When the verse API fails:
```java
} catch (Exception e) {
    VotdEntry fallback = cachedVerse;
    if (fallback != null) {
        future.complete(fallback);  // Return cached verse
    } else {
        future.completeExceptionally(e);  // Only fail if we have nothing
    }
}
```

Don't just crash. Try to provide a degraded experience. Cached data is better than an error message.

### 2. Use Descriptive Logging Levels

```java
if (debugLogging) {
    plugin.getLogger().log(Level.FINE, "Radio websocket connection failed.", error);
} else {
    plugin.getLogger().warning("Radio websocket connection failed: " + error.getMessage());
}
```

In production, show the simple message. When debugging, show the full stack trace. This keeps logs clean while still allowing detailed troubleshooting.

### 3. Clean Up After Yourself

The `stop()` methods are thorough:
```java
public void stop() {
    enabled = false;
    cancelReconnect();
    closeSocket();
    lastSongKey.set(null);
    clearLastSongText();
}
```

Every scheduled task is cancelled. Every connection is closed. Every cache is cleared. When the plugin disables, it should leave no trace.

---

## What Good Engineers Do (That You Can Learn From)

### They Think About Edge Cases

What if the API returns malformed JSON? What if the WebSocket URL is empty? What if someone reloads the config while a network request is in flight?

Good code handles these cases. Look at how `parseSongInfo` has null checks everywhere:

```java
if (nowPlaying == null) return null;
JsonObject song = getObject(nowPlaying, "song");
if (song == null) return null;
```

It's defensive. It assumes the world is out to get it.

### They Make Code Readable

Compare these two approaches:

```java
// Bad: Magic numbers and unclear intent
player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 2147483647, 0, false, false, true));

// Good: Named constant and clear structure
player.addPotionEffect(new PotionEffect(
    PotionEffectType.NIGHT_VISION,
    Integer.MAX_VALUE,  // Duration: infinite
    0,                  // Amplifier: level 1
    false,              // Ambient: no
    false,              // Particles: no
    true                // Icon: yes
));
```

Comments explain the *why*, not the *what*. The code should be self-explanatory for the *what*.

### They Don't Repeat Themselves

`CommandSupport` exists because reload operations follow the same pattern:

```java
public static void reloadConfigAndNotifySender(
    CraftedGatewayPlugin plugin,
    CommandSender sender,
    String message,
    Runnable reloadAction
) {
    runAndNotifySender(sender, message, () -> reloadConfigAndService(plugin, reloadAction));
}
```

Every reload command uses this helper instead of duplicating the logic. If you need to change how reloads work, you change it in one place.

---

## What's Missing (Room for Growth)

### Tests

There are no automated tests. This is technical debt. Every time you change something, you have to manually test it on a Minecraft server. That's slow and error-prone.

**Future improvement:** Add unit tests for the parsing logic, at minimum. Libraries like Mockito can help mock Bukkit APIs.

### Error Messages Could Be More Helpful

When something fails, we often just say "Unable to load the verse of the day right now." We could tell the user *why*—was it a network error? Did the API change its format? More specific errors help with troubleshooting.

### Metrics Are Internal Only

The `MetricsService` tracks performance data but doesn't expose it anywhere useful. Future versions could integrate with something like bStats to see how the plugin performs across many servers.

---

## Wrapping Up

CraftedGateway isn't a massive, complex system—and that's intentional. It's a well-organized plugin that does a few things well. The patterns here—service lifecycle, async operations, defensive coding, configuration management—apply to projects of any size.

The next time you're building something, ask yourself:
- What happens when this fails?
- Can someone reload this without restarting everything?
- Am I being a good citizen to external services?
- Will I understand this code in six months?

If you can answer those questions confidently, you're on the right track.

Happy coding! 🎮
