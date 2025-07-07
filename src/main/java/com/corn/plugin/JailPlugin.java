package com.corn.plugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.lib.PaperLib;

public class JailPlugin extends JavaPlugin {

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
            saveConfig();
            player.sendMessage(ChatColor.GREEN + "Jail location set to your current position.");
            return true;
        }

        // /jail <player>
        if (args.length == 1) {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Player not found.");
                return true;
            }

            if (!getConfig().contains("jail.world")) {
                player.sendMessage(ChatColor.RED + "Jail location has not been set yet.");
                return true;
            }

            World world = Bukkit.getWorld(getConfig().getString("jail.world"));
            double x = getConfig().getDouble("jail.x");
            double y = getConfig().getDouble("jail.y");
            double z = getConfig().getDouble("jail.z");

            if (world == null) {
                player.sendMessage(ChatColor.RED + "Jail world does not exist.");
                return true;
            }

            Location jailLoc = new Location(world, x, y, z);
            target.teleport(jailLoc);

            player.sendMessage(ChatColor.GREEN + "Teleported " + target.getName() + " to the jail.");
            target.sendMessage(ChatColor.RED + "You have been jailed by " + player.getName() + "!");
            return true;
        }

        player.sendMessage(ChatColor.YELLOW + "Usage: /jail setup or /jail <player>");
        return true;
    }
}
