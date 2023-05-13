package com.agoramp.minecraft;

import com.agoramp.minecraft.util.MinecraftUtil;
import com.agoramp.minecraft.util.Platform;
import com.agoramp.minecraft.util.api.text.MiniMessageUtil;
import com.agoramp.minecraft.util.api.text.Translatable;
import com.agoramp.minecraft.util.controller.TranslationController;
import com.agoramp.minecraft.util.data.packets.models.ModelledPacket;
import com.agoramp.minecraft.util.data.packets.models.packets.CloseWindowPacket;
import com.agoramp.minecraft.util.data.packets.models.packets.WindowItemsPacket;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public enum SpigotPlatform implements Platform {
    INSTANCE;

    private ProtocolManager manager;
    private final Map<UUID, Integer> windowCounts = new WeakHashMap<>();
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer
            .builder()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    public void load() {
        manager = ProtocolLibrary.getProtocolManager();
        MinecraftUtil.initialize(this);
    }

    @Override
    public void schedule(Runnable runnable, int i) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(AgoraSpigot.INSTANCE, runnable, i);
    }

    @Override
    public int openInventory(UUID uuid, int size, ComponentLike componentLike) {
        if (size % 9 != 0) throw new Error("Invalid size");
        Player player = Bukkit.getPlayer(uuid);
        int windowId = windowCounts.compute(uuid, (k,v) -> v == null ? 65000 : v + 1);
        PacketContainer container = new PacketContainer(PacketType.Play.Server.OPEN_WINDOW);
        container.getIntegers()
                .write(0, windowId)
                .write(1, size / 9 + 1);
        container.getChatComponents()
                        .write(2, serialize(uuid, componentLike));
        manager.sendServerPacket(player, container);
        return windowId;
    }

    @Override
    public void sendPacket(UUID uuid, ModelledPacket modelledPacket) {
        PacketContainer container;
        if (modelledPacket instanceof CloseWindowPacket) {
            container = new PacketContainer(PacketType.Play.Server.CLOSE_WINDOW);
            container.getIntegers()
                    .write(0, ((CloseWindowPacket) modelledPacket).getWindowId());
        } else if (modelledPacket instanceof WindowItemsPacket) {
            WindowItemsPacket<ItemStack> items = (WindowItemsPacket<ItemStack>) modelledPacket;
            container = new PacketContainer(PacketType.Play.Server.WINDOW_ITEMS);
            container.getIntegers()
                    .write(0, items.getWindowId())
                    .write(1, items.getItems().length);
            for (int i = 0; i < items.getItems().length; i++) {
                Object[] metadata = items.getMetadata()[i];
                if (metadata == null) metadata = new Object[0];
                ItemStack stack = items.getItems()[i];
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (metadata.length >= 1 && metadata[0] != null) {
                        ComponentLike name = (ComponentLike) metadata[0];
                        meta.setDisplayName(serializer.serialize(parse(uuid, name)));
                    }
                    if (metadata.length >= 2 && metadata[1] != null) {
                        ComponentLike lore = (ComponentLike) metadata[1];
                        meta.setLore(MiniMessageUtil
                                .split(parse(uuid, lore), '\n')
                                .stream()
                                .map(serializer::serialize)
                                .collect(Collectors.toList()));
                    }
                    stack.setItemMeta(meta);
                }
            }
            container.getItemArrayModifier().write(2, items.getItems());
        } else {
            throw new Error("Unhandled packet type " + modelledPacket.getClass().getSimpleName());
        }
        manager.sendServerPacket(Bukkit.getPlayer(uuid), container);
    }

    @Override
    public Locale getLocale(UUID uuid) {
        return Locale.forLanguageTag(Bukkit.getPlayer(uuid).getLocale());
    }

    @Override
    public void sendMessage(UUID uuid, ComponentLike componentLike) {
        PacketContainer container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
        container.getChatComponents()
                .write(0, serialize(uuid, componentLike));
        manager.sendServerPacket(Bukkit.getPlayer(uuid), container);
    }

    private Component parse(UUID id, ComponentLike component) {
        if (component instanceof Translatable) {
            component = ((Translatable) component).translate(getLocale(id));
        }
        return component.asComponent();
    }

    private WrappedChatComponent serialize(UUID id, ComponentLike component) {
        return WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(parse(id, component)));
    }
}
