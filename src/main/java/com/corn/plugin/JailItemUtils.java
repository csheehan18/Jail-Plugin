package com.corn.plugin;

import java.util.Collections;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class JailItemUtils {

    /**
     * Creates a custom "Jail Stick" item with the given display name.
     * @param displayName the name to show on the stick
     * @return Jail stick item
     */
    public static ItemStack createJailStick(String displayName) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Hit a player to jail them."));
            stick.setItemMeta(meta);
        }
        return stick;
    }

    /**
     * Checks if the given item is the Jail Stick.
     * @param item Item to check
     * @param jailStickName The expected jail stick display name
     * @return true if it's the Jail Stick
     */
    public static boolean isJailStick(org.bukkit.inventory.ItemStack item, String jailStickName) {
        if (item == null || item.getType() != Material.STICK) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && jailStickName.equals(meta.getDisplayName());
    }
}
