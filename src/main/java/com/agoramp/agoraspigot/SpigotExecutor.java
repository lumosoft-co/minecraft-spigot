package com.agoramp.agoraspigot;

import com.agoramp.FulfillmentExecutor;
import com.agoramp.data.models.common.OnlineStatus;
import com.agoramp.data.models.fulfillments.GameServerCommandsFulfillment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class SpigotExecutor implements FulfillmentExecutor {

    @Override
    public Flux<String> retrieveOnlinePlayerIds() {
        return Flux.fromStream(() -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .map(Objects::toString)
        );
    }

    @Override
    public Mono<Boolean> processCommandFulfillment(GameServerCommandsFulfillment fulfillment) {
        return Mono.fromSupplier(() -> {
            boolean needsOnline = fulfillment.getCommands().stream()
                    .anyMatch(c -> c.getRequiredStatus() == OnlineStatus.ONLINE);
            int requiredSlots = fulfillment.getCommands().stream()
                    .mapToInt(c -> c.getRequiredSlots() == null ? 0 : c.getRequiredSlots())
                    .sum();
            Player player = Optional.ofNullable(fulfillment.getTarget())
                    .map(UUID::fromString)
                    .map(Bukkit::getPlayer)
                    .orElse(null);
            if ((needsOnline || requiredSlots > 0) && player == null) {
                return false;
            }
            if (requiredSlots > 0) {
                int free = 0;
                for (int i = 0; i < 36; i++) {
                    if (player.getInventory().getItem(i) == null && ++free >= requiredSlots) break;
                }
                if (free < requiredSlots) return false;
            }
            boolean result = false;
            for (GameServerCommandsFulfillment.Command command : fulfillment.getCommands()) {
                try {
                    result |= Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.getCommand());
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            // If any of the commands were able to run we mark the entire fulfillment as a success
            return result;
        });
    }
}
