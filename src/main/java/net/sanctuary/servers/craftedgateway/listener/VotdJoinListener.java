package net.sanctuary.servers.craftedgateway.listener;

import net.sanctuary.servers.craftedgateway.votd.VotdService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class VotdJoinListener implements Listener {
    private final VotdService votdService;

    public VotdJoinListener(VotdService votdService) {
        this.votdService = votdService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        votdService.sendJoinVerse(event.getPlayer());
    }
}
