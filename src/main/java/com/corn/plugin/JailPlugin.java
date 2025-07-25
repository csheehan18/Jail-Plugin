package com.corn.plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import io.papermc.lib.PaperLib;

public class JailPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, Location> jailedPlayers = new HashMap<>();
    private final Map<UUID, BukkitTask> jailTasks = new HashMap<>();
    private final Map<UUID, Long> jailEndTimes = new HashMap<>();

    private String jailStickName;
    private int defaultJailDuration;

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);

        // Only create defaults if config.yml does not exist
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getConfig().addDefault("jail.duration", 10);
            getConfig().addDefault("jail.stick-name", "&cJail Stick");
            getConfig().addDefault("jail.run-command", "say <player> was jailed!");
            getConfig().addDefault("jail.world", "world");
            getConfig().addDefault("jail.x", 0.0);
            getConfig().addDefault("jail.y", 64.0);
            getConfig().addDefault("jail.z", 0.0);
            getConfig().addDefault("jail.yaw", 0.0);
            getConfig().addDefault("jail.pitch", 0.0);
            getConfig().options().copyDefaults(true);
            saveConfig();
        }

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new JailHitListener(this), this);

        // Load config-driven values
        jailStickName = ChatColor.translateAlternateColorCodes('&',
            getConfig().getString("jail.stick-name"));
        defaultJailDuration = getConfig().getInt("jail.duration", 10);

        loadJailedPlayersFromConfig();
    }

    @Override
    public void onDisable() {
        jailTasks.values().forEach(BukkitTask::cancel);
        jailedPlayers.clear();
        jailTasks.clear();
        saveJailedPlayersToConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("jailc")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can run jail commands.");
            return true;
        }

        if (!player.hasPermission("jailc.use")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setup" -> {
                if (!player.hasPermission("jailc.setup")) {
                    player.sendMessage(ChatColor.RED + "You lack permission: jailc.setup");
                } else {
                    handleSetup(player);
                }
            }
            case "stick" -> {
                if (!player.hasPermission("jailc.stick")) {
                    player.sendMessage(ChatColor.RED + "You lack permission: jailc.stick");
                } else {
                    handleStick(player);
                }
            }
            case "reload" -> {
                if (!player.hasPermission("jailc.reload")) {
                    player.sendMessage(ChatColor.RED + "You lack permission: jailc.reload");
                } else {
                    reloadConfig();
                    jailStickName = ChatColor.translateAlternateColorCodes('&',
                        getConfig().getString("jail.stick-name"));
                    defaultJailDuration = getConfig().getInt("jail.duration", 10);
                    player.sendMessage(ChatColor.GREEN + "JailPlugin configuration reloaded.");
                }
            }
            default -> {
                if (!player.hasPermission("jailc.jail")) {
                    player.sendMessage(ChatColor.RED + "You lack permission: jailc.jail");
                } else {
                    handleJail(player, args);
                }
            }
        }
        return true;
    }

    private void showHelp(Player p) {
        p.sendMessage(ChatColor.YELLOW + "Jail Commands:");
        p.sendMessage(ChatColor.YELLOW + "/jailc setup");
        p.sendMessage(ChatColor.YELLOW + "/jailc <player> [seconds]");
        p.sendMessage(ChatColor.YELLOW + "/jailc stick");
        p.sendMessage(ChatColor.YELLOW + "/jailc reload");
    }

    private void handleSetup(Player player) {
        Location loc = player.getLocation();
        getConfig().set("jail.world", loc.getWorld().getName());
        getConfig().set("jail.x", loc.getX());
        getConfig().set("jail.y", loc.getY());
        getConfig().set("jail.z", loc.getZ());
        getConfig().set("jail.yaw", loc.getYaw());
        getConfig().set("jail.pitch", loc.getPitch());
        saveConfig();
        player.sendMessage(ChatColor.GREEN + "Jail location saved.");
    }

    private void handleStick(Player player) {
        ItemStack stick = JailItemUtils.createJailStick(jailStickName);
        player.getInventory().addItem(stick);
        player.sendMessage(ChatColor.GREEN + "You received a Jail Stick.");
    }

    private void handleJail(Player player, String[] args) {
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            player.sendMessage(ChatColor.RED + "Player not found or not online.");
            return;
        }
        int seconds = defaultJailDuration;
        if (args.length > 1) {
            try {
                seconds = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage(ChatColor.RED + "Invalid time, using default: " + defaultJailDuration + "s");
            }
        }
        if (!getConfig().contains("jail.world")) {
            player.sendMessage(ChatColor.RED + "Failed to jail player. Jail location not set.");
            return;
        }
        // Pass player as attacker for contraband logic
        boolean ok = jailPlayer(target, player, seconds);
        if (ok) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " jailed for " + seconds + "s.");
        }
    }

    /**
     * Core jail logic. If attacker != null, checks & transfers XP bottles, runs configured command.
     */
    public boolean jailPlayer(Player target, Player attacker, int seconds) {
        boolean foundContraband = false;
        int totalXpBottles = 0;
        if (attacker != null) {
            for (ItemStack item : target.getInventory().getContents()) {
                if (item != null && item.getType() == Material.EXPERIENCE_BOTTLE) {
                    foundContraband = true;
                    totalXpBottles += item.getAmount();
                }
            }
            if (!foundContraband) {
                String cmd = getConfig().getString("jail.run-command", "");
                if (!cmd.isBlank()) {
                    cmd = cmd.replace("<player>", attacker.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                }
                return false;
            }
            // remove and transfer XP bottles
            target.getInventory().remove(Material.EXPERIENCE_BOTTLE);
            attacker.getInventory().addItem(new ItemStack(Material.EXPERIENCE_BOTTLE, totalXpBottles));
            target.updateInventory();
            attacker.updateInventory();
        }

        // teleport to jail & schedule unjail
        World w = Bukkit.getWorld(getConfig().getString("jail.world"));
        Location jailLoc = new Location(
            w,
            getConfig().getDouble("jail.x"),
            getConfig().getDouble("jail.y"),
            getConfig().getDouble("jail.z"),
            (float)getConfig().getDouble("jail.yaw"),
            (float)getConfig().getDouble("jail.pitch")
        );

        UUID u = target.getUniqueId();
        jailedPlayers.put(u, target.getLocation());
        if (jailTasks.containsKey(u)) jailTasks.get(u).cancel();

        target.teleport(jailLoc);
        target.sendMessage(ChatColor.RED + "You have been jailed for " + seconds + " seconds.");

        long end = System.currentTimeMillis() + seconds * 1000L;
        jailEndTimes.put(u, end);

        BukkitTask task = new BukkitRunnable() {
            @Override public void run() { unjailPlayer(target); }
        }.runTaskLater(this, seconds * 20L);

        jailTasks.put(u, task);
        saveJailedPlayersToConfig();
        return true;
    }

    public void unjailPlayer(Player p) {
        UUID u = p.getUniqueId();
        Location orig = jailedPlayers.remove(u);
        if (orig != null && p.isOnline()) {
            p.teleport(orig);
            p.sendMessage(ChatColor.GREEN + "You have been released from jail.");
        }
        if (jailTasks.containsKey(u)) jailTasks.get(u).cancel();
        jailTasks.remove(u);
        jailEndTimes.remove(u);
        saveJailedPlayersToConfig();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        if (jailEndTimes.containsKey(u)) {
            long rem = jailEndTimes.get(u) - System.currentTimeMillis();
            if (rem > 0) {
                e.getPlayer().teleport(getJailLocation());
                e.getPlayer().sendMessage(ChatColor.RED + "You are still jailed!");
                BukkitTask t = new BukkitRunnable() {
                    @Override public void run() { unjailPlayer(e.getPlayer()); }
                }.runTaskLater(this, rem / 50L);
                jailTasks.put(u, t);
            } else {
                unjailPlayer(e.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        if (jailTasks.containsKey(u)) {
            jailTasks.get(u).cancel();
            jailTasks.remove(u);
        }
    }

    private void loadJailedPlayersFromConfig() {
        if (!getConfig().isConfigurationSection("jailed")) return;
        for (String key : getConfig().getConfigurationSection("jailed").getKeys(false)) {
            try {
                UUID u = UUID.fromString(key);
                String p = "jailed." + key + ".";
                World w = Bukkit.getWorld(getConfig().getString(p + "world"));
                Location orig = new Location(
                    w,
                    getConfig().getDouble(p + "x"),
                    getConfig().getDouble(p + "y"),
                    getConfig().getDouble(p + "z"),
                    (float)getConfig().getDouble(p + "yaw"),
                    (float)getConfig().getDouble(p + "pitch")
                );
                long end = getConfig().getLong(p + "endTime");

                jailedPlayers.put(u, orig);
                jailEndTimes.put(u, end);

                Player pl = Bukkit.getPlayer(u);
                if (pl != null && pl.isOnline()) {
                    long rem = end - System.currentTimeMillis();
                    if (rem > 0) {
                        pl.teleport(getJailLocation());
                        BukkitTask t = new BukkitRunnable() {
                            @Override public void run() { unjailPlayer(pl); }
                        }.runTaskLater(this, rem / 50L);
                        jailTasks.put(u, t);
                    } else {
                        unjailPlayer(pl);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void saveJailedPlayersToConfig() {
        getConfig().set("jailed", null);
        jailedPlayers.forEach((u, loc) -> {
            String p = "jailed." + u + ".";
            getConfig().set(p + "world", loc.getWorld().getName());
            getConfig().set(p + "x", loc.getX());
            getConfig().set(p + "y", loc.getY());
            getConfig().set(p + "z", loc.getZ());
            getConfig().set(p + "yaw", loc.getYaw());
            getConfig().set(p + "pitch", loc.getPitch());
            getConfig().set(p + "endTime", jailEndTimes.get(u));
        });
        saveConfig();
    }

    private Location getJailLocation() {
        World w = Bukkit.getWorld(getConfig().getString("jail.world"));
        return new Location(
            w,
            getConfig().getDouble("jail.x"),
            getConfig().getDouble("jail.y"),
            getConfig().getDouble("jail.z"),
            (float)getConfig().getDouble("jail.yaw"),
            (float)getConfig().getDouble("jail.pitch")
        );
    }

    public String getJailStickName() {
        return jailStickName;
    }
}
