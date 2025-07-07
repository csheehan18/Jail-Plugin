package com.corn.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.lib.PaperLib;

/**
 * Main plugin class for JailPlugin.
 */
public class JailPlugin extends JavaPlugin {

  /**
   * Called when the plugin is enabled.
   */
  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);
    saveDefaultConfig();
  }

  /**
   * Handles commands registered in plugin.yml.
   *
   * @param sender  the command sender
   * @param command the command object
   * @param label   the command alias used
   * @param args    command arguments
   * @return true if the command was handled, false otherwise
   */
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
    return false;
  }
}
