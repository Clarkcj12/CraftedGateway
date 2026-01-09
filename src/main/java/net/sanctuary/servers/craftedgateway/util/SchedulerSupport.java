package net.sanctuary.servers.craftedgateway.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class SchedulerSupport {
    private SchedulerSupport() {
    }

    public static void cancelTask(BukkitTask task) {
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
    }

    public static <T extends BukkitTask> T cancelAndClearTask(T task) {
        cancelTask(task);
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

    public static BukkitTask rescheduleRepeatingIfEnabled(
        JavaPlugin plugin,
        BukkitTask current,
        Runnable action,
        long delayTicks,
        long intervalTicks,
        boolean enabled
    ) {
        if (!enabled) {
            return cancelAndClearTask(current);
        }
        return rescheduleRepeating(plugin, current, action, delayTicks, intervalTicks);
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

    public static BukkitTask rescheduleAsyncLaterIfEnabled(
        JavaPlugin plugin,
        BukkitTask current,
        Runnable action,
        long delayTicks,
        boolean enabled
    ) {
        if (!enabled) {
            return cancelAndClearTask(current);
        }
        return rescheduleAsyncLater(plugin, current, action, delayTicks);
    }
}
