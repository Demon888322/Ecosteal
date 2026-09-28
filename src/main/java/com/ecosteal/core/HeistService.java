package com.ecosteal.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class HeistService {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final ContractService contracts;
    private final ZoneManager zones;
    private final NamespacedKey bagKey;
    private final NamespacedKey eventKey;
    private final File stateFile;
    private final YamlConfiguration state;
    private final Map<UUID, Long> bags = new HashMap<>();
    private final Map<UUID, Long> objectiveCooldowns = new HashMap<>();
    private final Map<UUID, BukkitTask> extractions = new HashMap<>();
    private final Random random = new Random();
    private boolean active;
    private long endsAt;
    private long startedAt;
    private String eventId;
    private BossBar eventBar;
    private BukkitTask timerTask;

    public HeistService(JavaPlugin plugin, EconomyService economy, ZoneManager zones, ContractService contracts) {
        this.plugin = plugin;
        this.economy = economy;
        this.contracts = contracts;
        this.zones = zones;
        bagKey = new NamespacedKey(plugin, "heist_bag");
        eventKey = new NamespacedKey(plugin, "heist_event");
        stateFile = new File(plugin.getDataFolder(), "heist-state.yml");
        state = YamlConfiguration.loadConfiguration(stateFile);
        loadState();
        if (active) startTimer();
    }

    public boolean active() { return active && System.currentTimeMillis() < endsAt; }

    public boolean start(int minutes) {
        if (active()) return false;
        if (timerTask != null) timerTask.cancel();
        timerTask = null;
        if (eventBar != null) eventBar.removeAll();
        eventBar = null;
        extractions.values().forEach(BukkitTask::cancel);
        extractions.clear();
        bags.clear();
        active = true;
        eventId = UUID.randomUUID().toString();
        startedAt = System.currentTimeMillis();
        endsAt = System.currentTimeMillis() + minutes * 60_000L;
        startTimer();
        saveState();
        Bukkit.broadcastMessage(ChatColor.GOLD + "Vault heist started. Find the marked objective and extract with your bag.");
        return true;
    }

    public void stop(String message) {
        active = false;
        if (timerTask != null) timerTask.cancel();
        timerTask = null;
        if (eventBar != null) eventBar.removeAll();
        eventBar = null;
        extractions.values().forEach(BukkitTask::cancel);
        extractions.clear();
        bags.clear();
        saveState();
        Bukkit.broadcastMessage(ChatColor.GOLD + message);
    }

    public void onJoin(Player player) {
        if (active() && eventBar != null) eventBar.addPlayer(player);
        long carried = bag(player.getUniqueId());
        if (carried > 0) player.sendMessage(ChatColor.GOLD + "Your heist bag survived the restart: $" + carried + ". Extract it during an active event.");
    }

    public boolean loot(Player player, Location location) {
        if (!active() || !zones.contains("heist_objective", location)) return false;
        long now = System.currentTimeMillis();
        if (objectiveCooldowns.getOrDefault(player.getUniqueId(), 0L) > now) return false;
        int min = plugin.getConfig().getInt("heist.objective-reward-min", 100);
        int max = Math.max(min, plugin.getConfig().getInt("heist.objective-reward-max", 250));
        long reward = min + random.nextInt(max - min + 1);
        long current = bag(player.getUniqueId());
        long total = Math.min(plugin.getConfig().getLong("heist.max-bag-value", 5000), current + reward);
        if (total <= current) return false;
        bags.put(player.getUniqueId(), total);
        objectiveCooldowns.put(player.getUniqueId(), now + plugin.getConfig().getLong("heist.objective-cooldown-seconds", 20) * 1000L);
        saveState();
        player.sendMessage(ChatColor.GOLD + "Vault loot secured in your heist bag: $" + (total - current) + " (bag $" + total + "). Extract before the event ends.");
        return true;
    }

    public void dropBag(Player player, Location location) {
        cancelExtraction(player, "Your extraction was interrupted.");
        long amount = bags.getOrDefault(player.getUniqueId(), 0L);
        if (amount <= 0) return;
        bags.remove(player.getUniqueId());
        saveState();
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Heist Bag ($" + amount + ")");
        meta.setLore(java.util.List.of(ChatColor.GRAY + "Recoverable vault loot"));
        meta.getPersistentDataContainer().set(bagKey, PersistentDataType.LONG, amount);
        meta.getPersistentDataContainer().set(eventKey, PersistentDataType.STRING, eventId);
        item.setItemMeta(meta);
        player.getWorld().dropItemNaturally(location, item);
    }

    public void pickupBag(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (!item.hasItemMeta()) return;
        var data = item.getItemMeta().getPersistentDataContainer();
        Long amount = data.get(bagKey, PersistentDataType.LONG);
        String bagEventId = data.get(eventKey, PersistentDataType.STRING);
        if (amount == null || !active() || eventId == null || !eventId.equals(bagEventId)) return;
        event.setCancelled(true);
        long current = bag(player.getUniqueId());
        long capacity = Math.max(0, plugin.getConfig().getLong("heist.max-bag-value", 5000) - current);
        long recovered = Math.min(capacity, amount);
        if (recovered <= 0) {
            player.sendMessage(ChatColor.RED + "Your heist bag is at its value limit.");
            return;
        }
        long total = current + recovered;
        bags.put(player.getUniqueId(), total);
        saveState();
        if (recovered == amount) {
            event.getItem().remove();
        } else {
            ItemMeta updatedMeta = item.getItemMeta();
            updatedMeta.getPersistentDataContainer().set(bagKey, PersistentDataType.LONG, amount - recovered);
            updatedMeta.setDisplayName(ChatColor.GOLD + "Heist Bag ($" + (amount - recovered) + ")");
            item.setItemMeta(updatedMeta);
        }
        player.sendMessage(ChatColor.GOLD + "Recovered a heist bag. Your total is $" + total + ".");
    }

    public boolean extract(Player player) {
        if (!active() || bags.getOrDefault(player.getUniqueId(), 0L) <= 0 || !zones.contains("extraction", player.getLocation())) return false;
        cancelExtraction(player, null);
        int seconds = plugin.getConfig().getInt("heist.extraction-seconds", 10);
        long finishAt = System.currentTimeMillis() + seconds * 1000L;
        player.sendMessage(ChatColor.GOLD + "Extraction started. Stay in the extraction zone for " + seconds + " seconds.");
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !active() || !zones.contains("extraction", player.getLocation())) {
                cancelExtraction(player, "Extraction cancelled: you left the zone or the event ended.");
                return;
            }
            if (System.currentTimeMillis() >= finishAt) {
                long reward = bags.getOrDefault(player.getUniqueId(), 0L);
                bags.remove(player.getUniqueId());
                extractions.remove(player.getUniqueId());
                saveState();
                economy.reward(player, EconomyService.Currency.CASH, reward, "heist extraction");
                contracts.extract(player);
                player.sendMessage(ChatColor.GREEN + "Extraction complete. $" + reward + " deposited to your wallet.");
            }
        }, 20L, 20L);
        extractions.put(player.getUniqueId(), task);
        return true;
    }

    public void cancelExtraction(Player player, String message) {
        BukkitTask task = extractions.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
            if (message != null && player.isOnline()) player.sendMessage(ChatColor.RED + message);
        }
    }

    public void onDamage(Player player) { cancelExtraction(player, "Extraction cancelled: you took damage."); }

    public long bag(UUID player) { return bags.getOrDefault(player, 0L); }

    public void shutdown() {
        if (timerTask != null) timerTask.cancel();
        timerTask = null;
        if (eventBar != null) eventBar.removeAll();
        eventBar = null;
        extractions.values().forEach(BukkitTask::cancel);
        extractions.clear();
        saveState();
    }

    private void startTimer() {
        if (eventBar != null) eventBar.removeAll();
        eventBar = Bukkit.createBossBar(ChatColor.GOLD + "Vault Heist", BarColor.YELLOW, BarStyle.SOLID);
        Bukkit.getOnlinePlayers().forEach(eventBar::addPlayer);
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= endsAt) {
                stop("The vault event has ended. Unextracted bags were lost.");
                return;
            }
            long seconds = Math.max(0, (endsAt - System.currentTimeMillis() + 999) / 1000);
            eventBar.setTitle(ChatColor.GOLD + "Vault Heist - " + (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60));
            eventBar.setProgress(Math.max(0.0, Math.min(1.0, (double) (endsAt - System.currentTimeMillis()) / (endsAt - startedAt))));
        }, 0L, 20L);
    }

    private void loadState() {
        active = state.getBoolean("active");
        endsAt = state.getLong("ends-at");
        startedAt = state.getLong("started-at", System.currentTimeMillis());
        eventId = state.getString("event-id");
        ConfigurationSection savedBags = state.getConfigurationSection("bags");
        if (savedBags != null) {
            for (String key : savedBags.getKeys(false)) {
                try {
                    long amount = savedBags.getLong(key);
                    if (amount > 0) bags.put(UUID.fromString(key), amount);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping invalid saved heist bag owner: " + key);
                }
            }
        }
        if (!active || endsAt <= System.currentTimeMillis()) {
            active = false;
            bags.clear();
            saveState();
        } else {
            if (eventId == null) eventId = UUID.randomUUID().toString();
            plugin.getLogger().info("Resuming the active vault event after restart.");
        }
    }

    private void saveState() {
        state.set("active", active);
        state.set("started-at", startedAt);
        state.set("ends-at", endsAt);
        state.set("event-id", eventId);
        state.set("bags", null);
        bags.forEach((playerId, amount) -> state.set("bags." + playerId, amount));
        try {
            state.save(stateFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save heist event state: " + exception.getMessage());
        }
    }
}