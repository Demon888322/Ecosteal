package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

public final class ContractCommand implements CommandExecutor {
    private final ContractService contracts;

    public ContractCommand(ContractService contracts) {
        this.contracts = contracts;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            contracts.show(player);
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("claim")) {
            String id = args[1].toLowerCase(Locale.ROOT);
            if (!contracts.claim(player, id)) {
                player.sendMessage(ChatColor.RED + "That contract is incomplete or already claimed. Use /contract to see progress.");
            }
            return true;
        }
        player.sendMessage(ChatColor.YELLOW + "Usage: /contract [list] | /contract claim <mining|risk|heist>");
        return true;
    }
}
