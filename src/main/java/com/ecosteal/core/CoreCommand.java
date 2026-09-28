package com.ecosteal.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class CoreCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final ZoneManager zones;
    private final CombatTracker combat;

    public CoreCommand(JavaPlugin plugin, EconomyService economy, ZoneManager zones, CombatTracker combat) {
        this.plugin = plugin;
        this.economy = economy;
        this.zones = zones;
        this.combat = combat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
            player.sendMessage(ChatColor.GOLD + "Wallet: $" + money(economy.wallet(player.getUniqueId()))
                    + " | Bank: $" + money(economy.bank(player.getUniqueId()))
                    + " | Gems: " + money(economy.gems(player.getUniqueId()))
                    + " | Crowns: " + money(economy.crowns(player.getUniqueId())));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "deposit", "withdraw" -> {
                if (args.length != 2) return usage(player);
                if (!zones.contains("bank", player.getLocation())) {
                    player.sendMessage(ChatColor.RED + "You must be at a configured bank to move cash.");
                    return true;
                }
                if (combat.tagged(player)) {
                    player.sendMessage(ChatColor.RED + "You cannot use the bank while combat-tagged.");
                    return true;
                }
                boolean deposit = args[0].equalsIgnoreCase("deposit");
                double amount;
                if (args[1].equalsIgnoreCase("all")) amount = deposit ? economy.wallet(player.getUniqueId()) : economy.bank(player.getUniqueId());
                else amount = parseAmount(args[1]);
                boolean success = deposit ? economy.deposit(player, amount) : economy.withdraw(player, amount);
                player.sendMessage(success ? ChatColor.GREEN + (deposit ? "Deposited $" : "Withdrew $") + money(amount) + "."
                        : ChatColor.RED + "Invalid amount or insufficient funds.");
                return true;
            }
            case "pay" -> {
                if (args.length != 3) return usage(player);
                Player recipient = Bukkit.getPlayerExact(args[1]);
                double amount = parseAmount(args[2]);
                if (recipient == null || recipient.equals(player) || !economy.pay(player, recipient, amount)) {
                    player.sendMessage(ChatColor.RED + "Payment failed. Check the player and your wallet balance.");
                    return true;
                }
                player.sendMessage(ChatColor.GREEN + "Paid " + recipient.getName() + " $" + money(amount) + ".");
                recipient.sendMessage(ChatColor.GREEN + "Received $" + money(amount) + " from " + player.getName() + ".");
                return true;
            }
            case "sell" -> {
                if (args.length < 2 || args.length > 3) return usage(player);
                Material material = Material.matchMaterial(args[1]);
                if (material == null) {
                    player.sendMessage(ChatColor.RED + "Unknown item.");
                    return true;
                }
                double unitPrice = plugin.getConfig().getDouble("sell-prices." + material.name(), -1);
                if (unitPrice <= 0) {
                    player.sendMessage(ChatColor.RED + "That item cannot be sold here.");
                    return true;
                }
                int requested = args.length == 3 ? parseCount(args[2]) : Integer.MAX_VALUE;
                if (requested < 1) {
                    player.sendMessage(ChatColor.RED + "Amount must be a positive whole number.");
                    return true;
                }
                int sold = removeItems(player, material, requested);
                if (sold == 0) {
                    player.sendMessage(ChatColor.RED + "You do not have that item.");
                    return true;
                }
                double total = Math.floor(sold * unitPrice * 100) / 100;
                economy.reward(player, EconomyService.Currency.CASH, total, "sold " + sold + " " + material.name());
                player.sendMessage(ChatColor.GREEN + "Sold " + sold + " " + material.name().toLowerCase(Locale.ROOT) + " for $" + money(total) + ".");
                return true;
            }
            default -> { return usage(player); }
        }
    }

    private boolean usage(Player player) {
        player.sendMessage(ChatColor.YELLOW + "Usage: /eco [balance] | /eco deposit <amount|all> | /eco withdraw <amount|all> | /eco pay <player> <amount> | /eco sell <item> [count]");
        return true;
    }

    private double parseAmount(String value) {
        try {
            double amount = Double.parseDouble(value);
            return Double.isFinite(amount) && amount > 0 ? Math.floor(amount * 100) / 100 : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String money(double amount) { return String.format(Locale.US, "%.2f", amount); }

    private int removeItems(Player player, Material material, int requested) {
        int remaining = requested;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            var item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != material || item.hasItemMeta()) continue;
            int removed = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - removed);
            if (item.getAmount() == 0) player.getInventory().setItem(slot, null);
            remaining -= removed;
        }
        return requested - remaining;
    }

    private int parseCount(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return -1; }
    }
}