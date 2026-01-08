package net.sanctuary.servers.craftedgateway.votd;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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

public final class VotdService {
    private static final String DEFAULT_VERSION = "KJV";
    private static final String DEFAULT_API_URL = "https://beta.ourmanna.com/api/v1/get/?format=json&order=daily&version=%s";
    private static final String DEFAULT_RANDOM_API_URL = "https://beta.ourmanna.com/api/v1/get/?format=json&order=random&version=%s";
    private static final String DEFAULT_MESSAGE_FORMAT = "&6[VOTD] &e{reference} ({version}) &f{text}";
    private static final String DEFAULT_ANNOUNCEMENT_FORMAT = "&6[VOTD] &e{reference} ({version}) &f{text}";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

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
    private volatile String bibleVersion;
    private volatile String apiUrlTemplate;
    private volatile String randomApiUrlTemplate;
    private volatile String messageFormat;
    private volatile String announcementFormat;
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
        this.announcementFormat = DEFAULT_ANNOUNCEMENT_FORMAT;
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
        String nextVersion = plugin.getConfig().getString("votd.bible-version", DEFAULT_VERSION);
        String trimmedVersion = normalizeString(nextVersion, DEFAULT_VERSION);
        String nextTemplate = plugin.getConfig().getString("votd.api-url", DEFAULT_API_URL);
        String trimmedTemplate = normalizeString(nextTemplate, DEFAULT_API_URL);
        String nextRandomTemplate = plugin.getConfig().getString("votd.random-api-url", DEFAULT_RANDOM_API_URL);
        String trimmedRandomTemplate = normalizeString(nextRandomTemplate, DEFAULT_RANDOM_API_URL);
        boolean versionChanged = !Objects.equals(bibleVersion, trimmedVersion);

        if (versionChanged || !Objects.equals(apiUrlTemplate, trimmedTemplate)) {
            cachedVerse = null;
            cachedDate = null;
        }
        if (versionChanged || !Objects.equals(randomApiUrlTemplate, trimmedRandomTemplate)) {
            cachedRandomVerse = null;
        }

        String nextMessageFormat = plugin.getConfig().getString("votd.message-format", DEFAULT_MESSAGE_FORMAT);
        messageFormat = normalizeString(nextMessageFormat, DEFAULT_MESSAGE_FORMAT);
        String nextAnnouncementFormat = plugin.getConfig().getString("votd.announcement-format", DEFAULT_ANNOUNCEMENT_FORMAT);
        announcementFormat = normalizeString(nextAnnouncementFormat, DEFAULT_ANNOUNCEMENT_FORMAT);

        bibleVersion = trimmedVersion;
        apiUrlTemplate = trimmedTemplate;
        randomApiUrlTemplate = trimmedRandomTemplate;
        cachedVersion = trimmedVersion;

        int intervalMinutes = plugin.getConfig().getInt("votd.announcement-interval-minutes", 10);
        boolean enabled = plugin.getConfig().getBoolean("votd.announcement-enabled", true);
        announcementEnabled = enabled && intervalMinutes > 0;
        announcementIntervalTicks = Math.max(1, intervalMinutes) * 20L * 60L;
    }

    public void sendVerse(CommandSender sender) {
        getVerseAsync().whenComplete((verse, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null || verse == null) {
                    audiences.sender(sender).sendMessage(
                        Component.text("Unable to load the verse of the day right now.").color(NamedTextColor.RED)
                    );
                    return;
                }
                audiences.sender(sender).sendMessage(formatMessage(verse, messageFormat));
            });
        });
    }

    private void scheduleAnnouncements() {
        cancelAnnouncements();
        if (!announcementEnabled || announcementIntervalTicks <= 0) {
            return;
        }
        announcementTask = Bukkit.getScheduler().runTaskTimer(
            plugin,
            this::announceRandomVerse,
            announcementIntervalTicks,
            announcementIntervalTicks
        );
    }

    private void cancelAnnouncements() {
        if (announcementTask != null) {
            announcementTask.cancel();
            announcementTask = null;
        }
    }

    private void announceRandomVerse() {
        getRandomVerseAsync().whenComplete((verse, error) -> {
            if (error != null || verse == null) {
                if (error != null) {
                    plugin.getLogger().warning("Random verse fetch failed: " + error.getMessage());
                } else {
                    plugin.getLogger().warning("Random verse fetch failed with no cached verse.");
                }
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> audiences.all().sendMessage(formatMessage(verse, announcementFormat)));
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
        String resolved = template
            .replace("{reference}", verse.reference())
            .replace("{version}", verse.version())
            .replace("{text}", verse.text());
        return LEGACY_SERIALIZER.deserialize(resolved);
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

    private static String normalizeString(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String buildApiUrl(String template, String version) {
        if (template.contains("%s")) {
            return String.format(template, URLEncoder.encode(version, StandardCharsets.UTF_8));
        }
        return template;
    }
}
