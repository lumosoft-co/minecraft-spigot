package com.agoramp.minecraft.commands;

import com.agoramp.minecraft.AgoraSpigot;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ReloadCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("agora.admin.reload")) return false;
        AgoraSpigot.INSTANCE.reload();
        return true;
    }
}
