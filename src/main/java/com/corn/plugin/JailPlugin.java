package com.corn.plugin;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.lib.PaperLib;

public class JailPlugin extends JavaPlugin {

  @Override
  public void onEnable() {
    PaperLib.suggestPaper(this);

    saveDefaultConfig();
  }
}
