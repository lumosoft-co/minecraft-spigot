package com.agoramp.minecraft;

import com.agoramp.AgoraFulfillmentService;
import com.agoramp.error.ServiceAlreadyInitializedException;
import com.agoramp.minecraft.commands.BuyCommand;
import com.agoramp.minecraft.commands.SecretCommand;
import com.agoramp.minecraft.controller.ListingController;
import com.agoramp.minecraft.util.MinecraftUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class AgoraSpigot extends JavaPlugin {

    public static AgoraSpigot INSTANCE;

    @Override
    public void onEnable() {
        INSTANCE = this;
        Bukkit.getPluginCommand("agora").setExecutor(new SecretCommand());
        File config = new File(getDataFolder(), "config.json");
        try {
            boolean secretIsValid = AgoraFulfillmentService.INSTANCE.initializeFromFile(config, new SpigotExecutor());
            if (!secretIsValid) {
                INSTANCE.getLogger().info("Please enter your Agora destination secret using /agora secret <secret> or by adding it to " + config);
            }
        } catch (IOException | ServiceAlreadyInitializedException e) {
            e.printStackTrace();
        }

        SpigotPlatform.INSTANCE.load();
        ListingController.INSTANCE.load();
    }

    public void reload() {
        AgoraFulfillmentService.INSTANCE.shutdown();
        File config = new File(getDataFolder(), "config.json");
        try {
            AgoraFulfillmentService.INSTANCE.initializeFromFile(config, new SpigotExecutor());
        } catch (IOException | ServiceAlreadyInitializedException e) {
            throw new RuntimeException(e);
        } catch (Throwable t) {
            System.out.println("Please enter your Agora destination secret in the config located at " + config);
            return;
        }
    }

}
