package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class GearService {
    private final JavaPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey setKey;
    private final NamespacedKey levelKey;
    private final Map<String, Long> cooldowns = new HashMap<>();

    public GearService(JavaPlugin plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "custom_gear");
        setKey = new NamespacedKey(plugin, "armor_set");
        levelKey = new NamespacedKey(plugin, "gear_level");
    }

    public ItemStack weapon(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        Material material = switch (normalized) {
            case "riftblade" -> Material.DIAMOND_SWORD;
            case "emberblade" -> Material.NETHERITE_SWORD;
            case "stormmaul" -> Material.MACE;
            default -> null;
        };
        if (material == null) return null;
        String name = switch (normalized) {
            case "riftblade" -> "Riftblade";
            case "emberblade" -> "Emberblade";
            default -> "Storm Maul";
        };
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.AQUA + name);
        meta.setLore(java.util.List.of(ChatColor.GRAY + switch (normalized) {
            case "riftblade" -> "Right-click: blink forward";
            case "emberblade" -> "Right-click: ignite the target";
            default -> "Right-click: shock nearby enemies";
        }));
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, normalized);
        item.setItemMeta(meta);
        return item;
    }

    public java.util.List<ItemStack> armorSet(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.equals("bulwark") && !normalized.equals("scout")) return java.util.List.of();
        Material[] pieces = normalized.equals("bulwark")
                ? new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS}
                : new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS};
        java.util.List<ItemStack> result = new java.util.ArrayList<>();
        for (Material piece : pieces) {
            ItemStack item = new ItemStack(piece);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName((normalized.equals("bulwark") ? ChatColor.BLUE + "Bulwark " : ChatColor.GREEN + "Scout ") + piece.name().toLowerCase(Locale.ROOT).replace('_', ' '));
            meta.setLore(java.util.List.of(ChatColor.GRAY + (normalized.equals("bulwark")
                    ? "Set bonus: 20% less incoming damage; 10% less outgoing damage."
                    : "Set bonus: 15% more outgoing damage; 15% more damage taken.")));
            meta.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, normalized);
            item.setItemMeta(meta);
            result.add(item);
        }
        return result;
    }

    public boolean activate(Player player, ItemStack held) {
        if (held == null || !held.hasItemMeta()) return false;
        String id = held.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        if (id == null) return false;
        String cooldownKey = player.getUniqueId() + ":" + id;
        long now = System.currentTimeMillis();
        long until = cooldowns.getOrDefault(cooldownKey, 0L);
        if (until > now) {
            player.sendMessage(ChatColor.RED + "Ability cooldown: " + ((until - now + 999) / 1000) + "s.");
            return true;
        }
        cooldowns.put(cooldownKey, now + plugin.getConfig().getLong("gear.ability-cooldown-seconds", 12) * 1000L);
        switch (id) {
            case "riftblade" -> {
                var direction = player.getLocation().getDirection().normalize().multiply(4);
                var target = player.getLocation().add(direction);
                if (target.getBlock().isPassable() && target.clone().add(0, 1, 0).getBlock().isPassable()) {
                    player.teleport(target);
                    player.sendMessage(ChatColor.AQUA + "Riftstep!");
                } else player.sendMessage(ChatColor.RED + "There is no room to blink there.");
            }
            case "emberblade" -> {
                LivingEntity target = targetedLivingEntity(player, 12);
                if (target == null) player.sendMessage(ChatColor.RED + "No target in sight.");
                else {
                    target.setFireTicks(80);
                    target.damage(5, player);
                }
            }
            case "stormmaul" -> {
                int hit = 0;
                for (var entity : player.getNearbyEntities(4, 3, 4)) {
                    if (entity instanceof LivingEntity target && target != player) {
                        target.damage(6, player);
                        target.setVelocity(target.getVelocity().add(new org.bukkit.util.Vector(0, 0.45, 0)));
                        hit++;
                    }
                }
                player.sendMessage(ChatColor.AQUA + "Storm shock hit " + hit + " target(s).");
            }
        }
        return true;
    }

    private LivingEntity targetedLivingEntity(Player player, double range) {
        RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), range, 0.8,
                entity -> entity instanceof LivingEntity && entity != player);
        return result != null && result.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    public double damageMultiplier(Player player, boolean outgoing) {
        String set = fullArmorSet(player);
        int level = fullArmorSetLevel(player);
        if ("bulwark".equals(set)) {
            return outgoing ? 0.9 : Math.max(0.7, plugin.getConfig().getDouble("gear.bulwark-incoming-damage-multiplier", 0.8) - level * 0.025);
        }
        if ("scout".equals(set)) {
            return outgoing ? plugin.getConfig().getDouble("gear.scout-outgoing-damage-multiplier", 1.15) + level * 0.025
                    : Math.max(1.0, plugin.getConfig().getDouble("gear.scout-incoming-damage-multiplier", 1.15) - level * 0.025);
        }
        return 1.0;
    }

    public int nextUpgradeCost(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        ItemMeta meta = item.getItemMeta();
        boolean custom = meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)
                || meta.getPersistentDataContainer().has(setKey, PersistentDataType.STRING);
        if (!custom) return -1;
        int level = meta.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
        return level >= 3 ? -1 : (level + 1) * 10;
    }

    public boolean upgrade(ItemStack item) {
        int cost = nextUpgradeCost(item);
        if (cost < 0) return false;
        ItemMeta meta = item.getItemMeta();
        int level = meta.getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0) + 1;
        meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
        java.util.List<String> lore = meta.getLore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.getLore());
        lore.add(ChatColor.GRAY + "Upgrade level " + level + "/3");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    private String fullArmorSet(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length != 4) return "";
        String set = null;
        for (ItemStack item : armor) {
            if (item == null || !item.hasItemMeta()) return "";
            String current = item.getItemMeta().getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
            if (current == null || (set != null && !set.equals(current))) return "";
            set = current;
        }
        return set == null ? "" : set;
    }

    private int fullArmorSetLevel(Player player) {
        int level = 3;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !item.hasItemMeta()) return 0;
            level = Math.min(level, item.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0));
        }
        return level;
    }

    public double heldWeaponMultiplier(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || !held.hasItemMeta() || !held.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) return 1.0;
        int level = held.getItemMeta().getPersistentDataContainer().getOrDefault(levelKey, PersistentDataType.INTEGER, 0);
        return 1.0 + level * 0.05;
    }
}