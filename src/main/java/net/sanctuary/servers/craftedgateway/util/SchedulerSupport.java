package net.sanctuary.servers.craftedgateway.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SchedulerSupport {
    private SchedulerSupport() {
    }

    public static BukkitTask cancelTask(BukkitTask task) {
        if (task != null) {
            task.cancel();
        }
        return null;
    }

    public static BukkitTask rescheduleRepeating(
        JavaPlugin plugin,
        BukkitTask current,
        Runnable action,
        long delayTicks,
        long intervalTicks
    ) {
        cancelTask(current);
        return Bukkit.getScheduler().runTaskTimer(plugin, action, delayTicks, intervalTicks);
    }

    public static BukkitTask rescheduleAsyncLater(
        JavaPlugin plugin,
        BukkitTask current,
        Runnable action,
        long delayTicks
    ) {
        cancelTask(current);
        return Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, action, delayTicks);
    }
}
