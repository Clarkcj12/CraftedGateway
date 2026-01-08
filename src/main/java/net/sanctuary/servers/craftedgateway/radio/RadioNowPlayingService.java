package net.sanctuary.servers.craftedgateway.radio;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.text.MessageTemplate;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

public final class RadioNowPlayingService {
    private static final String DEFAULT_WEBSOCKET_URL =
        "wss://radio.sanctuaryunited.net/api/live/nowplaying/websocket";
    private static final String DEFAULT_STATION_URL =
        "https://radio.sanctuaryunited.net/public/sanctuary_radio";
    private static final String DEFAULT_STATION_SHORTCODE = "sanctuary_radio";
    private static final String NOW_PLAYING_PATH_PREFIX = "/api/live/nowplaying/";
    private static final String WEBSOCKET_PATH = "/api/live/nowplaying/websocket";
    private static final String DEFAULT_URL_LABEL = "Listen Now";
    private static final String DEFAULT_MESSAGE_FORMAT =
        "<gold>[Radio]</gold> <yellow>{song}</yellow> <gray>-</gray> <aqua>{url}</aqua>";
    private static final int DEFAULT_RECONNECT_SECONDS = 10;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final boolean DEFAULT_ANNOUNCEMENT_ENABLED = true;

    private final CraftedGatewayPlugin plugin;
    private final BukkitAudiences audiences;
    private final HttpClient httpClient;
    private final Object connectionLock = new Object();
    private final AtomicReference<String> lastSongKey = new AtomicReference<>();

