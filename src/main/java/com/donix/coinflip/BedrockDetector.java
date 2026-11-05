package com.donix.coinflip;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

public final class BedrockDetector {
    public boolean isBedrock(Player player) {
        if (Bukkit.getPluginManager().getPlugin("floodgate") == null
                && Bukkit.getPluginManager().getPlugin("Floodgate") == null) {
            return false;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method check = apiClass.getMethod("isFloodgatePlayer", java.util.UUID.class);
            return Boolean.TRUE.equals(check.invoke(api, player.getUniqueId()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return false;
        }
    }
}