package com.ecosteal.core;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public final class GearCommand implements CommandExecutor {
    private final GearService gear;
    private final EconomyService economy;

    public GearCommand(GearService gear, EconomyService economy) {
        this.gear = gear;
        this.economy = economy;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("upgrade")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "This action is for players.");
                return true;
            }
            ItemStack held = player.getInventory().getItemInMainHand();
            int cost = gear.nextUpgradeCost(held);
            if (cost < 0) {
                player.sendMessage(ChatColor.RED + "Hold custom gear that is not already fully upgraded.");
                return true;
            }
            if (!economy.spendGems(player, cost)) {
                player.sendMessage(ChatColor.RED + "You need " + cost + " gems for the next upgrade.");
                return true;
            }
            gear.upgrade(held);
            player.getInventory().setItemInMainHand(held);
            player.sendMessage(ChatColor.AQUA + "Gear upgraded for " + cost + " gems.");
            return true;
        }
        if (args.length != 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /gear give <player> <riftblade|emberblade|stormmaul|bulwark|scout> | /gear upgrade");
            return true;
        }
        if (!sender.hasPermission("ecosteal.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "That player must be online.");
            return true;
        }
        ItemStack weapon = gear.weapon(args[2]);
        List<ItemStack> items = weapon == null ? gear.armorSet(args[2]) : List.of(weapon);
        if (items.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Unknown gear item.");
            return true;
        }
        for (ItemStack item : items) {
            var overflow = target.getInventory().addItem(item);
            overflow.values().forEach(extra -> target.getWorld().dropItemNaturally(target.getLocation(), extra));
        }
        sender.sendMessage(ChatColor.GREEN + "Gave " + args[2] + " to " + target.getName() + ".");
        target.sendMessage(ChatColor.AQUA + "You received custom gear: " + args[2] + ".");
        return true;
    }
}