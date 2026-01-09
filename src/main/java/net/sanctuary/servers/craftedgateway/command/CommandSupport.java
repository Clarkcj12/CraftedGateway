package net.sanctuary.servers.craftedgateway.command;

import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import org.bukkit.command.CommandSender;

import java.util.Objects;

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
        CommandSender sender,
        String message,
        Runnable reloadAction
    ) {
        Objects.requireNonNull(plugin, "plugin must not be null");
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(reloadAction, "reloadAction must not be null");
        reloadConfigAndService(plugin, reloadAction);
        sender.sendMessage(message);
    }

    public static void runActionAndNotify(
        CommandSender sender,
        String message,
        Runnable action
    ) {
        Objects.requireNonNull(sender, "sender must not be null");
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(action, "action must not be null");
        action.run();
        sender.sendMessage(message);
    }

    public static void updateConfigFlag(CraftedGatewayPlugin plugin, String path, boolean value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
}
