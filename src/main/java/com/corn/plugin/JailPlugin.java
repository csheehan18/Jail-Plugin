package com.corn.plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.lib.PaperLib;

public class JailPlugin extends JavaPlugin implements Listener {

    // Stores jailed players and their original locations
    private final Map<UUID, Location> jailedPlayers = new HashMap<>();

    // Stores scheduled unjail tasks per player
    private final Map<UUID, BukkitTask> jailTasks = new HashMap<>();

    // Stores jail end timestamps (milliseconds)
    private final Map<UUID, Long> jailEndTimes = new HashMap<>();

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);

        loadJailedPlayersFromConfig();
    }

    @Override
    public void onDisable() {
        // Cancel all scheduled jail tasks on shutdown
        for (BukkitTask task : jailTasks.values()) {
            task.cancel();
        }
        jailTasks.clear();
        jailedPlayers.clear();

        // Save jailed players and remaining times
        saveJailedPlayersToConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("jail")) return false;

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

            boolean success = jailPlayer(target, jailTime);
            if (success) {
                player.sendMessage(ChatColor.YELLOW + target.getName() + " jailed for " + jailTime + " seconds.");
            } else {
                player.sendMessage(ChatColor.RED + "Failed to jail player. Is jail location set?");
            }

            return true;
        }

        // Help message
        player.sendMessage(ChatColor.YELLOW + "Usage:");
        player.sendMessage(ChatColor.YELLOW + "/jail setup");
        player.sendMessage(ChatColor.YELLOW + "/jail <player> <seconds>");
        return true;
    }

    /**
     * Jails the specified player for the specified seconds.
     * Saves their original location and schedules unjail.
     * Returns false if jail location is not set correctly.
     */
    public boolean jailPlayer(Player target, int seconds) {
        if (!getConfig().contains("jail.world") ||
            !getConfig().contains("jail.x") ||
            !getConfig().contains("jail.y") ||
            !getConfig().contains("jail.z")) {
            return false;
        }

        String worldName = getConfig().getString("jail.world");
        if (worldName == null || worldName.isEmpty()) return false;

        World world = Bukkit.getWorld(worldName);
        if (world == null) return false;

        double x = getConfig().getDouble("jail.x");
        double y = getConfig().getDouble("jail.y");
        double z = getConfig().getDouble("jail.z");
        float yaw = (float) getConfig().getDouble("jail.yaw");
        float pitch = (float) getConfig().getDouble("jail.pitch");

        Location jailLoc = new Location(world, x, y, z, yaw, pitch);
        Location originalLoc = target.getLocation();

        UUID uuid = target.getUniqueId();

        jailedPlayers.put(uuid, originalLoc);

        // Cancel existing task if any
        if (jailTasks.containsKey(uuid)) {
            jailTasks.get(uuid).cancel();
        }

        target.teleport(jailLoc);
        target.sendMessage(ChatColor.RED + "You have been jailed for " + seconds + " seconds.");

        long jailEndTime = System.currentTimeMillis() + seconds * 1000L;
        jailEndTimes.put(uuid, jailEndTime);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                unjailPlayer(target);
            }
        }.runTaskLater(this, seconds * 20L);

        jailTasks.put(uuid, task);

        // Save jail state to config immediately
        saveJailedPlayersToConfig();

        return true;
    }

    /**
     * Unjails the player, teleporting them back and clearing data.
     */
    public void unjailPlayer(Player player) {
        UUID uuid = player.getUniqueId();

        Location original = jailedPlayers.remove(uuid);
        if (original != null && player.isOnline()) {
            player.teleport(original);
            player.sendMessage(ChatColor.GREEN + "You have been released from jail.");
        }

        jailEndTimes.remove(uuid);

        if (jailTasks.containsKey(uuid)) {
            jailTasks.get(uuid).cancel();
            jailTasks.remove(uuid);
        }

        saveJailedPlayersToConfig();
    }

    /**
     * Load jailed players and remaining jail time from config.
     * Teleports players back to jail if online and reschedules tasks.
     */
    private void loadJailedPlayersFromConfig() {
        if (!getConfig().isConfigurationSection("jailed")) return;

        for (String key : getConfig().getConfigurationSection("jailed").getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "jailed." + key + ".";

                World world = Bukkit.getWorld(getConfig().getString(path + "world"));
                double x = getConfig().getDouble(path + "x");
                double y = getConfig().getDouble(path + "y");
                double z = getConfig().getDouble(path + "z");
                float yaw = (float) getConfig().getDouble(path + "yaw");
                float pitch = (float) getConfig().getDouble(path + "pitch");
                Location originalLoc = new Location(world, x, y, z, yaw, pitch);

                long jailEndTime = getConfig().getLong(path + "endTime");

                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    long now = System.currentTimeMillis();
                    long remainingMillis = jailEndTime - now;

                    if (remainingMillis > 0) {
                        jailedPlayers.put(uuid, originalLoc);
                        player.teleport(getJailLocation());
                        player.sendMessage(ChatColor.RED + "You are still jailed!");

                        // Schedule unjail for remaining time
                        BukkitTask task = new BukkitRunnable() {
                            @Override
                            public void run() {
                                unjailPlayer(player);
                            }
                        }.runTaskLater(this, remainingMillis / 50L); // 1 tick = 50ms

                        jailTasks.put(uuid, task);
                        jailEndTimes.put(uuid, jailEndTime);
                    } else {
                        // Jail time expired while offline, clear saved state
                        unjailPlayer(player);
                    }
                } else {
                    // Player offline, keep data, will be checked on join
                    jailedPlayers.put(uuid, originalLoc);
                    jailEndTimes.put(uuid, jailEndTime);
                }
            } catch (Exception e) {
                getLogger().warning("Failed to load jailed player: " + key);
                e.printStackTrace();
            }
        }
    }

    /**
     * Save jailed players data and jail end timestamps to config.
     */
    private void saveJailedPlayersToConfig() {
        getConfig().set("jailed", null); // clear

        for (UUID uuid : jailedPlayers.keySet()) {
            Location loc = jailedPlayers.get(uuid);
            long endTime = jailEndTimes.getOrDefault(uuid, 0L);

            String path = "jailed." + uuid.toString() + ".";
            getConfig().set(path + "world", loc.getWorld().getName());
            getConfig().set(path + "x", loc.getX());
            getConfig().set(path + "y", loc.getY());
            getConfig().set(path + "z", loc.getZ());
            getConfig().set(path + "yaw", loc.getYaw());
            getConfig().set(path + "pitch", loc.getPitch());
            getConfig().set(path + "endTime", endTime);
        }

        saveConfig();
    }

    /**
     * Returns the configured jail location.
     */
    private Location getJailLocation() {
        if (!getConfig().contains("jail.world")) return null;

        World world = Bukkit.getWorld(getConfig().getString("jail.world"));
        if (world == null) return null;

        double x = getConfig().getDouble("jail.x");
        double y = getConfig().getDouble("jail.y");
        double z = getConfig().getDouble("jail.z");
        float yaw = (float) getConfig().getDouble("jail.yaw");
        float pitch = (float) getConfig().getDouble("jail.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    // When player joins, re-jail if still jailed
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        if (jailedPlayers.containsKey(uuid)) {
            long now = System.currentTimeMillis();
            long endTime = jailEndTimes.getOrDefault(uuid, 0L);
            long remainingMillis = endTime - now;

            if (remainingMillis > 0) {
                player.teleport(getJailLocation());
                player.sendMessage(ChatColor.RED + "You are still jailed!");

                BukkitTask task = new BukkitRunnable() {
                    @Override
                    public void run() {
                        unjailPlayer(player);
                    }
                }.runTaskLater(this, remainingMillis / 50L);

                jailTasks.put(uuid, task);
            } else {
                // Jail time expired while offline, clear jail
                unjailPlayer(player);
            }
        }
    }

    // Optional: clean up on quit (does not unjail but cancels task)
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (jailTasks.containsKey(uuid)) {
            jailTasks.get(uuid).cancel();
            jailTasks.remove(uuid);
        }
    }
}
