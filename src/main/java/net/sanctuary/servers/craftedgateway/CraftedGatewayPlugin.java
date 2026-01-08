package net.sanctuary.servers.craftedgateway;

import co.aikar.commands.BukkitCommandManager;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import net.sanctuary.servers.craftedgateway.command.GatewayCommand;
import net.sanctuary.servers.craftedgateway.command.VotdCommand;
import net.sanctuary.servers.craftedgateway.votd.VotdService;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftedGatewayPlugin extends JavaPlugin {
    private BukkitCommandManager commandManager;
    private BukkitAudiences audiences;
    private VotdService votdService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        audiences = BukkitAudiences.create(this);
        votdService = new VotdService(this, audiences);
        votdService.start();
        commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new GatewayCommand(this));
        commandManager.registerCommand(new VotdCommand(this, votdService));
        getLogger().info("CraftedGateway enabled.");
    }

    @Override
    public void onDisable() {
        if (votdService != null) {
            votdService.stop();
            votdService = null;
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        commandManager = null;
        getLogger().info("CraftedGateway disabled.");
    }

    public BukkitAudiences audiences() {
        return audiences;
    }
}
