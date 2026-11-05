package com.donix.coinflip;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class DonixCoinflipPlugin extends JavaPlugin {
    private CoinflipService coinflipService;
    private CoinflipMenus menus;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault was not found. Install Vault and an economy plugin with a Vault bridge.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        EconomyProvider economy = VaultEconomyProvider.create();
        if (economy == null) {
            getLogger().severe("No Vault economy provider is registered. Enable EssentialsX Economy, CoinsEngine, ExcellentEconomy, or another Vault-compatible economy.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.coinflipService = new CoinflipService(this, economy);
        BedrockDetector bedrockDetector = new BedrockDetector();
        this.menus = new CoinflipMenus(this, coinflipService, bedrockDetector);

        getServer().getPluginManager().registerEvents(menus, this);

        CoinflipCommand command = new CoinflipCommand(this);
        if (getCommand("cf") != null) {
            getCommand("cf").setExecutor(command);
            getCommand("cf").setTabCompleter(command);
        } else {
            getLogger().severe("The /cf command is missing from plugin.yml.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Donix-Coinflip v" + getDescription().getVersion() + " enabled. Economy: Vault.");
    }

    @Override
    public void onDisable() {
        if (coinflipService != null) {
            coinflipService.refundOutstanding();
        }
    }

    public void openMenu(Player player) {
        if (menus == null) {
            return;
        }
        menus.openMain(player, 0);
    }

    public CoinflipService getCoinflipService() {
        return coinflipService;
    }

    public CoinflipMenus getMenus() {
        return menus;
    }
}