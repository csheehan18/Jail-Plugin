package com.corn.plugin;

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
    if ("hello".equalsIgnoreCase(command.getName())) {
      if (sender instanceof Player) {
        sender.sendMessage("Hello!");
      } else {
        sender.sendMessage("This command can only be run by a player.");
      }
      return true;
    }
    
    if ("setupJail".equalsIgnoreCase(command.getName())) {
      if (!(sender instanceof Player)) {
        sender.sendMessage("This command can only be run by a player.");
        return true;
      }
      Player player = (Player) sender;
      // Get player location
      var loc = player.getLocation();

      // Save coordinates to config
      getConfig().set("jail.world", loc.getWorld().getName());
      getConfig().set("jail.x", loc.getX());
      getConfig().set("jail.y", loc.getY());
      getConfig().set("jail.z", loc.getZ());
      saveConfig();

      player.sendMessage("Jail location saved at your current position!");
      return true;
    }

    return false;
  }
}
