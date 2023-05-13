package com.agoramp.minecraft.ui;

import com.agoramp.minecraft.controller.ListingController;
import com.agoramp.minecraft.util.api.text.Translation;
import com.agoramp.minecraft.util.api.ui.UserInterface;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ListingUI extends UserInterface<ItemStack> {

    public ListingUI() {
        super(54, Translation.create("ui.listing.title",
                "shop", ListingController.INSTANCE.getShop().title,
                "category", ListingController.INSTANCE.getListing().get(0).title
        ));
    }

    @Override
    protected void loadItems() {
        setItem(0, new ItemStack(Material.BLACK_WOOL), Translation.create("test"));
    }
}
