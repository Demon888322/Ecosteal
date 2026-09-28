package com.ecosteal.core;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PlayerStatsService {
    public enum Board { KILLS, STREAK, HEISTS, PLAYTIME }

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Long> sessions = new HashMap<>();
    private final BukkitTask checkpointTask;

    public PlayerStatsService(JavaPlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "player-stats.yml");
        data = YamlConfiguration.loadConfiguration(file);
        checkpointTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushSessions, 1200L, 1200L);
    }

    public void join(Player player) {
        UUID id = player.getUniqueId();
        String base = path(id);
        data.set(base + ".name", player.getName());
        sessions.put(id, System.currentTimeMillis());
        save();
    }

    public void quit(Player player) {
        if (flush(player.getUniqueId())) save();
    }

    public void playerDied(Player victim, Player killer) {
        String victimBase = path(victim.getUniqueId());
        data.set(victimBase + ".streak", 0);
        data.set(victimBase + ".name", victim.getName());
        if (killer != null && !killer.equals(victim)) {
            String killerBase = path(killer.getUniqueId());
            data.set(killerBase + ".name", killer.getName());
            data.set(killerBase + ".kills", data.getInt(killerBase + ".kills") + 1);
            int streak = data.getInt(killerBase + ".streak") + 1;
            data.set(killerBase + ".streak", streak);
            data.set(killerBase + ".best-streak", Math.max(streak, data.getInt(killerBase + ".best-streak")));
        }
        save();
    }

    public void successfulHeist(Player player) {
        String base = path(player.getUniqueId());
        data.set(base + ".name", player.getName());
        data.set(base + ".heists", data.getInt(base + ".heists") + 1);
        save();
    }

    public List<String> top(Board board, int limit) {
        ConfigurationSection players = data.getConfigurationSection("players");
        if (players == null || limit < 1) return List.of();
        return players.getKeys(false).stream()
                .map(id -> entry(id, players.getConfigurationSection(id), board))
                .filter(entry -> entry.value > 0)
                .sorted(Comparator.comparingLong(Entry::value).reversed())
                .limit(limit)
                .map(entry -> entry.name + " - " + format(entry.value, board))
                .toList();
    }

    public void shutdown() {
        checkpointTask.cancel();
        flushSessions();
    }

    private Entry entry(String id, ConfigurationSection section, Board board) {
        String name = section == null ? id : section.getString("name", id);
        String base = "players." + id;
        long value = switch (board) {
            case KILLS -> data.getLong(base + ".kills");
            case STREAK -> data.getLong(base + ".best-streak");
            case HEISTS -> data.getLong(base + ".heists");
            case PLAYTIME -> data.getLong(base + ".played-seconds") + currentSessionSeconds(id);
        };
        return new Entry(name, value);
    }

    private long currentSessionSeconds(String id) {
        try {
            Long started = sessions.get(UUID.fromString(id));
            return started == null ? 0 : Math.max(0, (System.currentTimeMillis() - started) / 1000);
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    private String format(long value, Board board) {
        if (board != Board.PLAYTIME) return Long.toString(value);
        long hours = value / 3600;
        long minutes = (value % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private boolean flush(UUID playerId) {
        Long started = sessions.remove(playerId);
        if (started == null) return false;
        String base = path(playerId);
        long elapsed = Math.max(0, (System.currentTimeMillis() - started) / 1000);
        data.set(base + ".played-seconds", data.getLong(base + ".played-seconds") + elapsed);
        return true;
    }

    private void flushSessions() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (UUID playerId : List.copyOf(sessions.keySet())) {
            Long started = sessions.put(playerId, now);
            if (started == null) continue;
            String base = path(playerId);
            long elapsed = Math.max(0, (now - started) / 1000);
            data.set(base + ".played-seconds", data.getLong(base + ".played-seconds") + elapsed);
            changed = true;
        }
        if (changed) save();
    }

    private String path(UUID playerId) { return "players." + playerId; }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save player statistics: " + exception.getMessage());
        }
    }

    private record Entry(String name, long value) { }
}
