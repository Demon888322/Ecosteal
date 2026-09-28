# Ecosteal Core 2

**A risk-and-reward economy for Paper.** Build a wallet, protect savings in the bank, take fights in contested mines, and carry heist loot to extraction.

| | |
| --- | --- |
| Minecraft | Paper 1.21.11 |
| Java | 21 |
| Release | 2.0.0 |
| Author | its_Demon |

## Features

- Wallet, protected bank, gems, and crowns, with transaction history
- Vault economy bridge for wallet cash; bank balances stay protected
- Safe and risk mining zones, including configurable PvP cash theft
- Higher configurable gem-drop odds in risk mines than safe mines
- Timed heists with extraction, recoverable bags, restart persistence, and a live countdown bar
- UTC daily contracts for mining, risk-zone PvP, and heist extractions
- Heist challenge awards configurable Crowns; farming crops can be sold for Cash
- Custom weapons, armor sets, and gem upgrades
- Crossplay-friendly right-click abilities; Geyser is optional
- SQLite account storage with automatic import of legacy YAML data
- Persistent kill, best-streak, successful-extraction, and playtime leaderboards
- A first-join guide to core earning, banking, upgrade, and risk commands

## Requirements

- Paper 1.21.11
- Java 21
- Vault is optional. Geyser and Floodgate are optional.

## Install

1. Build the plugin with Maven:

	```sh
	mvn package
	```

2. Copy `target/EcostealCore2-2.0.0.jar` into the server's `plugins` directory.
3. Start the server. Configure the zones before using banks, mines, or heists.

### Upgrade from an earlier release

On first startup, Ecosteal Core 2 copies existing configuration, economy, contract, heist, and player-stat files from `plugins/Ecosteal` or `plugins/EcoStealCore` when matching files are not already present in `plugins/EcostealCore2`. Legacy account and transaction YAML is imported into `economy.db` once; the original files are left in place as backups.

## Configure zones

As an operator, stand at one corner of a cuboid and run `/ecoadmin zone <name> 1`, then stand at the opposite corner and run `/ecoadmin zone <name> 2`.

| Zone | Purpose |
| --- | --- |
| `safe_mine` | PvP is disabled; mining can award gems and count toward the mining contract. |
| `risk_mine` | PvP is enabled; player-caused kills can steal wallet cash, subject to configured caps and cooldowns. |
| `bank` | Allows `/eco deposit` and `/eco withdraw` outside combat. |
| `heist_objective` | Blocks here can yield event loot; `/heist loot` also works in the zone. |
| `extraction` | Use `/heist extract` and remain in the zone until the countdown completes. |

Zone coordinates are unset by default. Configure all required corners before play.

## Commands

| Command | Description |
| --- | --- |
| `/eco balance` | View wallet, bank, gems, and crowns. |
| `/eco pay <player> <amount>` | Pay another online player from your wallet. |
| `/eco sell <item> [count]` | Sell configured items from your inventory. |
| `/eco deposit <amount or all>` | Move wallet cash into the bank at a bank zone. |
| `/eco withdraw <amount or all>` | Move bank cash into your wallet at a bank zone. |
| `/baltop` | View the top wallet-plus-bank cash balances. |
| `/ecotop [cash\|kills\|streak\|heists\|playtime]` | View cash or gameplay leaderboards. |
| `/gear upgrade` | Upgrade held custom gear using gems. |
| `/heist loot` | Collect loot at an active objective. |
| `/heist extract` | Start extraction while carrying a heist bag. |
| `/heist start [minutes]` | Start a heist event. Operator permission required. |
| `/heist stop` | Stop a heist event. Operator permission required. |
| `/contract` | View today's contract progress. |
| `/contract claim <id>` | Claim a contract; valid IDs are `mining`, `risk`, and `heist`. |
| `/ecoadmin zone <name> <corner>` | Set a zone corner; corner is `1` or `2`. Operator permission required. |
| `/ecoadmin event start [minutes]` | Start a heist event. Operator permission required. |
| `/ecoadmin event stop` | Stop a heist event. Operator permission required. |
| `/ecoadmin give <player> <currency> <amount>` | Grant `cash`, `gems`, or `crowns`. Operator permission required. |
| `/ecoadmin transactions <player> [limit]` | Inspect recent transactions. Operator permission required. |
| `/gear give <player> <item>` | Give custom gear. Operator permission required. |

## Data and configuration

- `plugins/EcostealCore2/config.yml`: zones, economy tuning, mining, heists, contracts, and gear settings
- `plugins/EcostealCore2/economy.db`: account balances and transaction history
- `plugins/EcostealCore2/contracts.yml`: daily contract progress
- `plugins/EcostealCore2/heist-state.yml`: active event and carried bag state
- `plugins/EcostealCore2/player-stats.yml`: kills, streaks, heists, and playtime

Other plugins can award currency through the public `EcoStealCore#getEconomy().reward(player, currency, amount, reason)` service. Use your ranks/shop plugins to sell Crowns for cosmetic ranks, titles, and effects; this plugin does not provide purchasable combat advantages. Vault exposes wallet cash only; protected bank funds are intentionally excluded. Land claims, spawn protection, ranks, shop displays, and Java/Bedrock access are configured by the server's respective plugins (including Geyser/Floodgate for Bedrock).