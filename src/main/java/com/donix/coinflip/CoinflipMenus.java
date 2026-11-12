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

        // Eight listings fit into four paired rows, leaving the final row for Refresh and Create.
        int configuredPageSize = plugin.getConfig().getInt("java-page-size", 8);
        int pageSize = Math.min(8, Math.max(1, configuredPageSize));
        int pages = Math.max(1, (int) Math.ceil(offers.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        int startIndex = page * pageSize;
        int endIndex = Math.min(offers.size(), startIndex + pageSize);

        StringBuilder body = new StringBuilder();
        body.append("Choose an open coinflip to match the player's bet.").append("\n\n");
        if (offers.isEmpty()) {
            body.append("There are currently no open coinflips.");
        } else {
            body.append("Open bets: ").append(offers.size());
            if (pages > 1) {
                body.append("    •    Page ").append(page + 1).append(" / ").append(pages);
            }
        }

        List<ActionButton> actions = new ArrayList<>();

        // Listings are authored in pairs, so the dialog displays two compact buttons per row.
        for (int index = startIndex; index < endIndex; index++) {
            CoinflipService.Offer offer = offers.get(index);
            if (!offer.ownerId().equals(player.getUniqueId())) {
                actions.add(button(offer.ownerName() + " • " + service.format(offer.amount()),
                        "View bet details and confirm before joining",
                        KEY_PREFIX + "list/join/" + offer.id(), 158));
            }
        }

        // Keep page controls above the footer, with Refresh and Create always in the bottom row.
        if (page > 0) {
            actions.add(button("Previous", "View the previous page",
                    KEY_PREFIX + "list/page/" + (page - 1), 158));
        }
        if (page + 1 < pages) {
            actions.add(button("Next", "View the next page",
                    KEY_PREFIX + "list/page/" + (page + 1), 158));
        }

        actions.add(button("Refresh", "Reload the current coinflip list",
                KEY_PREFIX + "list/refresh/" + page, 158));

        // A player may have only one open wager. Hide Create until their own offer is cancelled or matched.
        if (service.getOfferOwnedBy(player.getUniqueId()) == null) {
            actions.add(button("Create", "Create a new coinflip",
                    KEY_PREFIX + "list/create", 158));
        }

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("CoinFlip", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(
                                Component.text(body.toString(), NamedTextColor.GRAY))))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(actions, null, 2)));
        player.showDialog(dialog);
    }

    private void openJavaCreate(Player player) {
        openJavaCreate(player, "1000");
    }

    private void openJavaCreate(Player player, String initialAmount) {
        List<Long> presets = plugin.getConfig().getLongList("presets");
        if (presets.size() < 3) {
            presets = List.of(1_000L, 1_000_000L, 1_000_000_000L);
        }

        List<ActionButton> actions = new ArrayList<>();
        for (long preset : presets.stream().limit(3).toList()) {
            if (preset <= 0) {
                continue;
            }
            BigDecimal amount = BigDecimal.valueOf(preset);
            String amountText = amount.stripTrailingZeros().toPlainString();
            actions.add(button(service.format(amount),
                    "Fill the amount field with this suggested bet",
                    KEY_PREFIX + "create/preset/" + amountText, 112));
        }

        actions.add(button("Confirm", "Create your coinflip using the amount above",
                KEY_PREFIX + "create/confirm-input", 112));
        actions.add(button("Cancel", "Return to the coinflip list without creating a bet",
                KEY_PREFIX + "create/cancel", 112));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Create CoinFlip", NamedTextColor.AQUA))
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Type your bet amount, or choose one of the three suggestions.\n"
                                        + "A suggestion fills the field; press Confirm when ready.",
                                NamedTextColor.GRAY))))
                        .inputs(List.of(
                                DialogInput.text("amount", Component.text("Amount"))
                                        .initial(initialAmount)
                                        .maxLength(24)
                                        .width(300)
                                        .labelVisible(true)
                                        .build()
                        ))
                        .canCloseWithEscape(true)
                        .build())
                // Three compact preset buttons share the first row; Confirm and Cancel are below.
                .type(DialogType.multiAction(actions, null, 3)));
        player.showDialog(dialog);
    }

    private void openJavaJoinConfirm(Player player, UUID offerId) {
        CoinflipService.Offer offer = service.getOffer(offerId);
        if (offer == null) {
            service.message(player, "&cThat coinflip is no longer available. Refresh the list.");
            openJavaList(player, 0);
            return;
        }
        if (offer.ownerId().equals(player.getUniqueId())) {
            openJavaList(player, 0);
            return;
        }

        BigDecimal total = offer.amount().multiply(BigDecimal.valueOf(2));
        List<ActionButton> actions = List.of(
                button("Confirm", "Join this coinflip. Your bet will be taken immediately.",
                        KEY_PREFIX + "join/confirm/" + offerId, 112),
                button("Back", "Return to the coinflip listing",
                        KEY_PREFIX + "join/back", 112)
        );
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Confirm CoinFlip", NamedTextColor.AQUA))
                        .body(List.of(
                                DialogBody.plainMessage(Component.text(
                                        "You are matching " + offer.ownerName() + "'s bet of " + service.format(offer.amount()) + ".",
                                        NamedTextColor.GRAY)),
                                DialogBody.plainMessage(Component.text(
                                        "WARNING: NO REFUNDS. Once confirmed, your bet cannot be refunded if you lose or change your mind.",
                                        NamedTextColor.RED)),
                                DialogBody.plainMessage(Component.text(
                                        "Total pot: " + service.format(total),
                                        NamedTextColor.GREEN))
                        ))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.confirmation(actions.get(0), actions.get(1))));
        player.showDialog(dialog);
    }

    private ActionButton button(String label, String tooltip, String actionKey) {
        return button(label, tooltip, actionKey, 158);
    }

    private ActionButton button(String label, String tooltip, String actionKey, int width) {
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .width(width)
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
                openJavaJoinConfirm(player, offerId);
            } else {
                openJavaList(player, 0);
            }
            return;
        }
        if (key.startsWith(KEY_PREFIX + "join/confirm/")) {
            UUID offerId = parseUuid(key.substring((KEY_PREFIX + "join/confirm/").length()));
            if (offerId != null) {
                service.accept(player, offerId);
            }
            openJavaList(player, 0);
            return;
        }
        if (key.equals(KEY_PREFIX + "join/back")) {
            openJavaList(player, 0);
            return;
        }
        if (key.equals(KEY_PREFIX + "create/cancel")) {
            openJavaList(player, 0);
            return;
        }
        if (key.equals(KEY_PREFIX + "create/confirm-input")) {
            String input = event.getDialogResponseView() == null
                    ? null : event.getDialogResponseView().getText("amount");
            BigDecimal amount = CoinflipService.parseAmount(input);
            if (amount == null) {
                service.message(player, "&cEnter a valid amount, such as &f1k, 1m, or 1b&c.");
                openJavaCreate(player, input == null ? "" : input);
            } else {
                service.create(player, amount);
                openJavaList(player, 0);
            }
            return;
        }
        if (key.startsWith(KEY_PREFIX + "create/preset/")) {
            String preset = key.substring((KEY_PREFIX + "create/preset/").length());
            // Reopen the same form with the selected preset filled into the text input.
            openJavaCreate(player, preset);
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
            lore.add(Component.text(own ? "Your active coinflip" : "Click to review and join",
                    own ? NamedTextColor.GRAY : NamedTextColor.AQUA));
            Material icon = own ? Material.GOLD_BLOCK : Material.GOLD_INGOT;
            inventory.setItem(slot, item(icon, Component.text(title, own ? NamedTextColor.GOLD : NamedTextColor.YELLOW), lore));
            if (!own) {
                holder.offerSlots.put(slot, offer.id());
            }
        }

        inventory.setItem(45, item(Material.ARROW, Component.text("Previous page", NamedTextColor.WHITE),
                List.of(Component.text("Page " + Math.max(1, page), NamedTextColor.GRAY))));
        if (service.getOfferOwnedBy(player.getUniqueId()) == null) {
            inventory.setItem(49, item(Material.WRITABLE_BOOK, Component.text("Create coinflip", NamedTextColor.GREEN),
                    List.of(
                            Component.text("Create a new wager using a command.", NamedTextColor.GRAY),
                            Component.text("/cf create <amount>", NamedTextColor.YELLOW),
                            Component.text("Example: /cf create 1m", NamedTextColor.DARK_GRAY)
                    )));
        } else {
            inventory.setItem(49, filler);
        }
        inventory.setItem(50, item(Material.CLOCK, Component.text("Refresh list", NamedTextColor.AQUA),
                List.of(Component.text("Reload open coinflips", NamedTextColor.GRAY))));
        inventory.setItem(53, item(Material.ARROW, Component.text("Next page", NamedTextColor.WHITE),
                List.of(Component.text("Page " + Math.min(pages, page + 2), NamedTextColor.GRAY))));

        player.openInventory(inventory);
    }

    private void openBedrockJoinConfirm(Player player, UUID offerId, int returnPage) {
        CoinflipService.Offer offer = service.getOffer(offerId);
        if (offer == null || offer.ownerId().equals(player.getUniqueId())) {
            openBedrock(player, returnPage);
            return;
        }
        BedrockConfirmHolder holder = new BedrockConfirmHolder(offerId, returnPage);
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text("Confirm Coinflip", NamedTextColor.DARK_GRAY));
        holder.inventory = inventory;
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ", NamedTextColor.GRAY), List.of());
        for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, filler);
        inventory.setItem(13, item(Material.GOLD_INGOT,
                Component.text("Match " + service.format(offer.amount()), NamedTextColor.GOLD),
                List.of(
                        Component.text("Player: " + offer.ownerName(), NamedTextColor.GRAY),
                        Component.text("Your bet: " + service.format(offer.amount()), NamedTextColor.YELLOW),
                        Component.text("Total pot: " + service.format(offer.amount().multiply(BigDecimal.valueOf(2))), NamedTextColor.GREEN),
                        Component.text("WARNING: NO REFUNDS after confirming.", NamedTextColor.RED)
                )));
        inventory.setItem(11, item(Material.LIME_CONCRETE, Component.text("CONFIRM", NamedTextColor.GREEN),
                List.of(Component.text("Your bet will be taken immediately.", NamedTextColor.GRAY))));
        inventory.setItem(15, item(Material.RED_CONCRETE, Component.text("BACK", NamedTextColor.RED),
                List.of(Component.text("Return to coinflip listing.", NamedTextColor.GRAY))));
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
        InventoryHolder topHolder = event.getView().getTopInventory().getHolder(false);
        if (!(topHolder instanceof BedrockMenuHolder) && !(topHolder instanceof BedrockConfirmHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        int slot = event.getSlot();
        if (topHolder instanceof BedrockConfirmHolder confirm) {
            if (slot == 11) {
                service.accept(player, confirm.offerId);
                openBedrock(player, 0);
            } else if (slot == 15) {
                openBedrock(player, confirm.returnPage);
            }
            return;
        }
        BedrockMenuHolder holder = (BedrockMenuHolder) topHolder;
        UUID offerId = holder.offerSlots.get(slot);
        if (offerId != null) {
            CoinflipService.Offer offer = service.getOffer(offerId);
            if (offer != null && !offer.ownerId().equals(player.getUniqueId())) {
                openBedrockJoinConfirm(player, offerId, holder.page);
            } else {
                openBedrock(player, holder.page);
            }
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

    private static final class BedrockConfirmHolder implements InventoryHolder {
        private final UUID offerId;
        private final int returnPage;
        private Inventory inventory;

        private BedrockConfirmHolder(UUID offerId, int returnPage) {
            this.offerId = offerId;
            this.returnPage = returnPage;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
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