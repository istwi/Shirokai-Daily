package dev.shirokai.daily.fabric;
import dev.shirokai.daily.core.Config;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.CompletionStage;
/** Provider starts on the game thread; async completion may occur elsewhere. Never block the game thread. */
public interface RewardAdapter {
    boolean supports(String type);
    /** DONE after durable acknowledgement; PENDING only before effects; UNCERTAIN otherwise. */
    CompletionStage<String> deliver(ServerPlayerEntity player,Config.Reward reward,String transactionId);
}
