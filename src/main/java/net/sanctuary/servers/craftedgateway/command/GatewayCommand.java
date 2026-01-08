package net.sanctuary.servers.craftedgateway.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.sanctuary.servers.craftedgateway.CraftedGatewayPlugin;
import org.bukkit.command.CommandSender;

@CommandAlias("gateway|craftedgateway|cg")
@Description("CraftedGateway base command.")
public final class GatewayCommand extends BaseCommand {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final CraftedGatewayPlugin plugin;
    private final Component statusMessage;

    public GatewayCommand(CraftedGatewayPlugin plugin) {
        this.plugin = plugin;
        String version = plugin.getDescription().getVersion();
        this.statusMessage = MINI_MESSAGE.deserialize(
            "<gold>CraftedGateway</gold> <gray>v<yellow><version></yellow></gray> <green>online</green>",
            Placeholder.unparsed("version", version)
        );
    }

    @Default
    public void onDefault(CommandSender sender) {
        plugin.audiences().sender(sender).sendMessage(statusMessage);
    }
}
