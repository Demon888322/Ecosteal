package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Locale;

public final class EcoTopCommand implements CommandExecutor {
    private final EconomyService economy;
    private final PlayerStatsService stats;

    public EcoTopCommand(EconomyService economy, PlayerStatsService stats) {
        this.economy = economy;
        this.stats = stats;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("cash")) {
            List<String> entries = economy.topCash(10);
            sender.sendMessage(ChatColor.GOLD + "Top cash balances (wallet + protected bank):");
            send(sender, entries);
            return true;
        }

        PlayerStatsService.Board board;
        try {
            board = PlayerStatsService.Board.valueOf(args[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /ecotop [cash|kills|streak|heists|playtime]");
            return true;
        }
        List<String> entries = stats.top(board, 10);
        String title = switch (board) {
            case KILLS -> "Player kills";
            case STREAK -> "Best kill streaks";
            case HEISTS -> "Successful heist extractions";
            case PLAYTIME -> "Playtime";
        };
        sender.sendMessage(ChatColor.GOLD + "Top " + title + ":");
        send(sender, entries);
        return true;
    }

    private void send(CommandSender sender, List<String> entries) {
        if (entries.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No statistics recorded yet.");
        for (int index = 0; index < entries.size(); index++) {
            sender.sendMessage(ChatColor.YELLOW + "#" + (index + 1) + " " + ChatColor.WHITE + entries.get(index));
        }
    }
}
