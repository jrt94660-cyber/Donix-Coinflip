package com.donix.coinflip;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.math.BigDecimal;

public final class VaultEconomyProvider implements EconomyProvider {
    private final Economy economy;

    private VaultEconomyProvider(Economy economy) {
        this.economy = economy;
    }

    public static VaultEconomyProvider create() {
        RegisteredServiceProvider<Economy> registration =
                Bukkit.getServicesManager().getRegistration(Economy.class);
        return registration == null ? null : new VaultEconomyProvider(registration.getProvider());
    }

    @Override
    public BigDecimal balance(OfflinePlayer player) {
        return BigDecimal.valueOf(Math.max(0.0D, economy.getBalance(player)));
    }

    @Override
    public boolean withdraw(OfflinePlayer player, BigDecimal amount) {
        return economy.withdrawPlayer(player, amount.doubleValue()).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer player, BigDecimal amount) {
        return economy.depositPlayer(player, amount.doubleValue()).transactionSuccess();
    }
}