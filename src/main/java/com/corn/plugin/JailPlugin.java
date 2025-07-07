package com.corn.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import io.papermc.lib.PaperLib;

public class JailPlugin extends JavaPlugin {

    private final Map<UUID, Location> jailedPlayers = new HashMap<>();

    // Constants read from config
    private String jailStickName;
    private int defaultJailDuration;

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();

        jailStickName = ChatColor.translateAlternateColorCodes('&', getConfig().getString("jail.stickName", "&cJail Stick"));
        defaultJailDuration = getConfig().getInt("jail.duration", 10);

        // Register listener
        getServer().getPluginManager().registerEvents(new JailHitListener(this), this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("jail") && !command.getName().equalsIgnoreCase("jailstick"))
            return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("jail")) {
            // /jail setup
            if (args.length == 1 && args[0].equalsIgnoreCase("setup")) {
                Location loc = player.getLocation();
                getConfig().set("jail.world", loc.getWorld().getName());
                getConfig().set("jail.x", loc.getX());
                getConfig().set("jail.y", loc.getY());
                getConfig().set("jail.z", loc.getZ());
                getConfig().set("jail.yaw", loc.getYaw());
                getConfig().set("jail.pitch", loc.getPitch());
                saveConfig();
                player.sendMessage(ChatColor.GREEN + "Jail location saved.");
                return true;
            }

            // /jail <player> [seconds]
            if (args.length >= 1) {
                Player target = Bukkit.getPlayer(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(ChatColor.RED + "Player not found or not online.");
                    return true;
                }

                int jailTime = defaultJailDuration;
                if (args.length == 2) {
                    try {
                        jailTime = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage(ChatColor.RED + "Invalid time. Using default: " + defaultJailDuration + " seconds.");
                    }
                }

                boolean success = jailPlayer(target, jailTime);
                if (success) {
                    player.sendMessage(ChatColor.YELLOW + target.getName() + " jailed for " + jailTime + " seconds.");
                } else {
                    player.sendMessage(ChatColor.RED + "Failed to jail player. Is jail location set?");
                }

                return true;
            }

            // Help fallback
            player.sendMessage(ChatColor.YELLOW + "Usage:");
            player.sendMessage(ChatColor.YELLOW + "/jail setup");
            player.sendMessage(ChatColor.YELLOW + "/jail <player> [seconds]");
            return true;
        }

        // /jailstick <player> command
        if (command.getName().equalsIgnoreCase("jailstick")) {
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /jailstick <player>");
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Player not found or not online.");
                return true;
            }

            target.getInventory().addItem(JailItemUtils.createJailStick(jailStickName));
            player.sendMessage(ChatColor.GREEN + "Gave " + target.getName() + " a Jail Stick.");
            return true;
        }

        return false;
    }

    /**
     * Jails the specified player for a number of seconds.
     * Returns false if jail config is invalid.
     */
    public boolean jailPlayer(Player target, int seconds) {
        if (!getConfig().contains("jail.world") ||
            !getConfig().contains("jail.x") ||
            !getConfig().contains("jail.y") ||
            !getConfig().contains("jail.z")) {
            return false;
        }

        String worldName = getConfig().getString("jail.world");
        if (worldName == null || worldName.isEmpty()) return false;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        double x = getConfig().getDouble("jail.x");
        double y = getConfig().getDouble("jail.y");
        double z = getConfig().getDouble("jail.z");
        float yaw = (float) getConfig().getDouble("jail.yaw");
        float pitch = (float) getConfig().getDouble("jail.pitch");

        Location jailLoc = new Location(world, x, y, z, yaw, pitch);
        Location originalLoc = target.getLocation();
        jailedPlayers.put(target.getUniqueId(), originalLoc);

        target.teleport(jailLoc);
        target.sendMessage(ChatColor.RED + "You have been jailed for " + seconds + " seconds.");

        new BukkitRunnable() {
            @Override
            public void run() {
                Location original = jailedPlayers.remove(target.getUniqueId());
                if (original != null && target.isOnline()) {
                    target.teleport(original);
                    target.sendMessage(ChatColor.GREEN + "You have been released from jail.");
                }
            }
        }.runTaskLater(this, seconds * 20L); // 20 ticks per second

        return true;
    }

    public String getJailStickName() {
        return jailStickName;
    }
}
