package net.sanctuary.servers.craftedgateway.votd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.config.ConfigKeys;
import net.sanctuary.servers.craftedgateway.config.ConfigUtils;
import net.sanctuary.servers.craftedgateway.text.MessageTemplate;
import net.sanctuary.servers.craftedgateway.util.SchedulerSupport;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public final class VotdService {
    private static final String DEFAULT_VERSION = "KJV";
    private static final String DEFAULT_API_URL = "https://beta.ourmanna.com/api/v1/get/?format=json&order=daily&version=%s";
    private static final String DEFAULT_RANDOM_API_URL = "https://beta.ourmanna.com/api/v1/get/?format=json&order=random&version=%s";
    private static final String DEFAULT_MESSAGE_FORMAT = "&6[VOTD] &e{reference} ({version}) &f{text}";
    private static final String DEFAULT_JOIN_FORMAT = "&6[VOTD] &e{reference} ({version}) &f{text}";
    private static final String DEFAULT_RANDOM_ANNOUNCEMENT_FORMAT = "&6[Verse] &e{reference} ({version}) &f{text}";
    private static final boolean DEFAULT_DEBUG_LOGGING = false;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);

    private final CraftedGatewayPlugin plugin;
    private final BukkitAudiences audiences;
    private final HttpClient httpClient;
    private final Object fetchLock = new Object();
    private final Object randomFetchLock = new Object();

    private volatile VotdEntry cachedVerse;
    private volatile LocalDate cachedDate;
    private volatile String cachedVersion;
    private volatile CompletableFuture<VotdEntry> inflightFetch;
    private volatile VotdEntry cachedRandomVerse;
    private volatile CompletableFuture<VotdEntry> inflightRandomFetch;

    private volatile boolean announcementEnabled;
    private volatile long announcementIntervalTicks;
    private volatile boolean debugLogging;
    private volatile boolean joinEnabled;
    private volatile String bibleVersion;
    private volatile String apiUrlTemplate;
    private volatile String randomApiUrlTemplate;
    private volatile String messageFormat;
    private volatile String joinFormat;
    private volatile String randomAnnouncementFormat;
    private BukkitTask announcementTask;

    public VotdService(CraftedGatewayPlugin plugin, BukkitAudiences audiences) {
        this.plugin = plugin;
        this.audiences = audiences;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
        this.bibleVersion = DEFAULT_VERSION;
        this.apiUrlTemplate = DEFAULT_API_URL;
        this.randomApiUrlTemplate = DEFAULT_RANDOM_API_URL;
        this.cachedVersion = DEFAULT_VERSION;
        this.messageFormat = DEFAULT_MESSAGE_FORMAT;
        this.joinFormat = DEFAULT_JOIN_FORMAT;
        this.randomAnnouncementFormat = DEFAULT_RANDOM_ANNOUNCEMENT_FORMAT;
    }

    public void start() {
        reload();
    }

    public void stop() {
        cancelAnnouncements();
        cachedVerse = null;
        cachedDate = null;
        cachedVersion = null;
        inflightFetch = null;
        cachedRandomVerse = null;
        inflightRandomFetch = null;
    }

    public void reload() {
        reloadFromConfig();
        scheduleAnnouncements();
    }

    public void reloadFromConfig() {
        FileConfiguration config = plugin.getConfig();
        String trimmedVersion = ConfigUtils.getNormalizedString(
            config,
            ConfigKeys.Votd.BIBLE_VERSION,
            DEFAULT_VERSION
        );
        String trimmedTemplate = ConfigUtils.getNormalizedString(
            config,
            ConfigKeys.Votd.API_URL,
            DEFAULT_API_URL
        );
        String trimmedRandomTemplate = ConfigUtils.getNormalizedString(
            config,
            ConfigKeys.Votd.RANDOM_API_URL,
            DEFAULT_RANDOM_API_URL
        );
        boolean versionChanged = !Objects.equals(bibleVersion, trimmedVersion);

        if (versionChanged || !Objects.equals(apiUrlTemplate, trimmedTemplate)) {
            cachedVerse = null;
            cachedDate = null;
        }
        if (versionChanged || !Objects.equals(randomApiUrlTemplate, trimmedRandomTemplate)) {
            cachedRandomVerse = null;
        }

        messageFormat = ConfigUtils.getNormalizedStringFromDefaults(
            config,
            ConfigKeys.Votd.MESSAGE_FORMAT,
            DEFAULT_MESSAGE_FORMAT
        );
        debugLogging = config.getBoolean(
            ConfigKeys.Votd.DEBUG_LOGGING,
            ConfigUtils.getDefaultBoolean(
                config,
                ConfigKeys.Votd.DEBUG_LOGGING,
                DEFAULT_DEBUG_LOGGING
            )
        );
        joinEnabled = config.getBoolean(ConfigKeys.Votd.JOIN_ENABLED, true);
        joinFormat = ConfigUtils.getNormalizedStringFromDefaults(
            config,
            ConfigKeys.Votd.JOIN_FORMAT,
            DEFAULT_JOIN_FORMAT
        );
        String defaultRandomAnnouncement = ConfigUtils.getDefaultString(
            config,
            ConfigKeys.Votd.RANDOM_ANNOUNCEMENT_FORMAT,
            DEFAULT_RANDOM_ANNOUNCEMENT_FORMAT
        );
        randomAnnouncementFormat = ConfigUtils.getNormalizedStringWithFallbackKey(
            config,
            ConfigKeys.Votd.RANDOM_ANNOUNCEMENT_FORMAT,
            ConfigKeys.Votd.ANNOUNCEMENT_FORMAT,
            defaultRandomAnnouncement
        );

        bibleVersion = trimmedVersion;
        apiUrlTemplate = trimmedTemplate;
        randomApiUrlTemplate = trimmedRandomTemplate;
        cachedVersion = trimmedVersion;

        int intervalMinutes = config.getInt(ConfigKeys.Votd.ANNOUNCEMENT_INTERVAL_MINUTES, 10);
        boolean enabled = config.getBoolean(ConfigKeys.Votd.ANNOUNCEMENT_ENABLED, true);
        announcementEnabled = enabled && intervalMinutes > 0;
        announcementIntervalTicks = Math.max(1, intervalMinutes) * 20L * 60L;
    }

    public void sendVerse(CommandSender sender) {
        sendVerse(sender, messageFormat, "command invocation", debugLogging);
    }

    public void sendJoinVerse(CommandSender sender) {
        if (!joinEnabled) {
            return;
        }
        sendVerse(sender, joinFormat, "player join", debugLogging);
    }

    private void sendVerse(CommandSender sender, String template, String context, boolean logFailure) {
        getVerseAsync().whenComplete((verse, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || verse == null) {
                    if (logFailure && error != null) {
                        plugin.getLogger().log(
                            Level.FINE,
                            "Failed to load verse of the day for " + context + ".",
                            error
                        );
                    }
                    audiences.sender(sender).sendMessage(
                        Component.text("Unable to load the verse of the day right now.").color(NamedTextColor.RED)
                    );
                    return;
                }
                audiences.sender(sender).sendMessage(formatMessage(verse, template));
            });
        });
    }

    private void scheduleAnnouncements() {
        announcementTask = SchedulerSupport.cancelTask(announcementTask);
        if (!announcementEnabled || announcementIntervalTicks <= 0) {
            return;
        }
        announcementTask = SchedulerSupport.rescheduleRepeating(
            plugin,
            announcementTask,
            this::announceRandomVerse,
            announcementIntervalTicks,
            announcementIntervalTicks
        );
    }

    private void cancelAnnouncements() {
        announcementTask = SchedulerSupport.cancelTask(announcementTask);
    }

    private void announceRandomVerse() {
        getRandomVerseAsync().whenComplete((verse, error) -> {
            if (error != null || verse == null) {
                if (error != null) {
                    plugin.getLogger().warning("Random verse fetch failed: " + error.getMessage());
                    if (debugLogging) {
                        plugin.getLogger().log(Level.FINE, "Random verse fetch failed.", error);
                    }
                } else {
                    plugin.getLogger().warning("Random verse fetch failed with no cached verse.");
                }
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> audiences.all().sendMessage(formatMessage(verse, randomAnnouncementFormat)));
        });
    }

    private CompletableFuture<VotdEntry> getVerseAsync() {
        LocalDate today = LocalDate.now();
        VotdEntry cached = cachedVerse;
        if (cached != null && today.equals(cachedDate) && Objects.equals(cachedVersion, bibleVersion)) {
            return CompletableFuture.completedFuture(cached);
        }

        synchronized (fetchLock) {
            if (inflightFetch != null && !inflightFetch.isDone()) {
                return inflightFetch;
            }
            CompletableFuture<VotdEntry> future = new CompletableFuture<>();
            inflightFetch = future;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    VotdEntry verse = fetchVerse(apiUrlTemplate);
                    cacheVerse(verse, LocalDate.now());
                    future.complete(verse);
                } catch (Exception e) {
                    VotdEntry fallback = cachedVerse;
                    if (fallback != null) {
                        future.complete(fallback);
                    } else {
                        future.completeExceptionally(e);
                    }
                } finally {
                    synchronized (fetchLock) {
                        inflightFetch = null;
                    }
                }
            });
            return future;
        }
    }

    private CompletableFuture<VotdEntry> getRandomVerseAsync() {
        synchronized (randomFetchLock) {
            if (inflightRandomFetch != null && !inflightRandomFetch.isDone()) {
                return inflightRandomFetch;
            }
            CompletableFuture<VotdEntry> future = new CompletableFuture<>();
            inflightRandomFetch = future;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    VotdEntry verse = fetchVerse(randomApiUrlTemplate);
                    cacheRandomVerse(verse);
                    future.complete(verse);
                } catch (Exception e) {
                    VotdEntry fallback = cachedRandomVerse;
                    if (fallback != null) {
                        future.complete(fallback);
                    } else {
                        future.completeExceptionally(e);
                    }
                } finally {
                    synchronized (randomFetchLock) {
                        inflightRandomFetch = null;
                    }
                }
            });
            return future;
        }
    }

    private VotdEntry fetchVerse(String template) throws IOException, InterruptedException {
        String url = buildApiUrl(template, bibleVersion);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(HTTP_TIMEOUT)
            .header("User-Agent", "CraftedGateway VOTD")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected response status: " + response.statusCode());
        }

        return parseVerse(response.body());
    }

    private VotdEntry parseVerse(String json) throws IOException {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject verseObject = root.has("verse") ? root.getAsJsonObject("verse") : null;
        JsonObject details = verseObject != null && verseObject.has("details") ? verseObject.getAsJsonObject("details") : null;
        if (details == null) {
            throw new IOException("Missing verse details in API response.");
        }
        String text = getRequiredString(details, "text");
        String reference = getRequiredString(details, "reference");
        String version = details.has("version") ? details.get("version").getAsString() : bibleVersion;

        String cleanedText = text.replace("\r", " ").replace("\n", " ").trim();
        return new VotdEntry(reference, cleanedText, version);
    }

    private Component formatMessage(VotdEntry verse, String template) {
        return MessageTemplate.render(
            template,
            "reference", verse.reference(),
            "version", verse.version(),
            "text", verse.text()
        );
    }

    private void cacheVerse(VotdEntry verse, LocalDate date) {
        cachedVerse = verse;
        cachedDate = date;
        cachedVersion = bibleVersion;
    }

    private void cacheRandomVerse(VotdEntry verse) {
        cachedRandomVerse = verse;
    }

    private static String getRequiredString(JsonObject object, String key) throws IOException {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new IOException("Missing field: " + key);
        }
        return object.get(key).getAsString();
    }

    private static String buildApiUrl(String template, String version) {
        String safeTemplate = template == null ? "" : template;
        String encodedVersion = URLEncoder.encode(version, StandardCharsets.UTF_8);
        if (safeTemplate.contains("%s")) {
            return safeTemplate.replace("%s", encodedVersion);
        }
        return upsertQueryParam(safeTemplate, "version", encodedVersion);
    }

    private static String upsertQueryParam(String url, String key, String value) {
        int queryIndex = url.indexOf('?');
        if (queryIndex < 0) {
            return url + "?" + key + "=" + value;
        }
        String base = url.substring(0, queryIndex + 1);
        String query = url.substring(queryIndex + 1);
        if (query.isEmpty()) {
            return base + key + "=" + value;
        }
        int paramIndex = findQueryParamIndex(query, key);
        if (paramIndex < 0) {
            return url + "&" + key + "=" + value;
        }
        int valueStart = paramIndex + key.length();
        if (valueStart < query.length() && query.charAt(valueStart) == '=') {
            valueStart++;
            int valueEnd = query.indexOf('&', valueStart);
            if (valueEnd < 0) {
                valueEnd = query.length();
            }
            return base + query.substring(0, valueStart) + value + query.substring(valueEnd);
        }
        int valueEnd = valueStart;
        if (valueEnd < query.length() && query.charAt(valueEnd) == '&') {
            return base + query.substring(0, valueEnd) + "=" + value + query.substring(valueEnd);
        }
        return base + query.substring(0, valueStart) + "=" + value + query.substring(valueStart);
    }

    private static int findQueryParamIndex(String query, String key) {
        int index = 0;
        while (index <= query.length() - key.length()) {
            int match = query.indexOf(key, index);
            if (match < 0) {
                return -1;
            }
            boolean startOk = match == 0 || query.charAt(match - 1) == '&';
            int after = match + key.length();
            boolean endOk = after == query.length()
                || query.charAt(after) == '='
                || query.charAt(after) == '&';
            if (startOk && endOk) {
                return match;
            }
            index = match + key.length();
        }
        return -1;
    }

}
