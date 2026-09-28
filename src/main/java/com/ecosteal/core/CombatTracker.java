package com.ecosteal.core;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatTracker {
    private final JavaPlugin plugin;
    private final Map<UUID, Long> taggedUntil = new HashMap<>();

    public CombatTracker(JavaPlugin plugin) { this.plugin = plugin; }

    public void tag(Player player) {
        taggedUntil.put(player.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("economy.combat-tag-seconds", 15) * 1000L);
    }

    public boolean tagged(Player player) {
        return taggedUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }
}