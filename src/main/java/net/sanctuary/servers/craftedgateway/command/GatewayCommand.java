package net.sanctuary.servers.craftedgateway.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import org.bukkit.command.CommandSender;

@CommandAlias("gateway|craftedgateway|cg")
@Description("CraftedGateway base command.")
public final class GatewayCommand extends BaseCommand {
    private final String statusMessage;

    public GatewayCommand(CraftedGatewayPlugin plugin) {
        this.statusMessage = "CraftedGateway " + plugin.getDescription().getVersion() + " is running.";
    }

    @Default
    public void onDefault(CommandSender sender) {
        sender.sendMessage(statusMessage);
    }
}
