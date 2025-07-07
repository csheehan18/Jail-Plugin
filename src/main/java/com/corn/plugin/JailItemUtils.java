package com.corn.plugin;

import java.util.Collections;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class JailItemUtils {

    public static final String JAIL_ITEM_NAME = ChatColor.RED + "Jail Stick";

    /**
     * Creates a custom "Jail Stick" item.
     * @return Jail stick item
     */
    public static ItemStack createJailStick() {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(JAIL_ITEM_NAME);
            meta.setLore(Collections.singletonList(ChatColor.GRAY + "Hit a player to jail them."));
            stick.setItemMeta(meta);
        }
        return stick;
    }

    /**
     * Checks if the given item is the Jail Stick.
     * @param item Item to check
     * @return true if it's the Jail Stick
     */
    public static boolean isJailStick(ItemStack item) {
        if (item == null || item.getType() != Material.STICK) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName() && JAIL_ITEM_NAME.equals(meta.getDisplayName());
    }
}
