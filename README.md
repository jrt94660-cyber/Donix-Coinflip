# Donix-Coinflip

A Paper coinflip plugin with **native Minecraft dialogs for Java players** and a **chest GUI for Floodgate Bedrock players**.

## Features

- `/cf` opens the live coinflip list.
- Java players get a Paper native dialog with join buttons, Create, and Refresh.
- The Java create dialog includes an amount text field and configurable quick amounts (default: 1k, 1m, 1b).
- Java amount selection opens a separate Confirm / Cancel dialog. Either choice returns to the coinflip list.
- Floodgate players get a 54-slot chest menu with open bets, pagination, and Refresh.
- Bedrock players create bets with `/cf create <amount>`, for example `/cf create 1m`.
- `/cf cancel` cancels the player's own open bet and refunds it.
- The creator's stake is withdrawn when the listing is made. A challenger must match the stake. The winner receives the full pot.
- Amount input supports `k`, `m`, `b`, `t`, `q`, and `qq` suffixes.
- Outstanding open bets are refunded when the plugin is disabled cleanly.

## Requirements

- Paper or a Paper-based server.
- Vault and an economy plugin that registers a Vault economy provider. EssentialsX Economy, CoinsEngine, and ExcellentEconomy can be used when they expose their economy through Vault.
- Floodgate is optional and is only needed to detect Bedrock players.

Paper's native Dialog API is used for Java menus. The plugin is compiled against Paper 1.21.11 and Java 21 bytecode. Paper 1.21.11 runs on Java 21; the newer Paper 26.x line requires Java 25. Install the Java runtime required by your server version.

## Commands

| Command | Description |
|---|---|
| `/cf` | Open the coinflip list. |
| `/cf create <amount>` | Create a coinflip with the entered amount. |
| `/cf cancel` | Cancel your open coinflip and refund your stake. |

Examples: `/cf create 1k`, `/cf create 1m`, `/cf create 1b`.

## Installation

1. Install Vault and a Vault-compatible economy provider.
2. Install Floodgate if Bedrock players connect through Geyser.
3. Put `Donix-Coinflip-1.0.2.jar` in the server's `plugins` folder.
4. Restart the server.
5. Edit `plugins/Donix-Coinflip/config.yml` to adjust bet limits, quick amounts, and the currency symbol.

## Economy and wager handling

Coinflips use equal stakes. For example, if Player A lists a 1m bet, 1m is withdrawn and held. Player B must also stake 1m to join. The winner is chosen randomly and receives 2m.

A listing cannot be joined by its creator or joined more than once. The listing is removed before the winner is paid to prevent duplicate joins.

## Build

Run:

```bash
mvn clean package
```

The JAR is written to `target/Donix-Coinflip-1.0.2.jar`. GitHub Actions also builds the plugin on pushes to `main` and uploads the JAR as a workflow artifact.