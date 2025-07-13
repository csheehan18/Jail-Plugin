package com.corn.plugin;

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
        saveDefaultConfig();
        // Register the main plugin class for join/quit events
        getServer().getPluginManager().registerEvents(this, this);
        // Register the hit listener so /jailc stick actually jails on hit
        getServer().getPluginManager().registerEvents(new JailHitListener(this), this);

        jailStickName = ChatColor.translateAlternateColorCodes('&',
            getConfig().getString("jail.stick-name", "&cJail Stick"));
        defaultJailDuration = getConfig().getInt("jail.duration", 10);
        loadJailedPlayersFromConfig();
    }


    @Override
    public void onDisable() {
        jailTasks.values().forEach(BukkitTask::cancel);
        jailTasks.clear();
        jailedPlayers.clear();
        saveJailedPlayersToConfig();
    }

   @Override
public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!command.getName().equalsIgnoreCase("jailc")) return false;
    if (!(sender instanceof Player player)) {
        sender.sendMessage(ChatColor.RED + "Only players can run jail commands.");
        return true;
    }

    // Check base permission
    if (!player.hasPermission("jailc.use")) {
        player.sendMessage(ChatColor.RED + "You don't have permission to use that.");
        return true;
    }

    if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
        showHelp(player);
        return true;
    }

    switch (args[0].toLowerCase()) {
        case "setup":
            if (!player.hasPermission("jailc.setup")) {
                player.sendMessage(ChatColor.RED + "You lack permission: jailc.setup");
                return true;
            }
            handleSetup(player);
            break;

        case "stick":
            if (!player.hasPermission("jailc.stick")) {
                player.sendMessage(ChatColor.RED + "You lack permission: jailc.stick");
                return true;
            }
            handleStick(player);
            break;

        default:
            // jail subcommand
            if (!player.hasPermission("jailc.jail")) {
                player.sendMessage(ChatColor.RED + "You lack permission: jailc.jail");
                return true;
            }
            handleJail(player, args);
            break;
    }
    return true;
}


    private void showHelp(Player p) {
        p.sendMessage(ChatColor.YELLOW + "Jail Commands:");
        p.sendMessage(ChatColor.YELLOW + "/jailc setup");
        p.sendMessage(ChatColor.YELLOW + "/jailc <player> [seconds]");
        p.sendMessage(ChatColor.YELLOW + "/jailc stick");
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
        boolean ok = jailPlayer(target, null, seconds);
        if (ok) {
            player.sendMessage(ChatColor.YELLOW + target.getName() + " jailed for " + seconds + "s.");
        } 
    }

    public boolean jailPlayer(Player target, Player attacker, int seconds) {
        boolean foundContraband = false;
        int totalXpBottles = 0;
        if (attacker != null) {
            for (ItemStack item : target.getInventory().getContents()) {
                if (item == null) continue;
                if (item.getType() == Material.EXPERIENCE_BOTTLE) {
                    foundContraband = true;
                    totalXpBottles += item.getAmount();
                }
            }
        }
        if (!foundContraband && attacker != null) {
            // Run command on attacker
            var command = getConfig().getString("jail.run-command", "");
            if (command == null || command.equalsIgnoreCase(""))
                return false;
            command.replace("<player>", attacker.getName());
            Bukkit.dispatchCommand(Bukkit.getServer().getConsoleSender(), command);
            return false;
        }
        //Remove bottles from player and give to cop
        target.getInventory().remove(Material.EXPERIENCE_BOTTLE);
        target.updateInventory();
        ItemStack xpBottles = new ItemStack(Material.EXPERIENCE_BOTTLE, totalXpBottles);
        if (attacker != null) {
            attacker.getInventory().addItem(xpBottles);
            attacker.updateInventory();
        }
        String wn = getConfig().getString("jail.world");
        World w = Bukkit.getWorld(wn);

        Location jailLoc = new Location(w,
            getConfig().getDouble("jail.x"),
            getConfig().getDouble("jail.y"),
            getConfig().getDouble("jail.z"),
            (float)getConfig().getDouble("jail.yaw"),
            (float)getConfig().getDouble("jail.pitch")
        );

        UUID u = target.getUniqueId();
        jailedPlayers.put(u, target.getLocation());
        jailTasks.remove(u, jailTasks.get(u));

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
        jailTasks.remove(u,jailTasks.get(u));
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
                }.runTaskLater(this, rem/50L);
                jailTasks.put(u,t);
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
                World w = Bukkit.getWorld(getConfig().getString(p+"world"));
                double x = getConfig().getDouble(p+"x");
                double y = getConfig().getDouble(p+"y");
                double z = getConfig().getDouble(p+"z");
                float yaw = (float)getConfig().getDouble(p+"yaw");
                float pitch = (float)getConfig().getDouble(p+"pitch");
                Location orig = new Location(w,x,y,z,yaw,pitch);
                long end = getConfig().getLong(p+"endTime");
                jailedPlayers.put(u,orig);
                jailEndTimes.put(u,end);
                Player pl = Bukkit.getPlayer(u);
                if (pl!=null && pl.isOnline()) {
                    long rem = end-System.currentTimeMillis();
                    if (rem>0) {
                        pl.teleport(getJailLocation());
                        BukkitTask t = new BukkitRunnable(){@Override public void run(){unjailPlayer(pl);}}.runTaskLater(this,rem/50L);
                        jailTasks.put(u,t);
                    } else {
                        unjailPlayer(pl);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void saveJailedPlayersToConfig() {
        getConfig().set("jailed",null);
        jailedPlayers.forEach((u,loc)->{
            String p = "jailed."+u+".";
            getConfig().set(p+"world",loc.getWorld().getName());
            getConfig().set(p+"x",loc.getX());
            getConfig().set(p+"y",loc.getY());
            getConfig().set(p+"z",loc.getZ());
            getConfig().set(p+"yaw",loc.getYaw());
            getConfig().set(p+"pitch",loc.getPitch());
            getConfig().set(p+"endTime",jailEndTimes.get(u));
        });
        saveConfig();
    }

    private Location getJailLocation() {
        World w = Bukkit.getWorld(getConfig().getString("jail.world"));
        return new Location(w,
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