    private volatile boolean enabled;
    private volatile boolean debugLogging;
    private volatile String websocketUrl;
    private volatile String stationUrl;
    private volatile String stationShortcode;
    private volatile String urlLabel;
    private volatile String subscribeMessage;
    private volatile String messageFormat;
    private volatile int reconnectDelaySeconds;
    private volatile boolean announcementEnabled;
    private volatile String lastSongText = "";
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
        this.stationShortcode = DEFAULT_STATION_SHORTCODE;
        this.urlLabel = DEFAULT_URL_LABEL;
        this.subscribeMessage = buildSubscribeMessage(this.stationShortcode);
        this.messageFormat = DEFAULT_MESSAGE_FORMAT;
        this.reconnectDelaySeconds = DEFAULT_RECONNECT_SECONDS;
        this.announcementEnabled = DEFAULT_ANNOUNCEMENT_ENABLED;
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
        boolean configUpdated = false;
        String configuredWebsocketUrl = plugin.getConfig().getString("radio.websocket-url", DEFAULT_WEBSOCKET_URL);
        String normalizedWebsocketUrl = normalizeString(configuredWebsocketUrl, DEFAULT_WEBSOCKET_URL);
        String migratedWebsocketUrl = migrateLegacyWebsocketUrl(normalizedWebsocketUrl);
        if (!Objects.equals(normalizedWebsocketUrl, migratedWebsocketUrl)) {
            plugin.getLogger().info("Updating legacy radio websocket URL to " + migratedWebsocketUrl + ".");
            plugin.getConfig().set("radio.websocket-url", migratedWebsocketUrl);
            configUpdated = true;
            normalizedWebsocketUrl = migratedWebsocketUrl;
        }
        websocketUrl = normalizedWebsocketUrl;
        stationUrl = normalizeString(
            plugin.getConfig().getString("radio.station-url", DEFAULT_STATION_URL),
            DEFAULT_STATION_URL
        );
        urlLabel = normalizeString(
            plugin.getConfig().getString("radio.url-label", DEFAULT_URL_LABEL),
            DEFAULT_URL_LABEL
        );
        String configuredShortcode = normalizeOptional(
            plugin.getConfig().getString("radio.station-shortcode", null)
        );
        String derivedShortcode = resolveStationShortcode(null, websocketUrl, stationUrl);
        stationShortcode = resolveStationShortcode(configuredShortcode, websocketUrl, stationUrl);
        if (stationShortcode != null
            && (configuredShortcode == null
                || (DEFAULT_STATION_SHORTCODE.equals(configuredShortcode)
                    && derivedShortcode != null
                    && !DEFAULT_STATION_SHORTCODE.equals(derivedShortcode)))) {
            plugin.getConfig().set("radio.station-shortcode", stationShortcode);
            configUpdated = true;
        }
        subscribeMessage = buildSubscribeMessage(stationShortcode);
        messageFormat = normalizeString(
            plugin.getConfig().getString("radio.message-format", DEFAULT_MESSAGE_FORMAT),
            DEFAULT_MESSAGE_FORMAT
        );
        reconnectDelaySeconds = Math.max(
            1,
            plugin.getConfig().getInt("radio.reconnect-delay-seconds", DEFAULT_RECONNECT_SECONDS)
        );
        announcementEnabled = plugin.getConfig().getBoolean(
            "radio.announcement-enabled",
            DEFAULT_ANNOUNCEMENT_ENABLED
        );
        if (configUpdated) {
            plugin.saveConfig();
        }
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
        if (stationShortcode == null || stationShortcode.isBlank()) {
            plugin.getLogger().warning("Radio station shortcode is not configured; disabling radio updates.");
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
                boolean connected = false;
                synchronized (connectionLock) {
                    connecting = false;
                    if (error == null && enabled && webSocket == null) {
                        webSocket = socket;
                        connected = true;
                    }
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
                if (!connected) {
                    if (socket != null) {
                        try {
                            socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
                        } catch (Exception ignored) {
                            socket.abort();
                        }
                    }
                    return;
                }
                sendSubscribe(socket);
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

    private boolean isActiveSocket(WebSocket socket) {
        synchronized (connectionLock) {
            return enabled && webSocket == socket;
        }
    }

    private void sendSubscribe(WebSocket socket) {
        String connectMessage = subscribeMessage;
        if (connectMessage == null || socket == null) {
            return;
        }
        if (!isActiveSocket(socket)) {
            return;
        }
        socket.sendText(connectMessage, true);
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
                synchronized (connectionLock) {
                    reconnectTask = null;
                }
                connect();
            }, delayTicks);
        }
    }

    private void cancelReconnect() {
        synchronized (connectionLock) {
            if (reconnectTask != null) {
                reconnectTask.cancel();
                reconnectTask = null;
            }
        }
    }

    private void handleMessage(String payload) {
        if (payload == null) {
            return;
        }
        String trimmed = payload.trim();
        if (trimmed.isEmpty() || "{}".equals(trimmed)) {
            return;
        }
        JsonElement element;
        try {
            element = JsonParser.parseString(trimmed);
        } catch (Exception e) {
            if (debugLogging) {
                plugin.getLogger().log(Level.FINE, "Failed to parse radio websocket message.", e);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        JsonObject root = element.getAsJsonObject();
        if (handleConnectPayload(root)) {
            return;
        }
        if (handlePubPayload(root)) {
            return;
        }
        handleNowPlayingPayload(root);
    }

    private boolean handleConnectPayload(JsonObject root) {
        JsonObject connect = getObject(root, "connect");
        if (connect == null) {
            return false;
        }
        JsonArray data = getArray(connect, "data");
        if (data != null) {
            handleConnectDataArray(data);
            return true;
        }
        JsonObject subs = getObject(connect, "subs");
        if (subs == null) {
            return true;
        }
        for (Map.Entry<String, JsonElement> entry : subs.entrySet()) {
            JsonObject sub = asObject(entry.getValue());
            if (sub == null) {
                continue;
            }
            JsonArray publications = getArray(sub, "publications");
            if (publications != null) {
                handleConnectDataArray(publications);
            }
        }
        return true;
    }

    private void handleConnectDataArray(JsonArray data) {
        for (JsonElement element : data) {
            JsonObject payload = asObject(element);
            if (payload != null) {
                handleSsePayload(payload);
            }
        }
    }

    private boolean handlePubPayload(JsonObject root) {
        JsonObject pub = getObject(root, "pub");
        if (pub == null) {
            return false;
        }
        handleNowPlayingPayload(pub);
        return true;
    }

    private void handleSsePayload(JsonObject payload) {
        handleNowPlayingPayload(payload);
    }

    private void handleNowPlayingPayload(JsonObject payload) {
        if (!announcementEnabled) {
            return;
        }
        JsonObject nowPlaying = extractNowPlayingPayload(payload);
        SongInfo info = parseSongInfo(nowPlaying);
        if (info == null || info.text().isBlank()) {
            return;
        }
        String key = info.key();
        String previous = lastSongKey.getAndSet(key);
        if (Objects.equals(previous, key)) {
            return;
        }

        boolean legacyFormat = MessageTemplate.usesLegacyFormat(messageFormat);
        Object urlValue = stationUrl;
        if (!legacyFormat && stationUrl != null && !stationUrl.isBlank()) {
            String label = urlLabel == null || urlLabel.isBlank() ? stationUrl : urlLabel;
            urlValue = Component.text(label).clickEvent(ClickEvent.openUrl(stationUrl));
        }
        Component message = MessageTemplate.render(
            messageFormat,
            "song", info.text(),
            "artist", info.artist(),
            "title", info.title(),
            "url", urlValue
        );
        Bukkit.getScheduler().runTask(plugin, () -> audiences.all().sendMessage(message));
        lastSongText = info.text();
    }

    public void setAnnouncementEnabled(boolean enabled) {
        this.announcementEnabled = enabled;
    }

    public String getLastSongText() {
        return lastSongText;
    }

    private SongInfo parseSongInfo(JsonObject nowPlaying) {
        try {
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

    private JsonObject extractNowPlayingPayload(JsonObject root) {
        if (root == null) {
            return null;
        }
        JsonObject candidate = root;
        JsonObject data = getObject(candidate, "data");
        if (data != null) {
            candidate = data;
        }
        JsonObject np = getObject(candidate, "np");
        if (np != null) {
            candidate = np;
        }
        JsonObject nowPlaying = getObject(candidate, "now_playing");
        if (nowPlaying != null) {
            return nowPlaying;
        }
        JsonObject currentSong = getObject(candidate, "current_song");
        return currentSong != null ? currentSong : candidate;
    }

    private static JsonObject asObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
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

    private static JsonArray getArray(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) {
            return null;
        }
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
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

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String resolveStationShortcode(String configured, String websocketUrl, String stationUrl) {
        String shortcode = normalizeOptional(configured);
        if (shortcode != null) {
            return shortcode;
        }
        shortcode = extractStationShortcode(websocketUrl);
        if (shortcode != null) {
            return shortcode;
        }
        shortcode = extractStationShortcode(stationUrl);
        return shortcode == null ? DEFAULT_STATION_SHORTCODE : shortcode;
    }

    private static String extractStationShortcode(String url) {
        String normalized = normalizeOptional(url);
        if (normalized == null) {
            return null;
        }
        int queryIndex = normalized.indexOf('?');
        String trimmed = queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
        int end = trimmed.length();
        while (end > 0 && trimmed.charAt(end - 1) == '/') {
            end--;
        }
        if (end == 0) {
            return null;
        }
        int start = trimmed.lastIndexOf('/', end - 1);
        if (start < 0 || start == end - 1) {
            return null;
        }
        String segment = trimmed.substring(start + 1, end);
        if (segment.equalsIgnoreCase("websocket") || segment.equalsIgnoreCase("sse")) {
            return null;
        }
        return segment.isEmpty() ? null : segment;
    }

    private static String migrateLegacyWebsocketUrl(String websocketUrl) {
        String normalized = normalizeOptional(websocketUrl);
        if (normalized == null) {
            return null;
        }
        int queryIndex = normalized.indexOf('?');
        String base = queryIndex >= 0 ? normalized.substring(0, queryIndex) : normalized;
        String querySuffix = queryIndex >= 0 ? normalized.substring(queryIndex) : "";
        int prefixIndex = base.indexOf(NOW_PLAYING_PATH_PREFIX);
        if (prefixIndex < 0) {
            return normalized;
        }
        String tail = base.substring(prefixIndex + NOW_PLAYING_PATH_PREFIX.length());
        if (tail.isEmpty() || tail.startsWith("websocket") || tail.startsWith("sse")) {
            return normalized;
        }
        return base.substring(0, prefixIndex) + WEBSOCKET_PATH + querySuffix;
    }

    private static String buildSubscribeMessage(String stationShortcode) {
        if (stationShortcode == null || stationShortcode.isBlank()) {
            return null;
        }
        JsonObject root = new JsonObject();
        JsonObject subs = new JsonObject();
        JsonObject station = new JsonObject();
        station.addProperty("recover", true);
        subs.add("station:" + stationShortcode, station);
        root.add("subs", subs);
        return root.toString();
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
        private final Object bufferLock = new Object();
        private final StringBuilder buffer = new StringBuilder(512);

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            String message = null;
            synchronized (bufferLock) {
                buffer.append(data);
                if (last) {
                    message = buffer.toString();
                    buffer.setLength(0);
                }
            }
            if (message != null) {
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
