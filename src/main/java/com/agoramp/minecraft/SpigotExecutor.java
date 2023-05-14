package com.agoramp.minecraft;

import com.agoramp.FulfillmentExecutor;
import com.agoramp.data.models.common.OnlineStatus;
import com.agoramp.data.models.fulfillments.GameServerCommandsFulfillment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import com.agoramp.reactive.core.publisher.Flux;
import com.agoramp.reactive.core.publisher.Mono;

import java.math.BigInteger;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SpigotExecutor implements FulfillmentExecutor {

    @Override
    public Flux<String> retrieveOnlinePlayerIds() {
        return Flux.fromStream(() -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .map(id -> id.toString().replace("-", ""))
        );
    }

    @Override
    public Mono<Boolean> processCommandFulfillment(GameServerCommandsFulfillment fulfillment) {
        return Mono.fromFuture(() -> {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(AgoraSpigot.INSTANCE, () -> {
                try {
                    future.complete(process(fulfillment));
                } catch (Throwable t) {
                    t.printStackTrace();
                    future.completeExceptionally(t);
                }
            });
            return future;
        });
    }

    private boolean process(GameServerCommandsFulfillment fulfillment) {

        boolean needsOnline = fulfillment.getRequiredStatus() == OnlineStatus.ONLINE;
        int requiredSlots = fulfillment.getRequiredSlots();

        Player player = Optional.ofNullable(fulfillment.getTarget())
                .map(str -> {
                    // Stored ID does not contain dashes
                    BigInteger bi1 = new BigInteger(str.substring(0, 16), 16);
                    BigInteger bi2 = new BigInteger(str.substring(16, 32), 16);
                    return new UUID(bi1.longValue(), bi2.longValue());
                })
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
        for (String command : fulfillment.getCommands()) {
            try {
                System.out.println("Executing command for " + (player != null ? player.getName() : fulfillment.getTarget()) + ": " + command);
                result |= Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        // If any of the commands were able to run we mark the entire fulfillment as a success
        return result;
    }
}
