package com.ecosteal.core;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Random;

public final class GameplayListener implements Listener {
    private final JavaPlugin plugin;
    private final EconomyService economy;
    private final ContractService contracts;
    private final PlayerStatsService stats;
    private final ZoneManager zones;
    private final HeistService heists;
    private final GearService gear;
    private final CombatTracker combat;
    private final Random random = new Random();

    public GameplayListener(JavaPlugin plugin, EconomyService economy, ContractService contracts, PlayerStatsService stats, ZoneManager zones, HeistService heists, GearService gear, CombatTracker combat) {
        this.plugin = plugin;
        this.economy = economy;
        this.contracts = contracts;
        this.stats = stats;
        this.zones = zones;
        this.heists = heists;
        this.gear = gear;
        this.combat = combat;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        Player victim = event.getEntity() instanceof Player player ? player : null;
        Player attacker = null;
        if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity) {
            if (byEntity.getDamager() instanceof Player player) attacker = player;
            else if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) attacker = player;
        }
        if (attacker != null && attacker != victim) {
            if (victim != null && (zones.contains("safe_mine", victim.getLocation()) || zones.contains("safe_mine", attacker.getLocation()))) {
                event.setCancelled(true);
                return;
            }
            combat.tag(attacker);
            if (victim != null) combat.tag(victim);
            event.setDamage(event.getDamage() * gear.damageMultiplier(attacker, true) * gear.heldWeaponMultiplier(attacker));
        }
        if (victim != null) {
            event.setDamage(event.getDamage() * gear.damageMultiplier(victim, false));
            heists.onDamage(victim);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        heists.dropBag(victim, victim.getLocation());
        Player killer = diedToPlayer(victim, victim.getKiller()) ? victim.getKiller() : null;
        stats.playerDied(victim, killer);
        if (killer == null || !zones.contains("risk_mine", victim.getLocation())
            || !zones.contains("risk_mine", killer.getLocation())) return;
        long now = System.currentTimeMillis();
        String cooldownPath = "risk-kill-cooldowns." + killer.getUniqueId() + "." + victim.getUniqueId();
        if (plugin.getConfig().getLong(cooldownPath, 0) > now) {
            killer.sendMessage(ChatColor.RED + "No cash stolen: this player is protected by the repeat-kill cooldown.");
            return;
        }
        plugin.getConfig().set(cooldownPath, now + plugin.getConfig().getLong("economy.kill-reward-cooldown-seconds", 1800) * 1000L);
        plugin.saveConfig();
        contracts.riskKill(killer);
        double percent = plugin.getConfig().getDouble("economy.risk-steal-percent", 0.05);
        double cap = plugin.getConfig().getDouble("economy.risk-steal-cap", 500);
        double amount = Math.floor(Math.min(economy.wallet(victim.getUniqueId()) * percent, cap) * 100) / 100;
        double stolen = economy.steal(victim, killer, amount);
        if (stolen > 0) {
            killer.sendMessage(ChatColor.GOLD + "Risk-zone steal: $" + String.format(java.util.Locale.US, "%.2f", stolen) + ".");
            victim.sendMessage(ChatColor.RED + "You lost $" + String.format(java.util.Locale.US, "%.2f", stolen) + " from your wallet. Bank savings are protected.");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        if (zones.contains("safe_mine", event.getBlock().getLocation()) || zones.contains("risk_mine", event.getBlock().getLocation())) {
            contracts.mine(player);
                String chancePath = zones.contains("risk_mine", event.getBlock().getLocation())
                    ? "mining.risk-gem-drop-chance" : "mining.gem-drop-chance";
                double chance = Math.max(0, Math.min(1, plugin.getConfig().getDouble(chancePath,
                    zones.contains("risk_mine", event.getBlock().getLocation()) ? 0.07 : 0.03)));
            if (random.nextDouble() < chance) {
                economy.reward(player, EconomyService.Currency.GEMS, 1, "mine block: " + event.getBlock().getType());
                player.sendMessage(ChatColor.AQUA + "+1 gem");
            }
        }
        heists.loot(player, event.getBlock().getLocation());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (gear.activate(event.getPlayer(), event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onBagPickup(EntityPickupItemEvent event) { heists.pickupBag(event); }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        stats.join(player);
        heists.onJoin(player);
        if (player.hasPlayedBefore()) return;
        double startingBalance = plugin.getConfig().getDouble("economy.starting-balance", 100);
        economy.reward(player, EconomyService.Currency.CASH, startingBalance, "first-join starting balance");
        player.sendMessage(ChatColor.GOLD + "Welcome to Ecosteal!");
        player.sendMessage(ChatColor.YELLOW + "Earn cash with /eco sell, protect savings at a bank, and upgrade custom gear with gems using /gear upgrade.");
        player.sendMessage(ChatColor.YELLOW + "Try /contract, /ecotop, and /heist. Cash in your wallet can be lost in risk mines; bank savings are protected.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stats.quit(event.getPlayer());
        heists.cancelExtraction(event.getPlayer(), null);
        heists.dropBag(event.getPlayer(), event.getPlayer().getLocation());
    }

    private boolean diedToPlayer(Player victim, Player killer) {
        EntityDamageEvent cause = victim.getLastDamageCause();
        if (!(cause instanceof org.bukkit.event.entity.EntityDamageByEntityEvent byEntity)) return false;
        if (byEntity.getDamager() instanceof Player player) return player.equals(killer);
        return byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player && player.equals(killer);
    }
}