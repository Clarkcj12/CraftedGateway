package net.sanctuary.servers.craftedgateway;

import org.bukkit.plugin.java.JavaPlugin;

public final class CraftedGatewayPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("CraftedGateway enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("CraftedGateway disabled.");
    }
}
