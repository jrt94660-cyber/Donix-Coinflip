package com.donix.coinflip;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public final class CoinflipCommand implements CommandExecutor, TabCompleter {
    private final DonixCoinflipPlugin plugin;

    public CoinflipCommand(DonixCoinflipPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return true;
        }

        if (!player.hasPermission("donixcoinflip.use")) {
            plugin.getCoinflipService().message(player, "&cYou do not have permission to use coinflip.");
            return true;
        }

        if (args.length == 0) {
            plugin.openMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> {
                if (args.length != 2) {
                    plugin.getCoinflipService().message(player, "&cUsage: &f/cf create <amount>");
                    return true;
                }
                CoinflipService service = plugin.getCoinflipService();
                service.create(player, CoinflipService.parseAmount(args[1]));
                plugin.openMenu(player);
            }
            case "cancel" -> {
                plugin.getCoinflipService().cancel(player);
                plugin.openMenu(player);
            }
            default -> plugin.getCoinflipService().message(player,
                    "&7Use &f/cf &7to open the list, &f/cf create <amount> &7to create, or &f/cf cancel &7to cancel.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "cancel").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return List.of("1k", "1m", "1b").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}