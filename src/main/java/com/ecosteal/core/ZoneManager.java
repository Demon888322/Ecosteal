package com.ecosteal.core;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Set;

public final class ZoneManager {
    private static final Set<String> ZONES = Set.of("safe_mine", "risk_mine", "bank", "heist_objective", "extraction");
    private final JavaPlugin plugin;

    public ZoneManager(JavaPlugin plugin) { this.plugin = plugin; }

    public boolean contains(String zone, Location location) {
        if (location.getWorld() == null) return false;
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("zones." + zone);
        if (section == null || !section.contains("world")) return false;
        if (!location.getWorld().getName().equals(section.getString("world"))) return false;
        return location.getBlockX() >= section.getInt("minX") && location.getBlockX() <= section.getInt("maxX")
                && location.getBlockY() >= section.getInt("minY") && location.getBlockY() <= section.getInt("maxY")
                && location.getBlockZ() >= section.getInt("minZ") && location.getBlockZ() <= section.getInt("maxZ");
    }

    public boolean setCorner(String zone, int corner, Location location) {
        String normalized = zone.toLowerCase(Locale.ROOT);
        if (!ZONES.contains(normalized) || (corner != 1 && corner != 2)) return false;
        String path = "zones." + normalized;
        String otherWorld = plugin.getConfig().getString(path + ".corner" + (corner == 1 ? 2 : 1) + ".world");
        if (otherWorld != null && !otherWorld.equals(location.getWorld().getName())) return false;
        String key = "corner" + corner;
        plugin.getConfig().set(path + "." + key + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + "." + key + ".x", location.getBlockX());
        plugin.getConfig().set(path + "." + key + ".y", location.getBlockY());
        plugin.getConfig().set(path + "." + key + ".z", location.getBlockZ());
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section.contains("corner1") && section.contains("corner2")) {
            String world1 = section.getString("corner1.world");
            plugin.getConfig().set(path + ".world", world1);
            for (String axis : new String[]{"x", "y", "z"}) {
                int first = section.getInt("corner1." + axis);
                int second = section.getInt("corner2." + axis);
                plugin.getConfig().set(path + ".min" + axis.toUpperCase(Locale.ROOT), Math.min(first, second));
                plugin.getConfig().set(path + ".max" + axis.toUpperCase(Locale.ROOT), Math.max(first, second));
            }
        }
        plugin.saveConfig();
        return true;
    }

    public boolean validZone(String zone) { return ZONES.contains(zone.toLowerCase(Locale.ROOT)); }
}