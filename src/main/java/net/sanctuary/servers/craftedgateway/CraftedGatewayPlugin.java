package net.sanctuary.servers.craftedgateway;

import co.aikar.commands.BukkitCommandManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sanctuary.servers.craftedgateway.command.GatewayCommand;
import net.sanctuary.servers.craftedgateway.command.RadioCommand;
import net.sanctuary.servers.craftedgateway.command.VotdCommand;
import net.sanctuary.servers.craftedgateway.listener.VotdJoinListener;
import net.sanctuary.servers.craftedgateway.metrics.MetricsService;
import net.sanctuary.servers.craftedgateway.radio.RadioNowPlayingService;
import net.sanctuary.servers.craftedgateway.tablist.TablistService;
import net.sanctuary.servers.craftedgateway.votd.VotdService;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftedGatewayPlugin extends JavaPlugin {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final String CONSOLE_TEMPLATE =
        "<gold>[CraftedGateway]</gold> <gray>v<yellow><version></yellow></gray> <state>";
    private BukkitCommandManager commandManager;
    private BukkitAudiences audiences;
    private VotdService votdService;
    private RadioNowPlayingService radioService;
    private TablistService tablistService;
    private MetricsService metricsService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadAndUpdateConfig();
        audiences = BukkitAudiences.create(this);
        String version = getDescription().getVersion();
        sendConsoleStatus(version, Component.text("starting").color(NamedTextColor.YELLOW));
        getLogger().info("CraftedGateway v" + version + " is starting...");
        metricsService = new MetricsService(this);
        votdService = new VotdService(this, audiences, metricsService);
        votdService.start();
        radioService = new RadioNowPlayingService(this, audiences, metricsService);
        radioService.start();
        tablistService = new TablistService(this, audiences, radioService, metricsService);
        tablistService.start();
        metricsService.setServices(votdService, radioService, tablistService);
        metricsService.start();
        getServer().getPluginManager().registerEvents(new VotdJoinListener(votdService), this);
        commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new GatewayCommand(this));
        commandManager.registerCommand(new VotdCommand(this, votdService));
        commandManager.registerCommand(new RadioCommand(this, radioService));
        sendConsoleStatus(version, Component.text("ready").color(NamedTextColor.GREEN));
        getLogger().info("CraftedGateway v" + version + " is ready.");
    }

    @Override
    public void onDisable() {
        String version = getDescription().getVersion();
        if (votdService != null) {
            votdService.stop();
            votdService = null;
        }
        if (radioService != null) {
            radioService.stop();
            radioService = null;
        }
        if (tablistService != null) {
            tablistService.stop();
            tablistService = null;
        }
        if (metricsService != null) {
            metricsService.stop();
            metricsService = null;
        }
        if (audiences != null) {
            sendConsoleStatus(version, Component.text("stopped").color(NamedTextColor.RED));
            audiences.close();
            audiences = null;
        }
        commandManager = null;
        getLogger().info("CraftedGateway v" + version + " has stopped.");
    }

    public BukkitAudiences audiences() {
        return audiences;
    }

    public void reloadAndUpdateConfig() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    public void reloadAll() {
        reloadAndUpdateConfig();
        if (votdService != null) {
            votdService.reload();
        }
        if (radioService != null) {
            radioService.reload();
        }
        if (tablistService != null) {
            tablistService.reload();
        }
        if (metricsService != null) {
            metricsService.reload();
        }
    }

    private void sendConsoleStatus(String version, Component state) {
        if (audiences == null) {
            return;
        }
        audiences.console().sendMessage(
            MINI_MESSAGE.deserialize(
                CONSOLE_TEMPLATE,
                Placeholder.unparsed("version", version),
                Placeholder.component("state", state)
            )
        );
    }
}
