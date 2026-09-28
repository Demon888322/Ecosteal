package com.ecosteal.core;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.ServicePriority;

public final class VaultIntegration {
    private VaultIntegration() { }

    public static void register(EcoStealCore plugin, EconomyService economy) {
        plugin.getServer().getServicesManager().register(
                Economy.class,
                new VaultEconomyProvider(plugin, economy),
                plugin,
                ServicePriority.High
        );
        plugin.getLogger().info("Vault economy bridge registered for wallet cash.");
    }
}