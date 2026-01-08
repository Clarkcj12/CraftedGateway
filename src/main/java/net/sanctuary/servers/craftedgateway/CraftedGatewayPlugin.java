package net.sanctuary.servers.craftedgateway;

import co.aikar.commands.BukkitCommandManager;
import net.sanctuary.servers.craftedgateway.command.GatewayCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CraftedGatewayPlugin extends JavaPlugin {
    private BukkitCommandManager commandManager;

    @Override
    public void onEnable() {
        commandManager = new BukkitCommandManager(this);
        commandManager.registerCommand(new GatewayCommand(this));
        getLogger().info("CraftedGateway enabled.");
    }

    @Override
    public void onDisable() {
        commandManager = null;
        getLogger().info("CraftedGateway disabled.");
    }
}
