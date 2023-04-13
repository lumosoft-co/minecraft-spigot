package com.agoramp.agoraspigot;

import com.agoramp.AgoraFulfillmentService;
import com.agoramp.error.ServiceAlreadyInitializedException;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class AgoraSpigot extends JavaPlugin {

    @Override
    public void onEnable() {
        File config = new File(getDataFolder(), "config.json");
        try {
            AgoraFulfillmentService.INSTANCE.initializeFromFile(config, new SpigotExecutor());
        } catch (IOException | ServiceAlreadyInitializedException e) {
            throw new RuntimeException(e);
        } catch (Throwable t) {
            System.out.println("Please enter your Agora destination secret in the config located at " + config);
        }
    }
}
