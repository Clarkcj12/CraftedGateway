package net.sanctuary.servers.craftedgateway.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import net.kyori.adventure.text.format.NamedTextColor;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import net.sanctuary.servers.craftedgateway.votd.VotdService;
import org.bukkit.command.CommandSender;

@CommandAlias("votd|verseoftheday|bibleverse")
@Description("Show the Bible verse of the day.")
public final class VotdCommand extends BaseCommand {
    private final CraftedGatewayPlugin plugin;
    private final VotdService votdService;

    public VotdCommand(CraftedGatewayPlugin plugin, VotdService votdService) {
        this.plugin = plugin;
        this.votdService = votdService;
    }

    @Default
    public void onDefault(CommandSender sender) {
        votdService.sendVerse(sender);
    }

    @Subcommand("reload")
    @CommandPermission("craftedgateway.votd.reload")
    @Description("Reload the VOTD configuration.")
    public void onReload(CommandSender sender) {
        plugin.reloadAndUpdateConfig();
        votdService.reload();
        sender.sendMessage(NamedTextColor.GREEN + "VOTD configuration reloaded.");
    }

    @Subcommand("join enable")
    @CommandPermission("craftedgateway.votd.join")
    @Description("Enable VOTD join messages.")
    public void onJoinEnable(CommandSender sender) {
        updateConfigFlag("votd.join-enabled", true);
        votdService.reload();
        sender.sendMessage(NamedTextColor.GREEN + "VOTD join messages enabled.");
    }

    @Subcommand("join disable")
    @CommandPermission("craftedgateway.votd.join")
    @Description("Disable VOTD join messages.")
    public void onJoinDisable(CommandSender sender) {
        updateConfigFlag("votd.join-enabled", false);
        votdService.reload();
        sender.sendMessage(NamedTextColor.GREEN + "VOTD join messages disabled.");
    }

    @Subcommand("announcement enable")
    @CommandPermission("craftedgateway.votd.announce")
    @Description("Enable scheduled VOTD announcements.")
    public void onAnnouncementEnable(CommandSender sender) {
        updateConfigFlag("votd.announcement-enabled", true);
        votdService.reload();
        sender.sendMessage(NamedTextColor.GREEN + "VOTD announcements enabled.");
    }

    @Subcommand("announcement disable")
    @CommandPermission("craftedgateway.votd.announce")
    @Description("Disable scheduled VOTD announcements.")
    public void onAnnouncementDisable(CommandSender sender) {
        updateConfigFlag("votd.announcement-enabled", false);
        votdService.reload();
        sender.sendMessage(NamedTextColor.GREEN + "VOTD announcements disabled.");
    }

    private void updateConfigFlag(String path, boolean value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
}
