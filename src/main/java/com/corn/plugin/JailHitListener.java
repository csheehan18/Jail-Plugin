package com.corn.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class JailHitListener implements Listener {

    private final JailPlugin plugin;

    public JailHitListener(JailPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        ItemStack item = damager.getInventory().getItemInMainHand();
        if (item.getType() != Material.STICK) return;

        if (!JailItemUtils.isJailStick(item, plugin.getJailStickName())) return;

        // Jail the target using default duration from config
        boolean success = plugin.jailPlayer(target, plugin.getConfig().getInt("jail.duration", 10));
        if (success) {
            damager.sendMessage(ChatColor.GREEN + "You jailed " + target.getName() + "!");
            target.sendMessage(ChatColor.RED + "You've been jailed by " + damager.getName() + "!");
        } else {
            damager.sendMessage(ChatColor.RED + "Failed to jail player. Jail location not set.");
        }
    }
}
