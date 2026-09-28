package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class BalanceTopCommand implements CommandExecutor {
    private final EconomyService economy;

    public BalanceTopCommand(EconomyService economy) { this.economy = economy; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> entries = economy.topCash(10);
        sender.sendMessage(ChatColor.GOLD + "Top Cash Balances (wallet + protected bank):");
        if (entries.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No balances recorded.");
        for (int index = 0; index < entries.size(); index++) {
            sender.sendMessage(ChatColor.YELLOW + "#" + (index + 1) + " " + ChatColor.WHITE + entries.get(index));
        }
        return true;
    }
}