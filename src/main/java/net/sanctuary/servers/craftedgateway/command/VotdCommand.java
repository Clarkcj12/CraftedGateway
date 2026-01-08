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
}
