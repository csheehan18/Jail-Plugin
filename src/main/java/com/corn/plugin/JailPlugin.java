package com.corn.plugin;

import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JailPlugin extends JavaPlugin {

    private final Map<UUID, Location> jailedPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("jail")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

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

        // /jail <player> <seconds>
        if (args.length == 2) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                player.sendMessage(ChatColor.RED + "Player not found or not online.");
                return true;
            }

            int jailTime;
            try {
                jailTime = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Invalid time. Use number of seconds.");
                return true;
            }

            if (!getConfig().contains("jail.world") ||
                !getConfig().contains("jail.x") ||
                !getConfig().contains("jail.y") ||
                !getConfig().contains("jail.z")) {
                player.sendMessage(ChatColor.RED + "Jail location not set. Use /jail setup first.");
                return true;
            }

            String worldName = getConfig().getString("jail.world");
            if (worldName == null || worldName.isEmpty()) {
                player.sendMessage(ChatColor.RED + "Jail world is not set.");
                return true;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                player.sendMessage(ChatColor.RED + "Jail world '" + worldName + "' is not loaded.");
                return true;
            }

            double x = getConfig().getDouble("jail.x");
            double y = getConfig().getDouble("jail.y");
            double z = getConfig().getDouble("jail.z");
            float yaw = (float) getConfig().getDouble("jail.yaw");
            float pitch = (float) getConfig().getDouble("jail.pitch");

            Location jailLoc = new Location(world, x, y, z, yaw, pitch);
            Location originalLoc = target.getLocation();
            jailedPlayers.put(target.getUniqueId(), originalLoc);

            target.teleport(jailLoc);
            target.sendMessage(ChatColor.RED + "You have been jailed for " + jailTime + " seconds.");
            player.sendMessage(ChatColor.YELLOW + target.getName() + " jailed for " + jailTime + " seconds.");

            // Schedule unjail
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location original = jailedPlayers.remove(target.getUniqueId());
                    if (original != null && target.isOnline()) {
                        target.teleport(original);
                        target.sendMessage(ChatColor.GREEN + "You have been released from jail.");
                    }
                }
            }.runTaskLater(this, jailTime * 20L); // 20 ticks/sec

            return true;
        }

        // Help or fallback
        player.sendMessage(ChatColor.YELLOW + "Usage:");
        player.sendMessage(ChatColor.YELLOW + "/jail setup");
        player.sendMessage(ChatColor.YELLOW + "/jail <player> <seconds>");
        return true;
    }
}
