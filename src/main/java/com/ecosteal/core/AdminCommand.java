package com.ecosteal.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public final class AdminCommand implements CommandExecutor {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final ZoneManager zones;
    private final HeistService heists;

    public AdminCommand(JavaPlugin plugin, EconomyService economy, ZoneManager zones, HeistService heists) {
        this.plugin = plugin;
        this.economy = economy;
        this.zones = zones;
        this.heists = heists;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ecosteal.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length == 0) return usage(sender);
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "transactions" -> {
                if (args.length < 2 || args.length > 3) return usage(sender);
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                int limit = args.length == 3 ? parseLimit(args[2]) : 10;
                if (limit < 1 || limit > 50) {
                    sender.sendMessage(ChatColor.RED + "Limit must be between 1 and 50.");
                    return true;
                }
                sender.sendMessage(ChatColor.GOLD + "Recent transactions for " + target.getName() + ":");
                var entries = economy.history(target.getUniqueId(), limit);
                if (entries.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No transactions recorded.");
                else entries.forEach(entry -> sender.sendMessage(ChatColor.GRAY + entry));
                return true;
            }
            case "give" -> {
                if (args.length != 4) return usage(sender);
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                EconomyService.Currency currency;
                try {
                    currency = EconomyService.Currency.valueOf(args[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    sender.sendMessage(ChatColor.RED + "Currency must be cash, gems, or crowns.");
                    return true;
                }
                double amount = parseAmount(args[3]);
                if (amount <= 0) {
                    sender.sendMessage(ChatColor.RED + "Amount must be a positive number.");
                    return true;
                }
                economy.adminGrant(target, currency, amount);
                sender.sendMessage(ChatColor.GREEN + "Granted " + amount + " " + currency.name().toLowerCase(Locale.ROOT) + " to " + target.getName() + ".");
                if (target.isOnline()) target.getPlayer().sendMessage(ChatColor.GREEN + "An administrator granted you " + amount + " " + currency.name().toLowerCase(Locale.ROOT) + ".");
                return true;
            }
            case "zone" -> {
                if (args.length != 3 || !(sender instanceof Player player)) return usage(sender);
                if (!zones.validZone(args[1]) || (!args[2].equals("1") && !args[2].equals("2"))) return usage(sender);
                boolean saved = zones.setCorner(args[1], Integer.parseInt(args[2]), player.getLocation());
                sender.sendMessage(saved ? ChatColor.GREEN + "Set corner " + args[2] + " for zone " + args[1] + "."
                        : ChatColor.RED + "Zone corners must be in the same world.");
                return true;
            }
            case "event" -> {
                if (args.length < 2 || args.length > 3) return usage(sender);
                if (args[1].equalsIgnoreCase("stop")) {
                    heists.stop("The vault event was stopped by an administrator.");
                    return true;
                }
                if (!args[1].equalsIgnoreCase("start")) return usage(sender);
                int minutes = args.length == 3 ? parseLimit(args[2]) : plugin.getConfig().getInt("heist.default-duration-minutes", 15);
                if (minutes < 1 || minutes > 240) {
                    sender.sendMessage(ChatColor.RED + "Event duration must be 1-240 minutes.");
                    return true;
                }
                sender.sendMessage(heists.start(minutes) ? ChatColor.GREEN + "Vault event started for " + minutes + " minutes."
                        : ChatColor.RED + "A vault event is already active.");
                return true;
            }
            default -> { return usage(sender); }
        }
    }

    private boolean usage(CommandSender sender) {
        sender.sendMessage(ChatColor.YELLOW + "Usage: /ecoadmin transactions <player> [limit] | give <player> <cash|gems|crowns> <amount> | zone <name> <1|2> | event <start [minutes]|stop>");
        return true;
    }

    private int parseLimit(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return -1; }
    }

    private double parseAmount(String value) {
        try {
            double amount = Double.parseDouble(value);
            return Double.isFinite(amount) && amount > 0 ? Math.floor(amount * 100) / 100 : -1;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }
}