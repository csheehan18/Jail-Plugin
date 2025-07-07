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
        if (command.getName().equalsIgnoreCase("hello")) {
            if (sender instanceof Player) {
                sender.sendMessage("Hello!");
                return true;
            } else {
                sender.sendMessage("This command can only be run by a player.");
                return true;
            }
        }
        return false; // command not recognized by this plugin
    }
}
