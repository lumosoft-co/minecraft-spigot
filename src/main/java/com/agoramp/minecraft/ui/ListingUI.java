package com.agoramp.minecraft.ui;

import com.agoramp.minecraft.controller.ListingController;
import com.agoramp.minecraft.models.graphql.ListingQuery;
import com.agoramp.minecraft.models.graphql.fragment.CartInfo;
import com.agoramp.minecraft.models.graphql.fragment.Product;
import com.agoramp.minecraft.util.api.text.Translation;
import com.agoramp.minecraft.util.api.ui.UserInterface;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

public class ListingUI extends UserInterface<ItemStack> {
    private int expandedCategory = -1;
    private ListingQuery.Category current;
    private List<ListingQuery.Category> listing;
    private List<Product> products;
    private CartInfo cart;

    public ListingUI() {
        super(54, Translation.create("ui.listing.title",
                "shop", ListingController.INSTANCE.getShop().title,
                "category", ListingController.INSTANCE.getListing().get(0).title
        ));
        listing = ListingController.INSTANCE.getListing();
        products = listing.get(0).products.stream()
                .map(p -> p.product)
                .collect(Collectors.toList());
    }

    @Override
    public void open(UUID id, boolean render) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        super.open(id, render);
        ListingController.INSTANCE
                .getListing(player)
                .subscribe(t -> {
                    listing = t.getT2();
                    cart = t.getT1();
                });
    }

    @Override
    protected void loadItems() {
        // Set title
        rename(Translation.create("ui.listing.title",
                "shop", ListingController.INSTANCE.getShop().title,
                "category", ListingController.INSTANCE.getListing().get(0).title
        ), false);

        // Create background
        ItemStack backgroundItem = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
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
                    .text(category.title)
                    .decorate(TextDecoration.BOLD);
            setItem(slot(i + 1, 0), new ItemStack(Material.CHEST), name);
            if (expandedCategory == i) {
                for (int j = 0; j < Math.min(5, category.subcategories.size()); j++) {
                    Component subName = Component
                            .text(category.subcategories.get(i).title)
                            .decorate(TextDecoration.BOLD);
                    setItem(slot(i, j + 1), backgroundItem, backgroundName);
                    setItem(slot(i + 2, j + 1), backgroundItem, backgroundName);
                    setItem(slot(i + 1, j + 1), new ItemStack(Material.CHEST), subName);
                }
            }
        }

        // Product listing
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            Component name = Component
                    .text(product.title)
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text(" - "))
                    .append(Component.text(product.price.price.toString())
                            .color(NamedTextColor.GREEN)
                            .append(Objects.equals(product.price.price, product.price.listPrice) ?
                                    Component.empty() :
                                    Component.text(product.price.listPrice.toString()).decorate(TextDecoration.STRIKETHROUGH))
                    );

        }
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

    @Override
    protected void loadListeners() {
        // Category selection
        for (int i = 0; i < Math.min(5, listing.size()); i++) {
            ListingQuery.Category category = listing.get(i);
            if (expandedCategory == i) {
                addSlotListener(i + 1, () -> expandedCategory = -1);
                for (int j = 0; j < Math.min(5, category.subcategories.size()); j++) {
                    int k = j;
                    addSlotListener(slot(i + 1, j + 1), () -> products = category.subcategories.get(k).products.stream()
                            .map(p -> p.product)
                            .collect(Collectors.toList()));
                }
            } else if (category.subcategories.isEmpty()) {
                addSlotListener(i + 1, () -> products = category.products.stream()
                        .map(p -> p.product)
                        .collect(Collectors.toList()));
            } else {
                int j = i;
                addSlotListener(i + 1, () -> expandedCategory = j);
            }
        }
    }
}
