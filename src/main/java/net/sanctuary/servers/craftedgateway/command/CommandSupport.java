package net.sanctuary.servers.craftedgateway.command;

import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import org.bukkit.command.CommandSender;

public final class CommandSupport {
    private CommandSupport() {
    }

    public static void reloadConfigAndService(CraftedGatewayPlugin plugin, Runnable reloadAction) {
        plugin.reloadAndUpdateConfig();
        if (reloadAction != null) {
            reloadAction.run();
        }
    }

    public static void reloadConfigAndNotify(
        CraftedGatewayPlugin plugin,
        Runnable reloadAction,
        CommandSender sender,
        String message
    ) {
        reloadConfigAndService(plugin, reloadAction);
        if (sender != null && message != null) {
            sender.sendMessage(message);
        }
    }

    public static void reloadAndNotify(
        CommandSender sender,
        String message,
        Runnable reloadAction
    ) {
        if (reloadAction != null) {
            reloadAction.run();
        }
        if (sender != null && message != null) {
            sender.sendMessage(message);
        }
    }

    public static void updateConfigFlag(CraftedGatewayPlugin plugin, String path, boolean value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
}
