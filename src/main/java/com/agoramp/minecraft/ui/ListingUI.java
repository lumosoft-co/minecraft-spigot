package com.agoramp.minecraft.ui;

import com.agoramp.controller.Storefront;
import com.agoramp.error.GraphQLError;
import com.agoramp.kyori.adventure.text.format.NamedTextColor;
import com.agoramp.minecraft.controller.ListingController;
import com.agoramp.minecraft.models.graphql.CartAddProductMutation;
import com.agoramp.minecraft.models.graphql.CartRemoveProductMutation;
import com.agoramp.minecraft.models.graphql.ListingQuery;
import com.agoramp.minecraft.models.graphql.fragment.CartInfo;
import com.agoramp.minecraft.models.graphql.fragment.Category;
import com.agoramp.minecraft.models.graphql.fragment.Product;
import com.agoramp.minecraft.util.MinecraftUtil;
import com.agoramp.minecraft.util.api.text.Translation;
import com.agoramp.minecraft.util.api.text.transform.ClickTransform;
import com.agoramp.minecraft.util.api.ui.UserInterface;
import com.agoramp.minecraft.util.data.packets.models.ClickWindowPacket;
import com.agoramp.kyori.adventure.text.Component;
import com.agoramp.kyori.adventure.text.ComponentLike;
import com.agoramp.kyori.adventure.text.event.ClickEvent;
import com.agoramp.kyori.adventure.text.format.TextDecoration;
import com.apollographql.apollo3.api.Error;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.agoramp.reactive.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ListingUI extends UserInterface<ItemStack> {
    private int expandedCategory = -1;
    private Category current;
    private List<ListingQuery.Category> listing;
    private CartInfo cart;
    private CompletableFuture<CartInfo> cartFuture;
    private boolean cartOpen;
    private Object action;

    public ListingUI() {
        super(54, Translation.create("ui.listing.title",
                "shop", ListingController.INSTANCE.getShop().title,
                "category", ListingController.INSTANCE.getListing().get(0).category.title,
                "cartCount", 0
        ));
        listing = ListingController.INSTANCE.getListing();
        current = listing.get(0).category;
    }

    @Override
    public void open(UUID id, boolean render) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        cartFuture = new CompletableFuture<>();
        super.open(id, render);
        ListingController.INSTANCE
                .getListing(player)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(t -> {
                    listing = t.getT2();
                    cart = t.getT1();
                    cartFuture.complete(cart);
                    String category = current.handle;
                    current = listing.stream()
                            .flatMap(c -> Stream.concat(Stream.of(c.category), c.subcategories.stream().map(s -> s.category)))
                            .filter(c -> c.handle.equals(category))
                            .findFirst()
                            .orElse(listing.get(0).category);
                    render();
                });
    }

    @Override
    protected void loadItems() {

        // Create background
        ItemStack backgroundItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ComponentLike backgroundName = Component.empty();
        row(1, backgroundItem, backgroundName);
        row(5, backgroundItem, backgroundName);
        col(0, backgroundItem, backgroundName);
        col(8, backgroundItem, backgroundName);
        col(6, backgroundItem, backgroundName);
        setItem(slot(7, 1), null);
        setItem(slot(7, 0), backgroundItem, backgroundName);

        // Category listing
        for (int i = 0; i < Math.min(5, listing.size()); i++) {
            ListingQuery.Category category = listing.get(i);
            Component name = Component
                    .text(category.category.title)
                    .decorate(TextDecoration.BOLD);
            setItem(slot(i + 1, 0), new ItemStack(Material.CHEST), name);
            if (expandedCategory == i) {
                for (int j = 0; j < Math.min(5, category.subcategories.size()); j++) {
                    Component subName = Component
                            .text(category.subcategories.get(i).category.title)
                            .decorate(TextDecoration.BOLD);
                    setItem(slot(i, j + 1), backgroundItem, backgroundName);
                    setItem(slot(i + 2, j + 1), backgroundItem, backgroundName);
                    setItem(slot(i + 1, j + 1), new ItemStack(Material.CHEST), subName);
                }
            }
        }

        // Product listing
        if (cartOpen) {
            List<CartInfo.Item> products = cart == null ? new ArrayList<>() : cart.items;
            for (int i = 0; i < products.size(); i++) {
                CartInfo.Item item = products.get(i);
                CartInfo.Product product = item.product;
                int slot = constrain(i, 1, 2, 5, 4);
                if (slot == -1) break;
                setItem(slot, new ItemStack(product.id.equals(action) ? Material.YELLOW_WOOL : Material.GOLD_INGOT, Math.max(1, Math.min(64, item.quantity))),
                        Translation.create("ui.listing.product.title",
                                "title", product.title,
                                "price", item.cost.actual,
                                "listPrice", item.cost.list
                        ),
                        Translation.create("ui.listing.product.description",
                                "description", wrap(htmlToPlain(product.description), 80),
                                "quantity", item.quantity
                        )
                );
            }
        } else {
            List<Product> products = current.products.stream()
                    .map(p -> p.product)
                    .collect(Collectors.toList());
            for (int i = 0; i < products.size(); i++) {
                Product product = products.get(i);
                int cartQuantity = cart == null ? 0 : cart.items.stream()
                        .filter(item -> item.product.id.equals(product.id))
                        .mapToInt(item -> item.quantity)
                        .sum();
                int slot = constrain(i, 1, 2, 5, 4);
                if (slot == -1) break;
                setItem(slot,
                        new ItemStack(product.id.equals(action) ? Material.YELLOW_WOOL : Material.GOLD_INGOT, Math.max(1, Math.min(64, cartQuantity))),
                        Translation.create("ui.listing.product.title",
                                "title", product.title,
                                "price", product.price.price,
                                "listPrice", product.price.listPrice
                        ),
                        Translation.create("ui.listing.product.description",
                                "description", wrap(htmlToPlain(product.description), 80),
                                "restricted", product.restricted,
                                "quantity", cartQuantity
                        )
                );
            }
        }

        // Cart display
        setItem(slot(7, 1), new ItemStack(Material.ENDER_CHEST),
                Translation.create("ui.listing.cart.title"),
                Translation.create("ui.listing.cart.description",
                        "quantity", cart == null ? 0 : cart.totalQuantity,
                        "cost", cart == null ? "0.00" : cart.cost.actual,
                        "loading", cart == null
                )
        );

        // Checkout button
        setItem(slot(7, 4), new ItemStack(Material.GOLD_BLOCK),
                Translation.create("ui.listing.checkout.title"),
                Translation.create("ui.listing.checkout.description",
                        "quantity", cart == null ? 0 : cart.totalQuantity,
                        "cost", cart == null ? "0.00" : cart.cost.actual
                )
        );

        // Set title
        rename(Translation.create("ui.listing.title",
                "shop", ListingController.INSTANCE.getShop().title,
                "category", current.title,
                "cartCount", cart == null ? 0 : cart.totalQuantity
        ), false);
    }

    @Override
    protected void loadListeners() {
        // Category selection
        for (int i = 0; i < Math.min(5, listing.size()); i++) {
            ListingQuery.Category category = listing.get(i);
            if (expandedCategory == i) {
                addSlotListener(i + 1, () -> expandedCategory = -1);
                for (int j = 0; j < Math.min(5, category.subcategories.size()); j++) {
                    int k = j;
                    addSlotListener(slot(i + 1, j + 1), () -> current = category.subcategories.get(k).category);
                }
            } else if (category.subcategories.isEmpty()) {
                addSlotListener(i + 1, () -> current = category.category);
            } else {
                int j = i;
                addSlotListener(i + 1, () -> expandedCategory = j);
            }
        }

        addSlotListener(slot(7, 1), () -> cartOpen = !cartOpen);

        // Asynchronous actions
        if (action == null) {
            if (cartOpen) {
                if (cart != null) {
                    for (int i = 0; i < cart.items.size(); i++) {
                        CartInfo.Item item = cart.items.get(i);
                        CartInfo.Product product = item.product;
                        handle(i, product.id, false);
                    }
                }
            } else {
                List<Product> products = current.products.stream()
                        .map(p -> p.product)
                        .collect(Collectors.toList());
                for (int i = 0; i < products.size(); i++) {
                    Product product = products.get(i);
                    handle(i, product.id, product.restricted);
                }
            }


            if (cart != null && cart.totalQuantity > 0) {
                addSlotListener(slot(7, 4), () -> {
                    MinecraftUtil.PLATFORM.sendMessage(player, Translation
                            .create("checkout-url", "url", cart.checkoutURL)
                            .withTransform(new ClickTransform(ClickEvent.openUrl(cart.checkoutURL)))
                    );
                    close();
                });
            }
        }
    }

    private void handle(int i, String product, boolean restricted) {
        int slot = constrain(i, 1, 2, 5, 4);
        if (slot == -1) return;
        addSlotListener(slot, e -> {
            if (e.getClickType() == ClickWindowPacket.InventoryClickType.PICKUP) {
                if (e.getButtonNum() == 0 && !restricted) {
                    // left click
                    action = product;
                    cartFuture.thenAccept(cart -> Storefront.INSTANCE.mutate(new CartAddProductMutation(cart.id, product))
                            .map(d -> d.cartLineAdd.cartInfo)
                            .publishOn(Schedulers.boundedElastic())
                            .doOnError(t -> {
                                if (t instanceof GraphQLError) {
                                    for (Error error : ((GraphQLError) t).getErrors()) {
                                        MinecraftUtil.PLATFORM.sendMessage(player, Component.text(error.getMessage()).color(NamedTextColor.RED));
                                    }
                                }
                                action = null;
                            })
                            .subscribe(next -> {
                                this.cart = next;
                                action = null;
                                render();
                            }));
                } else if (e.getButtonNum() == 1) {
                    // right click
                    action = product;
                    cartFuture.thenAccept(cart -> Storefront.INSTANCE.mutate(new CartRemoveProductMutation(cart.id, product))
                            .map(d -> d.cartLineRemove.cartInfo)
                            .publishOn(Schedulers.boundedElastic())
                            .doOnError(t -> {
                                if (t instanceof GraphQLError) {
                                    for (Error error : ((GraphQLError) t).getErrors()) {
                                        MinecraftUtil.PLATFORM.sendMessage(player, Component.text(error.getMessage()).color(NamedTextColor.RED));
                                    }
                                }
                                action = null;
                            })
                            .subscribe(next -> {
                                this.cart = next;
                                action = null;
                                render();
                            }));
                }
            }
        });
    }

    private void row(int row, ItemStack item, Object... meta) {
        for (int x = 0; x < 9; x++) {
            setItem(slot(x, row), item, meta);
        }
    }

    private void col(int col, ItemStack item, Object... meta) {
        for (int y = 0; y < getSize() / 9; y++) {
            setItem(slot(col, y), item, meta);
        }
    }

    private int constrain(int i, int left, int top, int right, int bottom) {
        int y = i / (right - left + 1) + top;
        int x = i % (right - left + 1) + left;
        if (y > bottom || y == bottom && x >= right) return -1;
        return slot(x, y);
    }

    private String htmlToPlain(String html) {
        return html
                .replace("&gt;", "\n")
                .replace("<br>", "\n")
                .replaceAll("<[^>]+>", "");
    }

    private String wrap(String text, int width) {
        List<String> lines = new LinkedList<>();
        while (text.length() > width) {
            int next = text.indexOf('\n');
            if (next == -1) next = text.length();
            if (next > width) {
                for (int i = width - 1; i >= 0; i--) {
                    if (text.charAt(i) == ' ') {
                        next = i;
                        break;
                    }
                }
                if (next > width) {
                    for (int i = width; i < text.length(); i++) {
                        if (text.charAt(i) == ' ') {
                            next = i;
                            break;
                        }
                    }
                }
            }
            lines.add(text.substring(0, next));
            text = text.substring(next + 1);
        }
        lines.add(text);
        return String.join("\n", lines);
    }
}
