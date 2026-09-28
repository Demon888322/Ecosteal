package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class HeistCommand implements CommandExecutor {
    private final HeistService heists;
    private final ZoneManager zones;

    public HeistCommand(EcoStealCore plugin, HeistService heists, ZoneManager zones) {
        this.heists = heists;
        this.zones = zones;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /heist <loot|extract|start|stop>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "loot" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                if (!heists.loot(player, player.getLocation())) player.sendMessage(ChatColor.RED + "No active objective here, or its cooldown has not expired.");
                return true;
            }
            case "extract" -> {
                if (!(sender instanceof Player player)) return playerOnly(sender);
                if (!heists.extract(player)) player.sendMessage(ChatColor.RED + "You need an active event, a heist bag, and the extraction zone.");
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("ecosteal.admin")) return denied(sender);
                int duration = args.length > 1 ? parse(args[1]) : 15;
                if (duration < 1 || duration > 240 || !heists.start(duration)) sender.sendMessage(ChatColor.RED + "Could not start event. Check duration and active event state.");
                else sender.sendMessage(ChatColor.GREEN + "Vault event started for " + duration + " minutes.");
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("ecosteal.admin")) return denied(sender);
                heists.stop("The vault event was stopped by an administrator.");
                return true;
            }
            default -> {
                sender.sendMessage(ChatColor.YELLOW + "Usage: /heist <loot|extract|start|stop>");
                return true;
            }
        }
    }

    private boolean playerOnly(CommandSender sender) { sender.sendMessage(ChatColor.RED + "This action is for players."); return true; }
    private boolean denied(CommandSender sender) { sender.sendMessage(ChatColor.RED + "You do not have permission."); return true; }

    private int parse(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException exception) { return -1; }
    }
}