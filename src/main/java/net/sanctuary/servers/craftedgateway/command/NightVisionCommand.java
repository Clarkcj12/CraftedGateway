package net.sanctuary.servers.craftedgateway.command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CommandAlias("nv|nightvision")
@Description("Toggle night vision effect.")
@CommandPermission("craftedgateway.nightvision.self")
public final class NightVisionCommand extends BaseCommand {
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([smh])$");
    private static final int TICKS_PER_SECOND = 20;

    public NightVisionCommand() {
    }

    @Default
    @CommandCompletion("@players")
    public void onDefault(CommandSender sender, @Optional String arg1, @Optional String arg2) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(NamedTextColor.RED + "This command can only be used by players.");
            return;
        }

        Player player = (Player) sender;

        if (arg1 == null) {
            toggleNightVision(player, player, -1);
            return;
        }

        Integer duration = parseDuration(arg1);
        if (duration != null) {
            if (!sender.hasPermission("craftedgateway.nightvision.duration")) {
                sender.sendMessage(NamedTextColor.RED + "You don't have permission to use durations.");
                return;
            }
            applyNightVision(player, player, duration);
            return;
        }

        Player target = Bukkit.getPlayer(arg1);
        if (target != null) {
            if (!sender.hasPermission("craftedgateway.nightvision.others")) {
                sender.sendMessage(NamedTextColor.RED + "You don't have permission to toggle night vision for others.");
                return;
            }

            if (arg2 != null) {
                Integer targetDuration = parseDuration(arg2);
                if (targetDuration != null) {
                    if (!sender.hasPermission("craftedgateway.nightvision.duration")) {
                        sender.sendMessage(NamedTextColor.RED + "You don't have permission to use durations.");
                        return;
                    }
                    applyNightVision(player, target, targetDuration);
                } else {
                    sender.sendMessage(NamedTextColor.RED + "Invalid duration format. Use formats like 30s, 5m, or 1h.");
                }
            } else {
                toggleNightVision(player, target, -1);
            }
            return;
        }

        sender.sendMessage(NamedTextColor.RED + "Player not found: " + arg1);
    }

    @Subcommand("on")
    @Description("Enable night vision.")
    public void onEnable(CommandSender sender, @Optional String durationArg) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(NamedTextColor.RED + "This command can only be used by players.");
            return;
        }

        Player player = (Player) sender;

        if (durationArg != null) {
            Integer duration = parseDuration(durationArg);
            if (duration != null) {
                if (!sender.hasPermission("craftedgateway.nightvision.duration")) {
                    sender.sendMessage(NamedTextColor.RED + "You don't have permission to use durations.");
                    return;
                }
                applyNightVision(player, player, duration);
            } else {
                sender.sendMessage(NamedTextColor.RED + "Invalid duration format. Use formats like 30s, 5m, or 1h.");
            }
        } else {
            applyNightVision(player, player, Integer.MAX_VALUE);
        }
    }

    @Subcommand("off")
    @Description("Disable night vision.")
    public void onDisable(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(NamedTextColor.RED + "This command can only be used by players.");
            return;
        }

        Player player = (Player) sender;
        removeNightVision(player, player);
    }

    private void toggleNightVision(Player sender, Player target, int durationTicks) {
        if (target.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            removeNightVision(sender, target);
        } else {
            int duration = durationTicks > 0 ? durationTicks : Integer.MAX_VALUE;
            applyNightVision(sender, target, duration);
        }
    }

    private void applyNightVision(Player sender, Player target, int durationTicks) {
        target.addPotionEffect(new PotionEffect(
            PotionEffectType.NIGHT_VISION,
            durationTicks,
            0,
            false,
            false,
            true
        ));

        if (sender.equals(target)) {
            if (durationTicks == Integer.MAX_VALUE) {
                sender.sendMessage(NamedTextColor.GREEN + "Night vision enabled.");
            } else {
                sender.sendMessage(NamedTextColor.GREEN + "Night vision enabled for " + formatDuration(durationTicks) + ".");
            }
        } else {
            if (durationTicks == Integer.MAX_VALUE) {
                sender.sendMessage(NamedTextColor.GREEN + "Night vision enabled for " + target.getName() + ".");
            } else {
                sender.sendMessage(NamedTextColor.GREEN + "Night vision enabled for " + target.getName() + " for " + formatDuration(durationTicks) + ".");
            }
            target.sendMessage(NamedTextColor.GREEN + "Night vision has been enabled for you.");
        }
    }

    private void removeNightVision(Player sender, Player target) {
        target.removePotionEffect(PotionEffectType.NIGHT_VISION);

        if (sender.equals(target)) {
            sender.sendMessage(NamedTextColor.YELLOW + "Night vision disabled.");
        } else {
            sender.sendMessage(NamedTextColor.YELLOW + "Night vision disabled for " + target.getName() + ".");
            target.sendMessage(NamedTextColor.YELLOW + "Night vision has been disabled for you.");
        }
    }

    private Integer parseDuration(String input) {
        Matcher matcher = DURATION_PATTERN.matcher(input.toLowerCase());
        if (!matcher.matches()) {
            return null;
        }

        int value = Integer.parseInt(matcher.group(1));
        String unit = matcher.group(2);

        int seconds = switch (unit) {
            case "s" -> value;
            case "m" -> value * 60;
            case "h" -> value * 3600;
            default -> 0;
        };

        return seconds * TICKS_PER_SECOND;
    }

    private String formatDuration(int ticks) {
        int totalSeconds = ticks / TICKS_PER_SECOND;

        if (totalSeconds >= 3600) {
            int hours = totalSeconds / 3600;
            return hours + " hour" + (hours != 1 ? "s" : "");
        } else if (totalSeconds >= 60) {
            int minutes = totalSeconds / 60;
            return minutes + " minute" + (minutes != 1 ? "s" : "");
        } else {
            return totalSeconds + " second" + (totalSeconds != 1 ? "s" : "");
        }
    }
}
