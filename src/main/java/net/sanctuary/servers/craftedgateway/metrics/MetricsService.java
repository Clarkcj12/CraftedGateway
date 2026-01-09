package net.sanctuary.servers.craftedgateway.metrics;

import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.config.ConfigKeys;
import net.sanctuary.servers.craftedgateway.config.ConfigUtils;
import net.sanctuary.servers.craftedgateway.radio.RadioNowPlayingService;
import net.sanctuary.servers.craftedgateway.tablist.TablistService;
import net.sanctuary.servers.craftedgateway.util.SchedulerSupport;
import net.sanctuary.servers.craftedgateway.votd.VotdService;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public final class MetricsService {
    private static final int DEFAULT_LOG_INTERVAL_MINUTES = 10;
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final CraftedGatewayPlugin plugin;
    private final Object taskLock = new Object();
    private final TimingBucket tablistUpdate = new TimingBucket("tablist.update");
    private final TimingBucket radioMessage = new TimingBucket("radio.handle-message");
    private final TimingBucket votdFetchDaily = new TimingBucket("votd.fetch-daily");
    private final TimingBucket votdFetchRandom = new TimingBucket("votd.fetch-random");

    private volatile boolean enabled;
    private volatile long logIntervalTicks;
    private BukkitTask logTask;
    private VotdService votdService;
    private RadioNowPlayingService radioService;
    private TablistService tablistService;

    public MetricsService(CraftedGatewayPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin must not be null");
        this.enabled = false;
        this.logIntervalTicks = DEFAULT_LOG_INTERVAL_MINUTES * 20L * 60L;
    }

    public void setServices(
        VotdService votdService,
        RadioNowPlayingService radioService,
        TablistService tablistService
    ) {
        this.votdService = votdService;
        this.radioService = radioService;
        this.tablistService = tablistService;
    }

    public void start() {
        reload();
    }

    public void stop() {
        synchronized (taskLock) {
            logTask = SchedulerSupport.cancelAndClearTask(logTask);
        }
    }

    public void reload() {
        reloadFromConfig();
        schedule();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void recordTablistUpdate(long durationNanos) {
        if (!enabled) {
            return;
        }
        tablistUpdate.record(durationNanos);
    }

    public void recordRadioHandleMessage(long durationNanos) {
        if (!enabled) {
            return;
        }
        radioMessage.record(durationNanos);
    }

    public void recordVotdFetchDaily(long durationNanos) {
        if (!enabled) {
            return;
        }
        votdFetchDaily.record(durationNanos);
    }

    public void recordVotdFetchRandom(long durationNanos) {
        if (!enabled) {
            return;
        }
        votdFetchRandom.record(durationNanos);
    }

    private void reloadFromConfig() {
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean(
            ConfigKeys.Metrics.ENABLED,
            ConfigUtils.getDefaultBoolean(config, ConfigKeys.Metrics.ENABLED, false)
        );
        int intervalMinutes = config.getInt(
            ConfigKeys.Metrics.LOG_INTERVAL_MINUTES,
            ConfigUtils.getDefaultInt(
                config,
                ConfigKeys.Metrics.LOG_INTERVAL_MINUTES,
                DEFAULT_LOG_INTERVAL_MINUTES
            )
        );
        if (intervalMinutes <= 0) {
            enabled = false;
        }
        logIntervalTicks = Math.max(1, intervalMinutes) * 20L * 60L;
    }

    private void schedule() {
        boolean schedule = enabled && logIntervalTicks > 0;
        synchronized (taskLock) {
            logTask = SchedulerSupport.rescheduleRepeatingIfEnabled(
                plugin,
                logTask,
                this::logSnapshot,
                logIntervalTicks,
                logIntervalTicks,
                schedule
            );
        }
    }

    private void logSnapshot() {
        if (!enabled) {
            return;
        }
        try {
            StringBuilder builder = new StringBuilder(256);
            appendMemory(builder);
            appendCaches(builder);
            appendTiming(builder, tablistUpdate.snapshotAndReset());
            appendTiming(builder, radioMessage.snapshotAndReset());
            appendTiming(builder, votdFetchDaily.snapshotAndReset());
            appendTiming(builder, votdFetchRandom.snapshotAndReset());
            plugin.getLogger().info(builder.toString());
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to log metrics snapshot.", e);
        }
    }

    private void appendMemory(StringBuilder builder) {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = total - free;
        long max = runtime.maxMemory();
        builder.append("Metrics: heap=")
            .append(bytesToMb(used))
            .append("MB/")
            .append(bytesToMb(total))
            .append("MB max=")
            .append(bytesToMb(max))
            .append("MB");
    }

    private void appendCaches(StringBuilder builder) {
        builder.append(" caches[");
        int votdDaily = votdService != null && votdService.hasCachedVerse() ? 1 : 0;
        int votdRandom = votdService != null && votdService.hasCachedRandomVerse() ? 1 : 0;
        int radioLast = radioService != null && radioService.getLastSongText().isPresent() ? 1 : 0;
        builder.append("votd.daily=").append(votdDaily)
            .append(",votd.random=").append(votdRandom)
            .append(",radio.lastSong=").append(radioLast)
            .append("]");
        if (tablistService != null) {
            builder.append(" tablist.enabled=").append(tablistService.isEnabled());
        }
    }

    private void appendTiming(StringBuilder builder, TimingSnapshot snapshot) {
        builder.append(" timing[")
            .append(snapshot.name)
            .append(" count=")
            .append(snapshot.count);
        if (snapshot.count > 0) {
            long avgMs = snapshot.totalNanos / snapshot.count / 1_000_000L;
            long maxMs = snapshot.maxNanos / 1_000_000L;
            builder.append(" avgMs=").append(avgMs).append(" maxMs=").append(maxMs);
        }
        builder.append("]");
    }

    private static long bytesToMb(long bytes) {
        return bytes / BYTES_PER_MB;
    }

    private static final class TimingBucket {
        private final String name;
        private long count;
        private long totalNanos;
        private long maxNanos;

        private TimingBucket(String name) {
            this.name = name;
        }

        private synchronized void record(long nanos) {
            count++;
            totalNanos += nanos;
            if (nanos > maxNanos) {
                maxNanos = nanos;
            }
        }

        private synchronized TimingSnapshot snapshotAndReset() {
            TimingSnapshot snapshot = new TimingSnapshot(name, count, totalNanos, maxNanos);
            count = 0;
            totalNanos = 0;
            maxNanos = 0;
            return snapshot;
        }
    }

    private record TimingSnapshot(String name, long count, long totalNanos, long maxNanos) {
    }
}
