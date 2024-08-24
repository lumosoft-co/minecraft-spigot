package com.agoramp.minecraft.controller;

import com.agoramp.controller.Storefront;
import com.agoramp.minecraft.commands.BuyCommand;
import com.agoramp.minecraft.models.graphql.CartCreateMutation;
import com.agoramp.minecraft.models.graphql.ListingQuery;
import com.agoramp.minecraft.models.graphql.ShopQuery;
import com.agoramp.minecraft.models.graphql.fragment.CartInfo;
import com.apollographql.apollo3.api.Optional;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import com.agoramp.reactive.core.publisher.Mono;
import com.agoramp.reactive.util.function.Tuple2;

import java.util.List;

@Getter
public enum ListingController {
    INSTANCE;

    private ShopQuery.Shop shop;
    private List<ListingQuery.Category> listing;

    public void load() {
        Tuple2<ShopQuery.Shop, List<ListingQuery.Category>> data = Mono.zip(
                Storefront.INSTANCE
                        .query(new ShopQuery())
                        .map(d -> d.shop),
                Storefront.INSTANCE
                        .query(new ListingQuery(Optional.absent()))
                        .map(d -> d.categories)
        ).block();
        shop = data.getT1();
        listing = data.getT2();
        System.out.println("Loaded storefront for " + shop.title);
        PluginCommand command = Bukkit.getPluginCommand("buy");
        if (command != null) command.setExecutor(new BuyCommand());
        else System.out.println("Failed to register buy command, check if another plugin is attempting to register it.");
    }

    public Mono<Tuple2<CartInfo, List<ListingQuery.Category>>> getListing(Player player) {
        return Storefront.INSTANCE
                .mutate(new CartCreateMutation(player.getName(), player.getUniqueId().toString().replace("-", "")))
                .map(d -> d.cartCreate.cartInfo)
                .zipWhen(c -> Storefront.INSTANCE
                        .query(new ListingQuery(Optional.present(c.id)))
                        .map(d -> d.categories));
    }
}
