package net.sanctuary.servers.craftedgateway.command;

import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;

public final class CommandSupport {
    private CommandSupport() {
    }

    public static void reloadConfigAndService(CraftedGatewayPlugin plugin, Runnable reloadAction) {
        plugin.reloadAndUpdateConfig();
        if (reloadAction != null) {
            reloadAction.run();
        }
    }

    public static void updateConfigFlag(CraftedGatewayPlugin plugin, String path, boolean value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
}
