package com.agoramp.minecraft.commands;

import com.agoramp.minecraft.ui.ListingUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BuyCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (sender instanceof Player) {
            new ListingUI().open(((Player) sender).getUniqueId(), true);
            return true;
        }
        return false;
    }
}
