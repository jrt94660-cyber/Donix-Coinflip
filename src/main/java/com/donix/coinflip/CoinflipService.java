package com.donix.coinflip;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CoinflipService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final DonixCoinflipPlugin plugin;
    private final EconomyProvider economy;
    private final Map<UUID, Offer> offers = new LinkedHashMap<>();

    public CoinflipService(DonixCoinflipPlugin plugin, EconomyProvider economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    public record Offer(UUID id, UUID ownerId, String ownerName, BigDecimal amount, long createdAt) {
    }

    public List<Offer> getOffers() {
        return offers.values().stream()
                .sorted(Comparator.comparingLong(Offer::createdAt).reversed())
                .toList();
    }

    public Offer getOffer(UUID id) {
        return offers.get(id);
    }

    public Offer getOfferOwnedBy(UUID playerId) {
        return offers.values().stream()
                .filter(offer -> offer.ownerId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    public boolean create(Player player, BigDecimal requestedAmount) {
        if (requestedAmount == null) {
            message(player, "&cEnter a valid amount. Examples: 1k, 1m, 1b.");
            return false;
        }

        BigDecimal amount = requestedAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal minimum = BigDecimal.valueOf(plugin.getConfig().getDouble("minimum-bet", 1000.0D));
        BigDecimal maximum = BigDecimal.valueOf(plugin.getConfig().getDouble("maximum-bet", 1_000_000_000.0D));

        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(minimum) < 0
                || amount.compareTo(maximum) > 0) {
            message(player, "&cBet must be between &f" + format(minimum) + " &cand &f" + format(maximum) + "&c.");
            return false;
        }

        if (getOfferOwnedBy(player.getUniqueId()) != null) {
            message(player, "&cYou already have an active coinflip. Use &f/cf cancel &cto cancel it.");
            return false;
        }

        if (economy.balance(player).compareTo(amount) < 0) {
            message(player, "&cYou do not have enough money for that bet.");
            return false;
        }

        if (!economy.withdraw(player, amount)) {
            message(player, "&cYour bet could not be withdrawn. Please try again.");
            return false;
        }

        Offer offer = new Offer(UUID.randomUUID(), player.getUniqueId(), player.getName(), amount, System.currentTimeMillis());
        offers.put(offer.id(), offer);
        message(player, "&aYour coinflip is now listed for &f" + format(amount)
                + "&a. Another player can match your bet.");
        return true;
    }

    public boolean cancel(Player player) {
        Offer offer = getOfferOwnedBy(player.getUniqueId());
        if (offer == null) {
            message(player, "&cYou do not have an active coinflip to cancel.");
            return false;
        }

        if (!economy.deposit(player, offer.amount())) {
            message(player, "&cYour bet could not be refunded. Your coinflip is still listed.");
            return false;
        }

        offers.remove(offer.id());
        message(player, "&eYour coinflip was cancelled. &a" + format(offer.amount()) + " &ahas been refunded.");
        return true;
    }

    public boolean accept(Player challenger, UUID offerId) {
        Offer offer = offers.get(offerId);
        if (offer == null) {
            message(challenger, "&cThat coinflip is no longer available. Refresh the list.");
            return false;
        }

        if (offer.ownerId().equals(challenger.getUniqueId())) {
            message(challenger, "&cYou cannot join your own coinflip.");
            return false;
        }

        if (economy.balance(challenger).compareTo(offer.amount()) < 0) {
            message(challenger, "&cYou do not have enough money to match that bet.");
            return false;
        }

        if (!economy.withdraw(challenger, offer.amount())) {
            message(challenger, "&cYour matching bet could not be withdrawn. Please try again.");
            return false;
        }

        // Remove the listing before rolling so it cannot be joined twice.
        offers.remove(offer.id());

        boolean creatorWins = ThreadLocalRandom.current().nextBoolean();
        UUID winnerId = creatorWins ? offer.ownerId() : challenger.getUniqueId();
        UUID loserId = creatorWins ? challenger.getUniqueId() : offer.ownerId();
        BigDecimal pot = offer.amount().multiply(BigDecimal.valueOf(2));

        OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerId);
        if (!economy.deposit(winner, pot)) {
            boolean creatorRefunded = economy.deposit(Bukkit.getOfflinePlayer(offer.ownerId()), offer.amount());
            boolean challengerRefunded = economy.deposit(challenger, offer.amount());
            if (!creatorRefunded || !challengerRefunded) {
                plugin.getLogger().severe("Coinflip payout and refund failed for offer " + offer.id()
                        + ". Manual economy review may be required.");
            }
            message(challenger, "&cThe payout failed. Both bets were refunded where possible.");
            Player creator = Bukkit.getPlayer(offer.ownerId());
            if (creator != null) {
                message(creator, "&cThe coinflip payout failed. Your bet was refunded where possible.");
            }
            return false;
        }

        String winnerName = winnerId.equals(challenger.getUniqueId()) ? challenger.getName() : offer.ownerName();
        String loserName = loserId.equals(challenger.getUniqueId()) ? challenger.getName() : offer.ownerName();

        message(challenger, winnerId.equals(challenger.getUniqueId())
                ? "&aYou won the coinflip! You received &f" + format(pot) + "&a."
                : "&cYou lost the coinflip. &f" + winnerName + " &cwon &f" + format(pot) + "&c.");

        Player creator = Bukkit.getPlayer(offer.ownerId());
        if (creator != null) {
            message(creator, winnerId.equals(creator.getUniqueId())
                    ? "&aYou won the coinflip! You received &f" + format(pot) + "&a."
                    : "&cYou lost the coinflip. &f" + winnerName + " &cwon &f" + format(pot) + "&c.");
        }

        Bukkit.broadcast(LEGACY.deserialize(plugin.getConfig().getString("messages.prefix", "&8[&bCoinflip&8] &7")
                + "&f" + winnerName + " &awon a coinflip against &f" + loserName
                + " &afor &f" + format(pot) + "&a!"));
        return true;
    }

    public void refundOutstanding() {
        List<Offer> remaining = new ArrayList<>(offers.values());
        for (Offer offer : remaining) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(offer.ownerId());
            if (economy.deposit(owner, offer.amount())) {
                Player online = Bukkit.getPlayer(offer.ownerId());
                if (online != null) {
                    message(online, "&eThe server is closing. Your open coinflip bet of &f"
                            + format(offer.amount()) + " &ehas been refunded.");
                }
            } else {
                plugin.getLogger().severe("Could not refund " + format(offer.amount())
                        + " to " + offer.ownerName() + " for open coinflip " + offer.id());
            }
        }
        offers.clear();
    }

    public String format(BigDecimal amount) {
        BigDecimal absolute = amount.abs();
        BigDecimal divisor = BigDecimal.ONE;
        String suffix = "";
        if (absolute.compareTo(new BigDecimal("1000000000000000000")) >= 0) {
            divisor = new BigDecimal("1000000000000000000");
            suffix = "qq";
        } else if (absolute.compareTo(new BigDecimal("1000000000000000")) >= 0) {
            divisor = new BigDecimal("1000000000000000");
            suffix = "q";
        } else if (absolute.compareTo(new BigDecimal("1000000000000")) >= 0) {
            divisor = new BigDecimal("1000000000000");
            suffix = "t";
        } else if (absolute.compareTo(new BigDecimal("1000000000")) >= 0) {
            divisor = new BigDecimal("1000000000");
            suffix = "b";
        } else if (absolute.compareTo(new BigDecimal("1000000")) >= 0) {
            divisor = new BigDecimal("1000000");
            suffix = "m";
        } else if (absolute.compareTo(new BigDecimal("1000")) >= 0) {
            divisor = new BigDecimal("1000");
            suffix = "k";
        }

        DecimalFormat format = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.US));
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        return symbol + format.format(amount.divide(divisor, 2, RoundingMode.HALF_UP)) + suffix;
    }

    public static BigDecimal parseAmount(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String value = input.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").replace(" ", "");
        BigDecimal multiplier = BigDecimal.ONE;
        if (value.endsWith("qq")) {
            multiplier = new BigDecimal("1000000000000000000");
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("q")) {
            multiplier = new BigDecimal("1000000000000000");
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("t")) {
            multiplier = new BigDecimal("1000000000000");
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("b")) {
            multiplier = new BigDecimal("1000000000");
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = new BigDecimal("1000000");
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("k")) {
            multiplier = new BigDecimal("1000");
            value = value.substring(0, value.length() - 1);
        }

        try {
            return new BigDecimal(value).multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public void message(Player player, String message) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&8[&bCoinflip&8] &7");
        player.sendMessage(LEGACY.deserialize(prefix + message));
    }
}