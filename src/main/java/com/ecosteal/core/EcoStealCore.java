package com.ecosteal.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class EcoStealCore extends JavaPlugin {
    private EconomyService economy;
    private ContractService contracts;
    private PlayerStatsService stats;
    private ZoneManager zones;
    private HeistService heists;
    private GearService gear;
    private CombatTracker combat;

    @Override
    public void onEnable() {
        migrateLegacyPluginFolder();
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        economy = new EconomyService(this);
        contracts = new ContractService(this, economy);
        stats = new PlayerStatsService(this);
        zones = new ZoneManager(this);
        heists = new HeistService(this, economy, zones, contracts, stats);
        gear = new GearService(this);
        combat = new CombatTracker(this);

        getServer().getPluginManager().registerEvents(new GameplayListener(this, economy, contracts, stats, zones, heists, gear, combat), this);
        getCommand("eco").setExecutor(new CoreCommand(this, economy, zones, combat));
        getCommand("baltop").setExecutor(new BalanceTopCommand(economy));
        getCommand("ecotop").setExecutor(new EcoTopCommand(economy, stats));
        getCommand("ecoadmin").setExecutor(new AdminCommand(this, economy, zones, heists));
        getCommand("heist").setExecutor(new HeistCommand(this, heists, zones));
        getCommand("contract").setExecutor(new ContractCommand(contracts));
        getCommand("gear").setExecutor(new GearCommand(gear, economy));
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            VaultIntegration.register(this, economy);
        }
        getLogger().info("Ecosteal Core 2 v2.0.0 enabled. Configure zones with /ecoadmin zone.");
    }

    public EconomyService getEconomy() { return economy; }

    private void migrateLegacyPluginFolder() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create the Ecosteal Core 2 data folder for the legacy migration.");
            return;
        }
        for (String folderName : new String[]{"Ecosteal", "EcoStealCore"}) {
            File oldFolder = new File(getDataFolder().getParentFile(), folderName);
            if (!oldFolder.isDirectory() || oldFolder.equals(getDataFolder())) continue;
            for (String name : new String[]{"config.yml", "accounts.yml", "transactions.yml", "economy.db", "economy.db-wal", "economy.db-shm", "contracts.yml", "heist-state.yml", "player-stats.yml"}) {
                File source = new File(oldFolder, name);
                File destination = new File(getDataFolder(), name);
                if (!source.isFile() || destination.exists()) continue;
                try {
                    Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                    getLogger().info("Copied " + name + " from the " + folderName + " data folder.");
                } catch (IOException exception) {
                    getLogger().severe("Could not migrate " + name + " from the " + folderName + " data folder: " + exception.getMessage());
                }
            }
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (heists != null) heists.shutdown();
        if (stats != null) stats.shutdown();
        if (economy != null) economy.close();
    }
}