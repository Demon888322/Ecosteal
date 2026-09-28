package com.ecosteal.core;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;

@SuppressWarnings("deprecation")
public final class VaultEconomyProvider extends AbstractEconomy {
    private final EcoStealCore plugin;
    private final EconomyService economy;

    public VaultEconomyProvider(EcoStealCore plugin, EconomyService economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    @Override public boolean isEnabled() { return plugin.isEnabled(); }
    @Override public String getName() { return "Ecosteal Core 2"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return String.format(java.util.Locale.US, "$%.2f", amount); }
    @Override public String currencyNamePlural() { return "dollars"; }
    @Override public String currencyNameSingular() { return "dollar"; }

    @Override public boolean hasAccount(String playerName) { return hasAccount(Bukkit.getOfflinePlayer(playerName)); }
    @Override public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }
    @Override public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }
    @Override public boolean hasAccount(OfflinePlayer player) { return player.hasPlayedBefore() || economy.wallet(player.getUniqueId()) > 0; }

    @Override public double getBalance(String playerName) { return getBalance(Bukkit.getOfflinePlayer(playerName)); }
    @Override public double getBalance(String playerName, String worldName) { return getBalance(playerName); }
    @Override public double getBalance(OfflinePlayer player, String worldName) { return getBalance(player); }
    @Override public double getBalance(OfflinePlayer player) { return economy.wallet(player.getUniqueId()); }

    @Override public boolean has(String playerName, double amount) { return has(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }
    @Override public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }
    @Override public boolean has(OfflinePlayer player, double amount) {
        return Double.isFinite(amount) && amount >= 0 && getBalance(player) >= amount;
    }

    @Override public EconomyResponse withdrawPlayer(String playerName, double amount) { return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }
    @Override public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double balance = getBalance(player);
        if (!validAmount(amount) || balance < amount) return failure(amount, balance, "Insufficient funds or invalid amount.");
        if (!economy.withdrawCash(player, amount, "Vault withdrawal")) return failure(amount, getBalance(player), "Withdrawal failed.");
        return success(amount, getBalance(player));
    }

    @Override public EconomyResponse depositPlayer(String playerName, double amount) { return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount); }
    @Override public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }
    @Override public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double balance = getBalance(player);
        if (!validAmount(amount)) return failure(amount, balance, "Invalid amount.");
        economy.reward(player, EconomyService.Currency.CASH, amount, "Vault deposit");
        return success(amount, getBalance(player));
    }

    @Override public EconomyResponse createBank(String name, String player) { return unsupportedBank(); }
    @Override public EconomyResponse deleteBank(String name) { return unsupportedBank(); }
    @Override public EconomyResponse bankBalance(String name) { return unsupportedBank(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return unsupportedBank(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return unsupportedBank(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return unsupportedBank(); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return unsupportedBank(); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return unsupportedBank(); }
    @Override public List<String> getBanks() { return List.of(); }
    @Override public boolean createPlayerAccount(String playerName) { return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return true; }

    private boolean validAmount(double amount) { return Double.isFinite(amount) && amount > 0; }

    private EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(double amount, double balance, String message) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.FAILURE, message);
    }

    private EconomyResponse unsupportedBank() {
        return failure(0, 0, "Ecosteal Core 2 does not expose protected player banks through Vault.");
    }
}