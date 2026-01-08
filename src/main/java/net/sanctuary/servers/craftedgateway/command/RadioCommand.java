package net.sanctuary.servers.craftedgateway.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.radio.RadioNowPlayingService;
import org.bukkit.command.CommandSender;

@CommandAlias("radio")
@Description("Radio now playing controls.")
public final class RadioCommand extends BaseCommand {
    private final CraftedGatewayPlugin plugin;
    private final RadioNowPlayingService radioService;

    public RadioCommand(CraftedGatewayPlugin plugin, RadioNowPlayingService radioService) {
        this.plugin = plugin;
        this.radioService = radioService;
    }

    @Subcommand("reload")
    @CommandPermission("craftedgateway.radio.reload")
    @Description("Reload the radio configuration.")
    public void onReload(CommandSender sender) {
        plugin.reloadAndUpdateConfig();
        radioService.reload();
        sender.sendMessage("Radio configuration reloaded.");
    }

    @Subcommand("announcement enable")
    @CommandPermission("craftedgateway.radio.announce")
    @Description("Enable radio now playing announcements.")
    public void onAnnouncementEnable(CommandSender sender) {
        updateAnnouncementEnabled(true);
        sender.sendMessage("Radio announcements enabled.");
    }

    @Subcommand("announcement disable")
    @CommandPermission("craftedgateway.radio.announce")
    @Description("Disable radio now playing announcements.")
    public void onAnnouncementDisable(CommandSender sender) {
        updateAnnouncementEnabled(false);
        sender.sendMessage("Radio announcements disabled.");
    }

    private void updateAnnouncementEnabled(boolean enabled) {
        plugin.getConfig().set("radio.announcement-enabled", enabled);
        plugin.saveConfig();
        radioService.setAnnouncementEnabled(enabled);
    }
}
