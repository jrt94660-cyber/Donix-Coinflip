package com.donix.coinflip;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;

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
    private final Map<UUID, ActiveMatch> activeMatches = new LinkedHashMap<>();

    private record ActiveMatch(Offer offer, UUID challengerId, String challengerName,
                               UUID winnerId, String winnerName, String loserName) {
    }

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

        // Remove the listing immediately so it cannot be accepted twice.
        offers.remove(offer.id());

        boolean creatorWins = ThreadLocalRandom.current().nextBoolean();
        UUID winnerId = creatorWins ? offer.ownerId() : challenger.getUniqueId();
        String winnerName = creatorWins ? offer.ownerName() : challenger.getName();
        String loserName = creatorWins ? challenger.getName() : offer.ownerName();
        ActiveMatch match = new ActiveMatch(offer, challenger.getUniqueId(), challenger.getName(),
                winnerId, winnerName, loserName);
        activeMatches.put(offer.id(), match);

        message(challenger, "&eCoinflip accepted! Watch the roll...");
        Player creator = Bukkit.getPlayer(offer.ownerId());
        if (creator != null) {
            message(creator, "&e" + challenger.getName() + " &ejoined your coinflip! Watch the roll...");
        }

        startRoll(offer.id(), match);
        return true;
    }

    /**
     * Runs a private name-vs-name roulette on the two participants' screens only.
     * The winner is selected once with a 50/50 boolean before the animation starts.
     */
    private void startRoll(UUID matchId, ActiveMatch match) {
        int frames = Math.max(12, plugin.getConfig().getInt("roll.animation-frames", 24));
        long interval = Math.max(1L, plugin.getConfig().getLong("roll.interval-ticks", 3L));
        final int[] frame = {0};

        plugin.getServer().getScheduler().runTaskTimer(plugin, task -> {
            ActiveMatch active = activeMatches.get(matchId);
            if (active == null) {
                task.cancel();
                return;
            }

            if (frame[0] >= frames) {
                task.cancel();
                finishMatch(matchId);
                return;
            }

            boolean showCreator = frame[0] % 2 == 0;
            String highlighted = showCreator ? match.offer().ownerName() : match.challengerName();
            String other = showCreator ? match.challengerName() : match.offer().ownerName();
            NamedTextColor highlightColor = showCreator ? NamedTextColor.GOLD : NamedTextColor.AQUA;

            Component title = Component.text(highlighted, highlightColor);
            Component subtitle = Component.text(other + "  vs  " + highlighted
                    + "   •   " + format(match.offer().amount()), NamedTextColor.GRAY);

            showPrivateTitle(match.offer().ownerId(), title, subtitle);
            showPrivateTitle(match.challengerId(), title, subtitle);

            if (frame[0] % 4 == 0) {
                playPrivateSound(match.offer().ownerId(), Sound.UI_BUTTON_CLICK, 0.45f, 1.15f + (frame[0] * 0.015f));
                playPrivateSound(match.challengerId(), Sound.UI_BUTTON_CLICK, 0.45f, 1.15f + (frame[0] * 0.015f));
            }
            frame[0]++;
        }, 0L, interval);
    }

    private void finishMatch(UUID matchId) {
        ActiveMatch match = activeMatches.get(matchId);
        if (match == null) {
            return;
        }

        BigDecimal pot = match.offer().amount().multiply(BigDecimal.valueOf(2));
        OfflinePlayer winner = Bukkit.getOfflinePlayer(match.winnerId());

        if (!economy.deposit(winner, pot)) {
            boolean creatorRefunded = economy.deposit(
                    Bukkit.getOfflinePlayer(match.offer().ownerId()), match.offer().amount());
            boolean challengerRefunded = economy.deposit(
                    Bukkit.getOfflinePlayer(match.challengerId()), match.offer().amount());

            activeMatches.remove(matchId);
            if (!creatorRefunded || !challengerRefunded) {
                plugin.getLogger().severe("Coinflip payout and refund failed for match " + matchId
                        + ". Manual economy review may be required.");
            }

            Component failureTitle = Component.text("PAYOUT FAILED", NamedTextColor.RED);
            Component failureSubtitle = Component.text("Bets refunded where possible", NamedTextColor.GRAY);
            showPrivateTitle(match.offer().ownerId(), failureTitle, failureSubtitle);
            showPrivateTitle(match.challengerId(), failureTitle, failureSubtitle);
            Player creator = Bukkit.getPlayer(match.offer().ownerId());
            Player challenger = Bukkit.getPlayer(match.challengerId());
            if (creator != null) {
                message(creator, "&cThe coinflip payout failed. Your bet was refunded where possible.");
            }
            if (challenger != null) {
                message(challenger, "&cThe coinflip payout failed. Your bet was refunded where possible.");
            }
            return;
        }

        activeMatches.remove(matchId);
        String result = match.winnerName() + " beat " + match.loserName();
        Component winnerTitle = Component.text(match.winnerName(), NamedTextColor.GREEN);
        Component winnerSubtitle = Component.text("WON THE COINFLIP  •  " + format(pot), NamedTextColor.YELLOW);
        Component loserTitle = Component.text(match.winnerName(), NamedTextColor.RED);
        Component loserSubtitle = Component.text("DEFEATED " + match.loserName()
                + "  •  " + format(pot), NamedTextColor.GRAY);

        for (UUID participant : List.of(match.offer().ownerId(), match.challengerId())) {
            boolean isWinner = participant.equals(match.winnerId());
            showPrivateTitle(participant, isWinner ? winnerTitle : loserTitle,
                    isWinner ? winnerSubtitle : loserSubtitle);
            playPrivateSound(participant,
                    isWinner ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_BASS,
                    0.8f, isWinner ? 1.0f : 0.75f);
        }

        Player creator = Bukkit.getPlayer(match.offer().ownerId());
        Player challenger = Bukkit.getPlayer(match.challengerId());

        if (creator != null) {
            message(creator, match.winnerId().equals(creator.getUniqueId())
                    ? "&aYou won the coinflip! You received &f" + format(pot) + "&a."
                    : "&cYou lost the coinflip. &f" + match.winnerName() + " &cwon &f" + format(pot) + "&c.");
        }
        if (challenger != null) {
            message(challenger, match.winnerId().equals(challenger.getUniqueId())
                    ? "&aYou won the coinflip! You received &f" + format(pot) + "&a."
                    : "&cYou lost the coinflip. &f" + match.winnerName() + " &cwon &f" + format(pot) + "&c.");
        }

        // No server-wide broadcast: only the two players in this match see the roll and result.
        plugin.getLogger().fine("Private coinflip result: " + result + " for " + format(pot));
    }

    private void showPrivateTitle(UUID playerId, Component title, Component subtitle) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }

        Title.Times times = Title.Times.times(
                Duration.ofMillis(80),
                Duration.ofMillis(650),
                Duration.ofMillis(100)
        );
        player.showTitle(Title.title(title, subtitle, times));
    }

    private void playPrivateSound(UUID playerId, Sound sound, float volume, float pitch) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
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

        // A server shutdown during the animation must not leave either player's stake held.
        List<ActiveMatch> matches = new ArrayList<>(activeMatches.values());
        for (ActiveMatch match : matches) {
            boolean creatorRefunded = economy.deposit(
                    Bukkit.getOfflinePlayer(match.offer().ownerId()), match.offer().amount());
            boolean challengerRefunded = economy.deposit(
                    Bukkit.getOfflinePlayer(match.challengerId()), match.offer().amount());
            if (!creatorRefunded || !challengerRefunded) {
                plugin.getLogger().severe("Could not refund both players for active coinflip " + match.offer().id());
            }
        }
        activeMatches.clear();
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