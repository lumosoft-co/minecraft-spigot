package com.agoramp.minecraft.commands;

import com.agoramp.AgoraFulfillmentService;
import com.agoramp.data.FulfillmentDestinationConfig;
import com.agoramp.error.ServiceAlreadyInitializedException;
import com.agoramp.minecraft.AgoraSpigot;
import com.agoramp.minecraft.SpigotExecutor;
import com.agoramp.minecraft.ui.ListingUI;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class SecretCommand implements CommandExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) return false;
        if (args[0].equalsIgnoreCase("secret") && args.length == 2) {
            if (sender instanceof Player) {
                sender.sendMessage("Please use this command from the console");
                return true;
            }
            File config = new File(AgoraSpigot.INSTANCE.getDataFolder(), "config.json");
            try {
                FulfillmentDestinationConfig data = new Gson().fromJson(new FileReader(config), FulfillmentDestinationConfig.class);
                data.setSecret(args[1]);
                FileWriter writer = new FileWriter(config);
                writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(data));
                writer.flush();
                writer.close();
                sender.sendMessage("Config updated");
                AgoraFulfillmentService.INSTANCE.initialize(data, new SpigotExecutor());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ServiceAlreadyInitializedException e) {
                sender.sendMessage("Please reboot your server to update your secret");
            }
            return true;
        }
        return false;
    }
}
