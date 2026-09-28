package com.ecosteal.core;

import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EconomyService {
    public enum Currency { CASH, GEMS, CROWNS }

    private final JavaPlugin plugin;
    private final Map<UUID, Account> accounts = new HashMap<>();
    private Connection connection;

    public EconomyService(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create the Ecosteal data folder.");
        }
        try {
            Class.forName("org.sqlite.JDBC");
            File databaseFile = new File(plugin.getDataFolder(), "economy.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
            initializeDatabase();
            migrateLegacyFiles();
            loadAccounts();
        } catch (ClassNotFoundException | SQLException exception) {
            throw new IllegalStateException("Could not initialize the Ecosteal economy database.", exception);
        }
    }

    public synchronized double wallet(UUID id) { return value(id, "wallet"); }
    public synchronized double bank(UUID id) { return value(id, "bank"); }
    public synchronized double gems(UUID id) { return value(id, "gems"); }
    public synchronized double crowns(UUID id) { return value(id, "crowns"); }

    private double value(UUID id, String field) {
        Account account = accounts.get(id);
        return account == null ? 0 : account.value(field);
    }

    public synchronized boolean deposit(OfflinePlayer player, double amount) {
        if (!validAmount(amount) || wallet(player.getUniqueId()) < amount) return false;
        update(player, "wallet", -amount);
        update(player, "bank", amount);
        record(player, "DEPOSIT", amount, "wallet to protected bank");
        return true;
    }

    public synchronized boolean withdraw(OfflinePlayer player, double amount) {
        if (!validAmount(amount) || bank(player.getUniqueId()) < amount) return false;
        update(player, "bank", -amount);
        update(player, "wallet", amount);
        record(player, "WITHDRAW", amount, "protected bank to wallet");
        return true;
    }

    public synchronized boolean spendGems(OfflinePlayer player, double amount) {
        if (!validAmount(amount) || gems(player.getUniqueId()) < amount) return false;
        update(player, "gems", -amount);
        record(player, "GEAR_UPGRADE", -amount, "custom gear upgrade");
        return true;
    }

    public synchronized boolean withdrawCash(OfflinePlayer player, double amount, String reason) {
        if (!validAmount(amount) || wallet(player.getUniqueId()) < amount) return false;
        update(player, "wallet", -amount);
        record(player, "WITHDRAW_CASH", amount, reason);
        return true;
    }

    public synchronized boolean pay(OfflinePlayer sender, OfflinePlayer recipient, double amount) {
        if (!validAmount(amount) || sender.getUniqueId().equals(recipient.getUniqueId()) || wallet(sender.getUniqueId()) < amount) return false;
        update(sender, "wallet", -amount);
        update(recipient, "wallet", amount);
        record(sender, "PAY", amount, "to " + recipient.getName());
        record(recipient, "RECEIVE", amount, "from " + sender.getName());
        return true;
    }

    public synchronized void reward(OfflinePlayer player, Currency currency, double amount, String reason) {
        if (!validAmount(amount)) return;
        String field = switch (currency) {
            case CASH -> "wallet";
            case GEMS -> "gems";
            case CROWNS -> "crowns";
        };
        update(player, field, amount);
        record(player, "REWARD_" + currency.name(), amount, reason);
    }

    public synchronized double steal(OfflinePlayer victim, OfflinePlayer killer, double amount) {
        if (!validAmount(amount)) return 0;
        double stolen = Math.min(Math.max(0, amount), wallet(victim.getUniqueId()));
        if (stolen <= 0) return 0;
        update(victim, "wallet", -stolen);
        update(killer, "wallet", stolen);
        record(victim, "RISK_LOSS", -stolen, "stolen by " + killer.getName());
        record(killer, "RISK_STEAL", stolen, "stolen from " + victim.getName());
        return stolen;
    }

    public void adminGrant(OfflinePlayer player, Currency currency, double amount) {
        reward(player, currency, amount, "admin grant");
    }

    private void update(OfflinePlayer player, String field, double delta) {
        UUID id = player.getUniqueId();
        Account current = accounts.getOrDefault(id, new Account(player.getName(), 0, 0, 0, 0));
        Account updated = current.with(player.getName(), field, Math.max(0, current.value(field) + delta));
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts(uuid, name, wallet, bank, gems, crowns) VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET name=excluded.name, wallet=excluded.wallet, bank=excluded.bank, gems=excluded.gems, crowns=excluded.crowns
                """)) {
            statement.setString(1, id.toString());
            statement.setString(2, updated.name);
            statement.setDouble(3, updated.wallet);
            statement.setDouble(4, updated.bank);
            statement.setDouble(5, updated.gems);
            statement.setDouble(6, updated.crowns);
            statement.executeUpdate();
            accounts.put(id, updated);
        } catch (SQLException exception) {
            throw databaseFailure("Could not update account " + id, exception);
        }
    }

    private void record(OfflinePlayer player, String type, double amount, String detail) {
        String entry = Instant.now() + " | " + player.getUniqueId() + " | " + player.getName() + " | " + type + " | " + String.format(java.util.Locale.US, "%.2f", amount) + " | " + detail;
        int limit = plugin.getConfig().getInt("economy.transaction-history-limit", 1000);
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO transactions(player_uuid, entry) VALUES(?, ?)");
             PreparedStatement trim = connection.prepareStatement("DELETE FROM transactions WHERE id NOT IN (SELECT id FROM transactions ORDER BY id DESC LIMIT ?)")) {
            insert.setString(1, player.getUniqueId().toString());
            insert.setString(2, entry);
            insert.executeUpdate();
            trim.setInt(1, Math.max(1, limit));
            trim.executeUpdate();
        } catch (SQLException exception) {
            throw databaseFailure("Could not record economy transaction", exception);
        }
    }

    public synchronized List<String> history(UUID playerId, int limit) {
        if (limit < 1) return List.of();
        List<String> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("SELECT entry FROM transactions WHERE player_uuid=? ORDER BY id DESC LIMIT ?")) {
            statement.setString(1, playerId.toString());
            statement.setInt(2, limit);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) entries.add(results.getString("entry"));
            }
        } catch (SQLException exception) {
            throw databaseFailure("Could not read transaction history", exception);
        }
        java.util.Collections.reverse(entries);
        return entries;
    }

    public synchronized List<String> topCash(int limit) {
        if (limit < 1) return List.of();
        return accounts.values().stream()
                .map(account -> new BalanceEntry(account.name, account.wallet + account.bank))
                .sorted(Comparator.comparingDouble(BalanceEntry::total).reversed())
                .limit(limit)
                .map(entry -> entry.name() + " $" + String.format(java.util.Locale.US, "%.2f", entry.total()))
                .toList();
    }

    private record BalanceEntry(String name, double total) { }

    public synchronized void save() {
        if (connection == null) return;
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(PASSIVE)");
            }
        }
        catch (SQLException exception) {
            plugin.getLogger().severe("Could not checkpoint the Ecosteal economy database: " + exception.getMessage());
        }
    }

    public synchronized void close() {
        if (connection == null) return;
        save();
        try {
            connection.close();
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not close the Ecosteal economy database: " + exception.getMessage());
        }
        connection = null;
    }

    private void initializeDatabase() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("CREATE TABLE IF NOT EXISTS accounts (uuid TEXT PRIMARY KEY, name TEXT NOT NULL, wallet REAL NOT NULL, bank REAL NOT NULL, gems REAL NOT NULL, crowns REAL NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY AUTOINCREMENT, player_uuid TEXT NOT NULL, entry TEXT NOT NULL)");
            statement.execute("CREATE INDEX IF NOT EXISTS transactions_player_idx ON transactions(player_uuid, id)");
            statement.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
        }
    }

    private void migrateLegacyFiles() throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT value FROM metadata WHERE key='legacy_yaml_imported'")) {
            try (ResultSet result = query.executeQuery()) {
                if (result.next()) return;
            }
        }

        YamlConfiguration legacyAccounts = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "accounts.yml"));
        YamlConfiguration legacyTransactions = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "transactions.yml"));
        connection.setAutoCommit(false);
        try {
            var players = legacyAccounts.getConfigurationSection("players");
            if (players != null) {
                for (String key : players.getKeys(false)) {
                    UUID id;
                    try {
                        id = UUID.fromString(key);
                    } catch (IllegalArgumentException ignored) {
                        plugin.getLogger().warning("Skipping invalid legacy account ID: " + key);
                        continue;
                    }
                    String path = "players." + key;
                    Account account = new Account(legacyAccounts.getString(path + ".name", key),
                            legacyAccounts.getDouble(path + ".wallet"), legacyAccounts.getDouble(path + ".bank"),
                            legacyAccounts.getDouble(path + ".gems"), legacyAccounts.getDouble(path + ".crowns"));
                    insertMigratedAccount(id, account);
                }
            }
            for (String entry : legacyTransactions.getStringList("entries")) {
                String[] parts = entry.split(" \\| ", 5);
                if (parts.length < 2) continue;
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO transactions(player_uuid, entry) VALUES(?, ?)")) {
                    insert.setString(1, UUID.fromString(parts[1]).toString());
                    insert.setString(2, entry);
                    insert.executeUpdate();
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Skipping a legacy transaction with an invalid player ID.");
                }
            }
            try (PreparedStatement marker = connection.prepareStatement("INSERT INTO metadata(key, value) VALUES('legacy_yaml_imported', 'true')")) {
                marker.executeUpdate();
            }
            connection.commit();
            plugin.getLogger().info("Imported legacy YAML economy data into economy.db; original YAML files were left untouched.");
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private void insertMigratedAccount(UUID id, Account account) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO accounts(uuid, name, wallet, bank, gems, crowns) VALUES(?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, id.toString());
            statement.setString(2, account.name);
            statement.setDouble(3, account.wallet);
            statement.setDouble(4, account.bank);
            statement.setDouble(5, account.gems);
            statement.setDouble(6, account.crowns);
            statement.executeUpdate();
        }
    }

    private void loadAccounts() throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT uuid, name, wallet, bank, gems, crowns FROM accounts")) {
            while (result.next()) {
                accounts.put(UUID.fromString(result.getString("uuid")), new Account(result.getString("name"),
                        result.getDouble("wallet"), result.getDouble("bank"), result.getDouble("gems"), result.getDouble("crowns")));
            }
        }
    }

    private boolean validAmount(double amount) {
        return Double.isFinite(amount) && amount > 0;
    }

    private IllegalStateException databaseFailure(String message, SQLException cause) {
        plugin.getLogger().severe(message + ": " + cause.getMessage());
        return new IllegalStateException(message, cause);
    }

    private static final class Account {
        private final String name;
        private final double wallet;
        private final double bank;
        private final double gems;
        private final double crowns;

        private Account(String name, double wallet, double bank, double gems, double crowns) {
            this.name = name;
            this.wallet = wallet;
            this.bank = bank;
            this.gems = gems;
            this.crowns = crowns;
        }

        private double value(String field) {
            return switch (field) {
                case "wallet" -> wallet;
                case "bank" -> bank;
                case "gems" -> gems;
                case "crowns" -> crowns;
                default -> 0;
            };
        }

        private Account with(String name, String field, double value) {
            return switch (field) {
                case "wallet" -> new Account(name, value, bank, gems, crowns);
                case "bank" -> new Account(name, wallet, value, gems, crowns);
                case "gems" -> new Account(name, wallet, bank, value, crowns);
                case "crowns" -> new Account(name, wallet, bank, gems, value);
                default -> this;
            };
        }
    }
}