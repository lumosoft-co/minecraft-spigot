package com.agoramp.minecraft;

import com.agoramp.minecraft.commands.BuyCommand;
import com.agoramp.minecraft.util.MinecraftUtil;
import com.agoramp.minecraft.util.Platform;
import com.agoramp.minecraft.util.api.text.MiniMessageUtil;
import com.agoramp.minecraft.util.api.text.Translatable;
import com.agoramp.minecraft.util.data.packets.models.*;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.InternalStructure;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.agoramp.kyori.adventure.text.Component;
import com.agoramp.kyori.adventure.text.ComponentLike;
import com.agoramp.kyori.adventure.text.format.NamedTextColor;
import com.agoramp.kyori.adventure.text.format.TextDecoration;
import com.agoramp.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import com.agoramp.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
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
        manager.addPacketListener(new PacketAdapter(AgoraSpigot.INSTANCE, PacketType.Play.Client.WINDOW_CLICK, PacketType.Play.Client.CLOSE_WINDOW) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                try {
                    PacketContainer packet = event.getPacket();
                    ModelledPacket translated;
                    if (packet.getType() == PacketType.Play.Client.WINDOW_CLICK) {
                        int button;
                        ClickWindowPacket.InventoryClickType type;
                        if (MinecraftVersion.COMBAT_UPDATE.atOrAbove()) {
                            type = packet.getEnumModifier(ClickWindowPacket.InventoryClickType.class, MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.CAVES_CLIFFS_2) ? 4 : 5).read(0);
                            button = MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.CAVES_CLIFFS_2) ?
                                    packet.getIntegers().read(3) :
                                    packet.getShorts().read(0);
                        } else {
                            type = ClickWindowPacket.InventoryClickType.values()[packet.getIntegers().read(3)];
                            button = packet.getIntegers().read(2);
                        }
                        translated = new ClickWindowPacket(
                                packet.getIntegers().read(0),
                                packet.getIntegers().read(MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.CAVES_CLIFFS_2) ? 2 : 1),
                                button,
                                packet.getItemModifier().read(0),
                                type
                        );
                    } else if (packet.getType() == PacketType.Play.Client.CLOSE_WINDOW) {
                        translated = new CloseWindowPacket(packet.getIntegers().read(0));
                    } else {
                        return;
                    }
                    if (handlePacket(event.getPlayer().getUniqueId(), translated)) {
                        event.setCancelled(true);
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        });
    }

    @Override
    public void schedule(Runnable runnable, int i) {
        Bukkit.getScheduler().scheduleSyncDelayedTask(AgoraSpigot.INSTANCE, () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, i);
    }

    @Override
    public int openInventory(UUID uuid, int size, ComponentLike componentLike) {
        if (size % 9 != 0) throw new Error("Invalid size");
        Player player = Bukkit.getPlayer(uuid);
        PacketContainer container = new PacketContainer(PacketType.Play.Server.OPEN_WINDOW);
        container.getIntegers().write(0, 69);
        if (!MinecraftVersion.VILLAGE_UPDATE.atOrAbove()) {
            container.getStrings().write(0, "minecraft:generic_9x" + (size / 9));
            container.getIntegers().write(1, size);
        } else if (!MinecraftVersion.WILD_UPDATE.atOrAbove()) {
            container.getIntegers().write(1, size / 9 - 1);
        } else {
            Field field = container.getHandle().getClass().getDeclaredFields()[1];
            Field menuType = field.getType().getDeclaredFields()[(size / 9 - 1)];
            try {
                container.getStructures()
                        .write(0, InternalStructure.getConverter().getSpecific(menuType.get(null)));
            } catch (Throwable t) {
                throw new Error("Unsupported version of Minecraft");
            }
        }
        container.getChatComponents()
                        .write(0, serialize(uuid, componentLike));
        manager.sendServerPacket(player, container);
        return 69;
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
            container.getIntegers().write(0, items.getWindowId());
            if (MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.CAVES_CLIFFS_2)) {
                container.getIntegers()
                        .write(1, 1); // state id
                container.getItemModifier().write(0, new ItemStack(Material.AIR)); // cursor item
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (int i = 0; i < items.getItems().length; i++) {
                Object[] metadata = items.getMetadata()[i];
                if (metadata == null) metadata = new Object[0];
                ItemStack stack = items.getItems()[i];
                if (stack == null) stack = new ItemStack(Material.AIR);
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    if (metadata.length >= 1 && metadata[0] != null) {
                        ComponentLike name = (ComponentLike) metadata[0];
                        meta.setDisplayName(serializer.serialize(parse(uuid, name).colorIfAbsent(NamedTextColor.WHITE)));
                    }
                    if (metadata.length >= 2 && metadata[1] != null) {
                        ComponentLike lore = (ComponentLike) metadata[1];
                        meta.setLore(MiniMessageUtil
                                .split(parse(uuid, lore).colorIfAbsent(NamedTextColor.WHITE), '\n')
                                .stream()
                                .map(serializer::serialize)
                                .collect(Collectors.toList()));
                    }
                    stack.setItemMeta(meta);
                }
                stacks.add(stack);
            }
            if (MinecraftVersion.EXPLORATION_UPDATE.atOrAbove())
                container.getItemListModifier().write(0, stacks);
            else
                container.getItemArrayModifier().write(0, stacks.toArray(new ItemStack[0]));
        } else if (modelledPacket instanceof SetSlotPacket) {
           SetSlotPacket<ItemStack> packet =  (SetSlotPacket<ItemStack>) modelledPacket;
           container = new PacketContainer(PacketType.Play.Server.SET_SLOT);
           container.getIntegers().write(0, packet.getWindowId());
           if (MinecraftVersion.getCurrentVersion().isAtLeast(MinecraftVersion.CAVES_CLIFFS_2)) {
               container.getIntegers()
                       .write(1, 1) // state id
                       .write(2, packet.getSlot());
           } else {
               container.getIntegers().write(1, packet.getSlot());
           }
           container.getItemModifier()
                   .write(0, packet.getItem() == null ? new ItemStack(Material.AIR) : packet.getItem());
        } else {
            throw new Error("Unhandled packet type " + modelledPacket.getClass().getSimpleName());
        }
        manager.sendServerPacket(Bukkit.getPlayer(uuid), container);
    }

    @Override
    public Locale getLocale(UUID uuid) {
        try {
            return Locale.forLanguageTag(Bukkit.getPlayer(uuid).getLocale());
        } catch (Throwable t) {
            return Locale.ENGLISH;
        }
    }

    @Override
    public void sendMessage(UUID uuid, ComponentLike componentLike) {
        try {
            PacketContainer container;
            if (MinecraftVersion.WILD_UPDATE.atOrAbove()) {
                container = new PacketContainer(PacketType.Play.Server.SYSTEM_CHAT);
                container.getChatComponents()
                        .write(0, serialize(uuid, componentLike));
                container.getBooleans()
                        .write(0, false);
            } else {
                container = new PacketContainer(PacketType.Play.Server.CHAT);
                container.getChatComponents()
                        .write(0, serialize(uuid, componentLike));
                container.getChatTypes()
                        .write(0, EnumWrappers.ChatType.SYSTEM);
            }
            manager.sendServerPacket(Bukkit.getPlayer(uuid), container);
        } catch (Throwable t) {
            // unsupported in this version
            Bukkit.getPlayer(uuid).sendMessage(LegacyComponentSerializer.legacySection().serialize(componentLike.asComponent()));
        }
    }

    private Component parse(UUID id, ComponentLike component) {
        if (component instanceof Translatable) {
            component = ((Translatable) component).translate(getLocale(id));
        }
        Component out = component.asComponent();
        if (out.decoration(TextDecoration.ITALIC) == TextDecoration.State.NOT_SET) {
            out = out.decoration(TextDecoration.ITALIC, false);
        }
        return out;
    }

    private WrappedChatComponent serialize(UUID id, ComponentLike component) {
        return WrappedChatComponent.fromJson(GsonComponentSerializer.gson().serialize(parse(id, component)));
    }
}
