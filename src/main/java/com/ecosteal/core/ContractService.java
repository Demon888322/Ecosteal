package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

public final class ContractService {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final File file;
    private final YamlConfiguration data;

    public ContractService(JavaPlugin plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
        file = new File(plugin.getDataFolder(), "contracts.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void mine(Player player) { progress(player, "mining"); }
    public void riskKill(Player player) { progress(player, "risk"); }
    public void extract(Player player) { progress(player, "heist"); }

    public void show(Player player) {
        String base = prepare(player.getUniqueId());
        player.sendMessage(ChatColor.GOLD + "Daily contracts (UTC):");
        for (String id : List.of("mining", "risk", "heist")) {
            String path = base + "." + id;
            int target = target(id);
            int count = Math.min(target, data.getInt(path + ".progress"));
            String reward = rewardText(id);
            String state = data.getBoolean(path + ".claimed") ? ChatColor.GREEN + "claimed"
                    : count >= target ? ChatColor.GREEN + "complete - /contract claim " + id
                    : ChatColor.YELLOW + count + "/" + target;
            player.sendMessage(ChatColor.GRAY + description(id) + ": " + state + ChatColor.GRAY + " (" + reward + ")");
        }
    }

    public boolean claim(Player player, String id) {
        if (!List.of("mining", "risk", "heist").contains(id)) return false;
        String path = prepare(player.getUniqueId()) + "." + id;
        int target = target(id);
        if (data.getBoolean(path + ".claimed") || data.getInt(path + ".progress") < target) return false;
        economy.reward(player, currency(id), reward(id), "daily " + id + " contract");
        double crownReward = Math.max(0, plugin.getConfig().getDouble("contracts.daily." + id + ".crowns", 0));
        if (crownReward > 0) economy.reward(player, EconomyService.Currency.CROWNS, crownReward, "daily " + id + " contract challenge");
        data.set(path + ".claimed", true);
        save();
        player.sendMessage(ChatColor.GREEN + "Contract reward claimed: " + rewardText(id) + ".");
        return true;
    }

    private void progress(Player player, String id) {
        String path = prepare(player.getUniqueId()) + "." + id;
        int target = target(id);
        int current = data.getInt(path + ".progress");
        if (data.getBoolean(path + ".claimed") || current >= target) return;
        int next = current + 1;
        data.set(path + ".progress", next);
        save();
        if (next >= target) {
            player.sendMessage(ChatColor.GOLD + "Daily contract complete: " + description(id) + ". Claim with /contract claim " + id + ".");
        }
    }

    private String prepare(UUID playerId) {
        String base = "players." + playerId;
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (!today.equals(data.getString(base + ".date"))) {
            data.set(base, null);
            data.set(base + ".date", today);
            save();
        }
        return base;
    }

    private int target(String id) {
        return Math.max(1, plugin.getConfig().getInt("contracts.daily." + id + ".target", switch (id) {
            case "mining" -> 32;
            case "risk" -> 1;
            default -> 1;
        }));
    }

    private double reward(String id) {
        return Math.max(0, plugin.getConfig().getDouble("contracts.daily." + id + ".reward", switch (id) {
            case "mining" -> 150;
            case "risk" -> 250;
            default -> 5;
        }));
    }

    private EconomyService.Currency currency(String id) {
        return id.equals("heist") ? EconomyService.Currency.GEMS : EconomyService.Currency.CASH;
    }

    private String rewardText(String id) {
        String text = (currency(id) == EconomyService.Currency.CASH ? "$" : "") + reward(id) + " "
            + currency(id).name().toLowerCase(java.util.Locale.ROOT);
        double crownReward = Math.max(0, plugin.getConfig().getDouble("contracts.daily." + id + ".crowns", 0));
        return crownReward > 0 ? text + " + " + crownReward + " crowns" : text;
    }

    private String description(String id) {
        return switch (id) {
            case "mining" -> "Mine in a configured mine";
            case "risk" -> "Win a fight in the risk mine";
            default -> "Complete a heist extraction";
        };
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save daily contracts: " + exception.getMessage());
        }
    }
}
