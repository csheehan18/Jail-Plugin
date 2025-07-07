package com.corn.plugin;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class JailHitListener implements Listener {

    private final JailPlugin plugin;

    @SuppressWarnings("EI_EXPOSE_REP2")
    public JailHitListener(JailPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof Player target)) return;

        ItemStack item = damager.getInventory().getItemInMainHand();
        if (item.getType() != Material.STICK) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(
                new NamespacedKey(plugin, "jail_stick"),
                PersistentDataType.BYTE)) {
            return;
        }

        // Jail the target
        plugin.jailPlayer(target, plugin.defaultJailTime);
        damager.sendMessage(ChatColor.GREEN + "You jailed " + target.getName() + " for " + plugin.defaultJailTime + " seconds!");
        target.sendMessage(ChatColor.RED + "You've been jailed by " + damager.getName() + " for " + plugin.defaultJailTime + " seconds!");
    }
}

