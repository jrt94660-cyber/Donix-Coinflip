package com.donix.coinflip;

import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;

public interface EconomyProvider {
    BigDecimal balance(OfflinePlayer player);

    boolean withdraw(OfflinePlayer player, BigDecimal amount);

    boolean deposit(OfflinePlayer player, BigDecimal amount);
}