package com.donix.coinflip;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CoinflipMenus implements Listener {
    private static final String KEY_PREFIX = "donix:cf/";
    private final DonixCoinflipPlugin plugin;
    private final CoinflipService service;
    private final BedrockDetector bedrockDetector;

    public CoinflipMenus(DonixCoinflipPlugin plugin, CoinflipService service, BedrockDetector bedrockDetector) {
        this.plugin = plugin;
        this.service = service;
        this.bedrockDetector = bedrockDetector;
    }

    public void openMain(Player player, int page) {
        if (plugin.getConfig().getBoolean("bedrock-chest-menu", true) && bedrockDetector.isBedrock(player)) {
            openBedrock(player, page);
            return;
        }
        openJavaList(player, page);
    }

    private void openJavaList(Player player, int requestedPage) {
        List<CoinflipService.Offer> offers = service.getOffers();
        int pageSize = Math.max(1, plugin.getConfig().getInt("java-page-size", 12));
        int pages = Math.max(1, (int) Math.ceil(offers.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int start = page * pageSize;
        int end = Math.min(offers.size(), start + pageSize);

        StringBuilder body = new StringBuilder();
        body.append("Open coinflips").append("\n");
        body.append("Choose a player below to match their bet. The winner receives both bets.").append("\n\n");

        if (offers.isEmpty()) {
            body.append("There are currently no open coinflips.").append("\n");
        } else {
            for (int index = start; index < end; index++) {
                CoinflipService.Offer offer = offers.get(index);
                body.append(offer.ownerName())
                        .append("  •  ")
                        .append(service.format(offer.amount()))
                        .append(offer.ownerId().equals(player.getUniqueId()) ? "  (Your bet)" : "")
                        .append("\n");
            }
        }
        body.append("\nPage ").append(page + 1).append(" / ").append(pages);

        List<ActionButton> actions = new ArrayList<>();
        for (int index = start; index < end; index++) {
            CoinflipService.Offer offer = offers.get(index);
            if (offer.ownerId().equals(player.getUniqueId())) {
                actions.add(button("Cancel your " + service.format(offer.amount()) + " bet",
                        "Refund your open coinflip bet",
                        KEY_PREFIX + "list/cancel/" + offer.id()));
            } else {
                actions.add(button("Join " + offer.ownerName() + "  •  " + service.format(offer.amount()),
                        "Match this bet for a chance to win " + service.format(offer.amount().multiply(BigDecimal.valueOf(2))),
                        KEY_PREFIX + "list/join/" + offer.id()));
            }
        }

        actions.add(button("Create coinflip", "Choose an amount to put up", KEY_PREFIX + "list/create"));
        actions.add(button("Refresh list", "Reload the current open coinflips", KEY_PREFIX + "list/refresh/" + page));
        if (page > 0) {
            actions.add(button("Previous page", "View the previous page", KEY_PREFIX + "list/page/" + (page - 1)));
        }
        if (page + 1 < pages) {
            actions.add(button("Next page", "View the next page", KEY_PREFIX + "list/page/" + (page + 1)));
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Donix Coinflip", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(body.toString(), NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions, null, 2)));
        player.showDialog(dialog);
    }

    private void openJavaCreate(Player player) {
        List<ActionButton> actions = new ArrayList<>();
        List<Long> presets = plugin.getConfig().getLongList("presets");
        if (presets.isEmpty()) {
            presets = List.of(1_000L, 1_000_000L, 1_000_000_000L);
        }

        for (long preset : presets) {
            if (preset <= 0) {
                continue;
            }
            BigDecimal amount = BigDecimal.valueOf(preset);
            String amountText = amount.stripTrailingZeros().toPlainString();
            actions.add(button("Use " + service.format(amount), "Select this suggested amount",
                    KEY_PREFIX + "create/preset/" + amountText));
        }

        actions.add(button("Confirm typed amount", "Continue using the amount entered above",
                KEY_PREFIX + "create/confirm-input"));
        actions.add(button("Cancel", "Return to the coinflip list", KEY_PREFIX + "create/cancel"));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Create a Coinflip", NamedTextColor.AQUA))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text(
                                        "Enter your bet amount or choose one of the suggested amounts below.\n"
                                                + "Your money is held until the coinflip is joined or cancelled.",
                                        NamedTextColor.GRAY))
                        ))
                        .inputs(List.of(
                                DialogInput.text("amount", Component.text("Bet amount"))
                                        .initial("1000")
                                        .maxLength(24)
                                        .width(300)
                                        .labelVisible(true)
                                        .build()
                        ))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions, null, 2)));
        player.showDialog(dialog);
    }

    private void openJavaConfirm(Player player, BigDecimal amount) {
        String amountText = amount.stripTrailingZeros().toPlainString();
        BigDecimal pot = amount.multiply(BigDecimal.valueOf(2));
        ActionButton confirm = button("Confirm " + service.format(amount),
                "Hold this amount and publish your coinflip",
                KEY_PREFIX + "confirm/yes/" + amountText);
        ActionButton cancel = button("Cancel",
                "Return to the coinflip list without spending money",
                KEY_PREFIX + "confirm/no");

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Confirm Coinflip", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Your bet: " + service.format(amount) + "\n"
                                        + "Potential pot: " + service.format(pot) + "\n\n"
                                        + "Your bet will be withdrawn when you confirm.",
                                NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(confirm, cancel)));
        player.showDialog(dialog);
    }

    private ActionButton button(String label, String tooltip, String actionKey) {
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .width(220)
                .action(DialogAction.customClick(Key.key(actionKey), null))
                .build();
    }

    @EventHandler
    public void onDialogClick(PlayerCustomClickEvent event) {
        String key = event.getIdentifier().asString();
        if (!key.startsWith(KEY_PREFIX)) {
            return;
        }
        if (!(event.getCommonConnection() instanceof PlayerGameConnection connection)) {
            return;
        }

        Player player = connection.getPlayer();

        if (key.equals(KEY_PREFIX + "list/create")) {
            openJavaCreate(player);
            return;
        }
        if (key.startsWith(KEY_PREFIX + "list/refresh/")) {
            openJavaList(player, parsePage(key.substring((KEY_PREFIX + "list/refresh/").length())));
            return;
        }
        if (key.startsWith(KEY_PREFIX + "list/page/")) {
            openJavaList(player, parsePage(key.substring((KEY_PREFIX + "list/page/").length())));
            return;
        }
        if (key.startsWith(KEY_PREFIX + "list/join/")) {
            UUID offerId = parseUuid(key.substring((KEY_PREFIX + "list/join/").length()));
            if (offerId != null) {
                service.accept(player, offerId);
            }
            openJavaList(player, 0);
            return;
        }
        if (key.startsWith(KEY_PREFIX + "list/cancel/")) {
            UUID offerId = parseUuid(key.substring((KEY_PREFIX + "list/cancel/").length()));
            CoinflipService.Offer offer = offerId == null ? null : service.getOffer(offerId);
            if (offer != null && offer.ownerId().equals(player.getUniqueId())) {
                service.cancel(player);
            }
            openJavaList(player, 0);
            return;
        }
        if (key.equals(KEY_PREFIX + "create/cancel")) {
            openJavaList(player, 0);
            return;
        }
        if (key.equals(KEY_PREFIX + "create/confirm-input")) {
            if (event.getDialogResponseView() == null) {
                openJavaCreate(player);
                return;
            }
            BigDecimal amount = CoinflipService.parseAmount(event.getDialogResponseView().getText("amount"));
            if (amount == null) {
                service.message(player, "&cEnter a valid amount, such as &f1k, 1m, or 1b&c.");
                openJavaCreate(player);
            } else {
                openJavaConfirm(player, amount);
            }
            return;
        }
        if (key.startsWith(KEY_PREFIX + "create/preset/")) {
            BigDecimal amount = CoinflipService.parseAmount(
                    key.substring((KEY_PREFIX + "create/preset/").length()));
            if (amount == null) {
                openJavaCreate(player);
            } else {
                openJavaConfirm(player, amount);
            }
            return;
        }
        if (key.startsWith(KEY_PREFIX + "confirm/yes/")) {
            BigDecimal amount = CoinflipService.parseAmount(
                    key.substring((KEY_PREFIX + "confirm/yes/").length()));
            service.create(player, amount);
            openJavaList(player, 0);
            return;
        }
        if (key.equals(KEY_PREFIX + "confirm/no")) {
            openJavaList(player, 0);
        }
    }

    private int parsePage(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private void openBedrock(Player player, int requestedPage) {
        List<CoinflipService.Offer> offers = service.getOffers();
        int pageSize = 45;
        int pages = Math.max(1, (int) Math.ceil(offers.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));

        BedrockMenuHolder holder = new BedrockMenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                Component.text("Coinflip List", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;

        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE,
                Component.text(" ", NamedTextColor.GRAY), List.of());
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }

        int start = page * pageSize;
        int end = Math.min(offers.size(), start + pageSize);
        for (int index = start; index < end; index++) {
            CoinflipService.Offer offer = offers.get(index);
            int slot = index - start;
            boolean own = offer.ownerId().equals(player.getUniqueId());
            String title = own ? "Your coinflip" : "Coinflip by " + offer.ownerName();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Bet: " + service.format(offer.amount()), NamedTextColor.YELLOW));
            lore.add(Component.text("Potential pot: "
                    + service.format(offer.amount().multiply(BigDecimal.valueOf(2))), NamedTextColor.GREEN));
            lore.add(Component.text(own ? "Click to cancel and refund" : "Click to match this bet",
                    own ? NamedTextColor.RED : NamedTextColor.AQUA));
            Material icon = own ? Material.GOLD_BLOCK : Material.GOLD_INGOT;
            inventory.setItem(slot, item(icon, Component.text(title, own ? NamedTextColor.GOLD : NamedTextColor.YELLOW), lore));
            holder.offerSlots.put(slot, offer.id());
        }

        inventory.setItem(45, item(Material.ARROW, Component.text("Previous page", NamedTextColor.WHITE),
                List.of(Component.text("Page " + Math.max(1, page), NamedTextColor.GRAY))));
        inventory.setItem(49, item(Material.WRITABLE_BOOK, Component.text("Create coinflip", NamedTextColor.GREEN),
                List.of(
                        Component.text("Create a new wager using a command.", NamedTextColor.GRAY),
                        Component.text("/cf create <amount>", NamedTextColor.YELLOW),
                        Component.text("Example: /cf create 1m", NamedTextColor.DARK_GRAY)
                )));
        inventory.setItem(50, item(Material.CLOCK, Component.text("Refresh list", NamedTextColor.AQUA),
                List.of(Component.text("Reload open coinflips", NamedTextColor.GRAY))));
        inventory.setItem(53, item(Material.ARROW, Component.text("Next page", NamedTextColor.WHITE),
                List.of(Component.text("Page " + Math.min(pages, page + 2), NamedTextColor.GRAY))));

        player.openInventory(inventory);
    }

    private ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(name);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof BedrockMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getSlot();
        UUID offerId = holder.offerSlots.get(slot);
        if (offerId != null) {
            CoinflipService.Offer offer = service.getOffer(offerId);
            if (offer != null && offer.ownerId().equals(player.getUniqueId())) {
                service.cancel(player);
            } else {
                service.accept(player, offerId);
            }
            openBedrock(player, holder.page);
            return;
        }

        if (slot == 45) {
            openBedrock(player, Math.max(0, holder.page - 1));
        } else if (slot == 49) {
            service.message(player, "&7To create a coinflip, type &f/cf create <amount>&7. Example: &f/cf create 1m&7.");
        } else if (slot == 50) {
            openBedrock(player, holder.page);
        } else if (slot == 53) {
            openBedrock(player, holder.page + 1);
        }
    }

    private static final class BedrockMenuHolder implements InventoryHolder {
        private final int page;
        private final Map<Integer, UUID> offerSlots = new HashMap<>();
        private Inventory inventory;

        private BedrockMenuHolder(int page) {
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}