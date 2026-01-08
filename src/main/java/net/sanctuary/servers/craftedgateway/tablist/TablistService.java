package net.sanctuary.servers.craftedgateway.tablist;

import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.radio.RadioNowPlayingService;
import net.sanctuary.servers.craftedgateway.text.MessageTemplate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class TablistService {
    private static final String DEFAULT_TIME_FORMAT = "h:mm a";
    private static final String DEFAULT_HEADER = "<gold>SanctuaryMC</gold>";
    private static final String DEFAULT_FOOTER = "<gray>Now Playing:</gray> <yellow>{song}</yellow>";
    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_UPDATE_TICKS = 40;

    private final CraftedGatewayPlugin plugin;
    private final BukkitAudiences audiences;
    private final RadioNowPlayingService radioService;
    private final Object taskLock = new Object();
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();

    private volatile boolean enabled;
    private volatile long updateIntervalTicks;
    private volatile DateTimeFormatter timeFormatter;
    private volatile List<String> headerLines;
    private volatile List<String> footerLines;
    private volatile LuckPerms luckPerms;
    private BukkitTask task;

    public TablistService(
        CraftedGatewayPlugin plugin,
        BukkitAudiences audiences,
        RadioNowPlayingService radioService
    ) {
        this.plugin = plugin;
        this.audiences = audiences;
        this.radioService = radioService;
        this.timeFormatter = DateTimeFormatter.ofPattern(DEFAULT_TIME_FORMAT, Locale.ENGLISH);
        this.headerLines = List.of(DEFAULT_HEADER);
        this.footerLines = List.of(DEFAULT_FOOTER);
        this.updateIntervalTicks = DEFAULT_UPDATE_TICKS;
        this.enabled = DEFAULT_ENABLED;
        refreshLuckPerms();
    }

    public void start() {
        reload();
    }

    public void stop() {
        cancelTask();
    }

    public void reload() {
        reloadFromConfig();
        scheduleTask();
    }

    private void reloadFromConfig() {
        enabled = plugin.getConfig().getBoolean("tablist.enabled", DEFAULT_ENABLED);
        updateIntervalTicks = Math.max(
            1,
            plugin.getConfig().getInt("tablist.update-interval-ticks", DEFAULT_UPDATE_TICKS)
        );
        String pattern = plugin.getConfig().getString("tablist.time-format", DEFAULT_TIME_FORMAT);
        timeFormatter = buildFormatter(pattern, DEFAULT_TIME_FORMAT);
        headerLines = normalizeLines(
            plugin.getConfig().getStringList("tablist.header"),
            DEFAULT_HEADER
        );
        footerLines = normalizeLines(
            plugin.getConfig().getStringList("tablist.footer"),
            DEFAULT_FOOTER
        );
        refreshLuckPerms();
    }

    private void refreshLuckPerms() {
        try {
            if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                luckPerms = LuckPermsProvider.get();
                return;
            }
        } catch (IllegalStateException ignored) {
            // LuckPerms not ready or not installed.
        }
        luckPerms = null;
    }

    private void scheduleTask() {
        cancelTask();
        if (!enabled) {
            return;
        }
        synchronized (taskLock) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, updateIntervalTicks);
        }
    }

    private void cancelTask() {
        synchronized (taskLock) {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }
    }

    private void updateAll() {
        if (!enabled) {
            return;
        }
        String time = timeFormatter.format(LocalTime.now());
        String song = radioService != null ? radioService.getLastSongText() : "";
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player, time, song);
        }
    }

    private void updatePlayer(Player player, String time, String song) {
        String prefix = getPrefix(player);
        Component prefixComponent = parsePrefixComponent(prefix);

        Component header = renderLines(
            headerLines,
            player,
            time,
            song,
            prefix,
            prefixComponent
        );
        Component footer = renderLines(
            footerLines,
            player,
            time,
            song,
            prefix,
            prefixComponent
        );
        audiences.player(player).sendPlayerListHeaderAndFooter(header, footer);
    }

    private Component renderLines(
        List<String> lines,
        Player player,
        String time,
        String song,
        String prefix,
        Component prefixComponent
    ) {
        if (lines.isEmpty()) {
            return Component.empty();
        }
        Component result = Component.empty();
        boolean first = true;
        for (String line : lines) {
            Component rendered = renderLine(line, player, time, song, prefix, prefixComponent);
            if (!first) {
                result = result.append(Component.newline());
            } else {
                first = false;
            }
            result = result.append(rendered);
        }
        return result;
    }

    private Component renderLine(
        String template,
        Player player,
        String time,
        String song,
        String prefix,
        Component prefixComponent
    ) {
        boolean legacyFormat = MessageTemplate.usesLegacyFormat(template);
        Object prefixValue = legacyFormat ? prefix : prefixComponent;
        return MessageTemplate.render(
            template,
            "player", player.getName(),
            "time", time,
            "song", song,
            "ping", Integer.toString(player.getPing()),
            "prefix", prefixValue
        );
    }

    private String getPrefix(Player player) {
        LuckPerms current = luckPerms;
        if (current == null) {
            return "";
        }
        User user = current.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return "";
        }
        String prefix = user.getCachedData().getMetaData().getPrefix();
        return prefix == null ? "" : prefix;
    }

    private Component parsePrefixComponent(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return Component.empty();
        }
        return legacySerializer.deserialize(prefix);
    }

    private static DateTimeFormatter buildFormatter(String pattern, String fallback) {
        String usePattern = (pattern == null || pattern.isBlank()) ? fallback : pattern;
        try {
            return DateTimeFormatter.ofPattern(usePattern, Locale.ENGLISH);
        } catch (IllegalArgumentException | DateTimeException ignored) {
            return DateTimeFormatter.ofPattern(fallback, Locale.ENGLISH);
        }
    }

    private static List<String> normalizeLines(List<String> lines, String fallback) {
        if (lines == null || lines.isEmpty()) {
            return List.of(fallback);
        }
        boolean hasContent = false;
        for (String line : lines) {
            if (line != null && !line.trim().isEmpty()) {
                hasContent = true;
                break;
            }
        }
        if (!hasContent) {
            return List.of(fallback);
        }
        return Collections.unmodifiableList(lines);
    }
}
