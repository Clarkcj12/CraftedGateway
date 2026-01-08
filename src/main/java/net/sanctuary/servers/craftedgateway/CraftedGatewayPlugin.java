package net.sanctuary.servers.craftedgateway;

import co.aikar.commands.BukkitCommandManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.sanctuary.servers.craftedgateway.command.GatewayCommand;
import net.sanctuary.servers.craftedgateway.command.VotdCommand;
import net.sanctuary.servers.craftedgateway.listener.VotdJoinListener;
import net.sanctuary.servers.craftedgateway.votd.VotdService;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftedGatewayPlugin extends JavaPlugin {
    private BukkitCommandManager commandManager;
    private BukkitAudiences audiences;
    private VotdService votdService;

    @Override
    public void onEnable() {
        String version = getDescription().getVersion();
        getLogger().info("CraftedGateway v" + version + " is starting...");
        saveDefaultConfig();
        audiences = BukkitAudiences.create(this);
        votdService = new VotdService(this, audiences);
        votdService.start();
        getServer().getPluginManager().registerEvents(new VotdJoinListener(votdService), this);
        commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new GatewayCommand(this));
        commandManager.registerCommand(new VotdCommand(this, votdService));
        getLogger().info("CraftedGateway v" + version + " is ready.");
    }

    @Override
    public void onDisable() {
        String version = getDescription().getVersion();
        if (votdService != null) {
            votdService.stop();
            votdService = null;
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        commandManager = null;
        getLogger().info("CraftedGateway v" + version + " has stopped.");
    }

    public BukkitAudiences audiences() {
        return audiences;
    }
}
