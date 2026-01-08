package net.sanctuary.servers.craftedgateway.radio;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.text.MessageTemplate;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class RadioNowPlayingService {
    private static final String DEFAULT_WEBSOCKET_URL =
        "wss://radio.sanctuaryunited.net/api/live/nowplaying/sanctuary_radio";
    private static final String DEFAULT_STATION_URL =
        "https://radio.sanctuaryunited.net/public/sanctuary_radio";
    private static final String DEFAULT_MESSAGE_FORMAT =
        "<gold>[Radio]</gold> <yellow>{song}</yellow> <gray>-</gray> <aqua>{url}</aqua>";
    private static final int DEFAULT_RECONNECT_SECONDS = 10;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final CraftedGatewayPlugin plugin;
    private final BukkitAudiences audiences;
    private final HttpClient httpClient;
    private final Object connectionLock = new Object();
    private final AtomicReference<String> lastSongKey = new AtomicReference<>();

    private volatile boolean enabled;
    private volatile boolean debugLogging;
    private volatile String websocketUrl;
    private volatile String stationUrl;
    private volatile String messageFormat;
    private volatile int reconnectDelaySeconds;
    private volatile WebSocket webSocket;
    private volatile BukkitTask reconnectTask;
    private volatile boolean connecting;

    public RadioNowPlayingService(CraftedGatewayPlugin plugin, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.audiences = audiences;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
        this.websocketUrl = DEFAULT_WEBSOCKET_URL;
        this.stationUrl = DEFAULT_STATION_URL;
        this.messageFormat = DEFAULT_MESSAGE_FORMAT;
        this.reconnectDelaySeconds = DEFAULT_RECONNECT_SECONDS;
    }

    public void start() {
        reload();
    }

    public void stop() {
        enabled = false;
        cancelReconnect();
        closeSocket();
        lastSongKey.set(null);
    }

    public void reload() {
        reloadFromConfig();
        reconnect();
    }

    private void reloadFromConfig() {
        enabled = plugin.getConfig().getBoolean("radio.enabled", false);
        debugLogging = plugin.getConfig().getBoolean("radio.debug-logging", false);
        websocketUrl = normalizeString(
            plugin.getConfig().getString("radio.websocket-url", DEFAULT_WEBSOCKET_URL),
            DEFAULT_WEBSOCKET_URL
        );
        stationUrl = normalizeString(
            plugin.getConfig().getString("radio.station-url", DEFAULT_STATION_URL),
            DEFAULT_STATION_URL
        );
        messageFormat = normalizeString(
            plugin.getConfig().getString("radio.message-format", DEFAULT_MESSAGE_FORMAT),
            DEFAULT_MESSAGE_FORMAT
        );
        reconnectDelaySeconds = Math.max(
            1,
            plugin.getConfig().getInt("radio.reconnect-delay-seconds", DEFAULT_RECONNECT_SECONDS)
        );
    }

    private void reconnect() {
        closeSocket();
        cancelReconnect();
        lastSongKey.set(null);
        if (!enabled) {
            return;
        }
        if (websocketUrl == null || websocketUrl.isBlank()) {
            plugin.getLogger().warning("Radio websocket URL is not configured; disabling radio updates.");
            enabled = false;
            return;
        }
        connect();
    }

    private void connect() {
        synchronized (connectionLock) {
            if (!enabled || connecting || webSocket != null) {
                return;
            }
            connecting = true;
        }

        httpClient.newWebSocketBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .buildAsync(URI.create(websocketUrl), new RadioWebSocketListener())
            .whenComplete((socket, error) -> {
                synchronized (connectionLock) {
                    connecting = false;
                }
                if (error != null) {
                    if (debugLogging) {
                        plugin.getLogger().log(Level.FINE, "Radio websocket connection failed.", error);
                    } else {
                        plugin.getLogger().warning("Radio websocket connection failed: " + error.getMessage());
                    }
                    scheduleReconnect();
                    return;
                }
                webSocket = socket;
                if (debugLogging) {
                    plugin.getLogger().info("Radio websocket connected.");
                }
            });
    }

    private void closeSocket() {
        WebSocket socket = webSocket;
        webSocket = null;
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
                socket.abort();
            }
        }
    }

    private void markSocketClosed() {
        synchronized (connectionLock) {
            webSocket = null;
        }
    }

    private void scheduleReconnect() {
        if (!enabled) {
            return;
        }
        synchronized (connectionLock) {
            if (reconnectTask != null) {
                return;
            }
            long delayTicks = reconnectDelaySeconds * 20L;
            reconnectTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                reconnectTask = null;
                connect();
            }, delayTicks);
        }
    }

    private void cancelReconnect() {
        BukkitTask task = reconnectTask;
        if (task != null) {
            task.cancel();
        }
        reconnectTask = null;
    }

    private void handleMessage(String payload) {
        SongInfo info = parseSongInfo(payload);
        if (info == null || info.text().isBlank()) {
            return;
        }
        String key = info.key();
        String previous = lastSongKey.getAndSet(key);
        if (Objects.equals(previous, key)) {
            return;
        }

        Component message = MessageTemplate.render(
            messageFormat,
            "song", info.text(),
            "artist", info.artist(),
            "title", info.title(),
            "url", stationUrl
        );
        Bukkit.getScheduler().runTask(plugin, () -> audiences.all().sendMessage(message));
    }

    private SongInfo parseSongInfo(String payload) {
        try {
            JsonElement element = JsonParser.parseString(payload);
            if (!element.isJsonObject()) {
                return null;
            }
            JsonObject root = element.getAsJsonObject();
            JsonObject data = getObject(root, "data");
            if (data == null) {
                data = root;
            }
            JsonObject nowPlaying = getObject(data, "now_playing");
            if (nowPlaying == null) {
                nowPlaying = getObject(data, "current_song");
            }
            if (nowPlaying == null) {
                return null;
            }
            JsonObject song = getObject(nowPlaying, "song");
            if (song == null) {
                return null;
            }

            String text = getString(song, "text");
            String artist = getString(song, "artist");
            String title = getString(song, "title");
            if (text == null || text.isBlank()) {
                if (artist != null && title != null) {
                    text = artist + " - " + title;
                } else if (title != null) {
                    text = title;
                } else if (artist != null) {
                    text = artist;
                }
            }
            if (text == null) {
                text = "";
            }

            String key = firstNonEmpty(
                getString(nowPlaying, "sh_id"),
                getString(nowPlaying, "played_at"),
                getString(song, "id"),
                text
            );

            return new SongInfo(key, text, nullToEmpty(artist), nullToEmpty(title));
        } catch (Exception e) {
            if (debugLogging) {
                plugin.getLogger().log(Level.FINE, "Failed to parse radio now playing payload.", e);
            }
            return null;
        }
    }

    private static JsonObject getObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private static String getString(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        JsonElement element = parent.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsString();
        } catch (UnsupportedOperationException ignored) {
            return element.toString();
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String normalizeString(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record SongInfo(String key, String text, String artist, String title) {
    }

    private final class RadioWebSocketListener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder(512);

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (debugLogging) {
                plugin.getLogger().log(Level.FINE, "Radio websocket error.", error);
            } else {
                plugin.getLogger().warning("Radio websocket error: " + error.getMessage());
            }
            markSocketClosed();
            scheduleReconnect();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (debugLogging) {
                plugin.getLogger().info("Radio websocket closed: " + statusCode + " (" + reason + ")");
            }
            markSocketClosed();
            scheduleReconnect();
            return CompletableFuture.completedFuture(null);
        }
    }
}
