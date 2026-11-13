package dev.shirokai.daily.fabric;
import dev.shirokai.daily.core.Config;
import dev.shirokai.daily.core.integration.ImpactorEconomy;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.*;
final class ImpactorAdapter implements RewardAdapter {
    public boolean supports(String type){return type.equals("DOLLARS")||type.equals("TOKENS");}
    public CompletionStage<String> deliver(ServerPlayerEntity player,Config.Reward reward,String transactionId){
        String currency=reward.type().equals("DOLLARS")?"impactor:dollars":"impactor:tokens";
        try{return ImpactorEconomy.fromRuntime(player.getServer()::execute).deposit(player.getUuid(),currency,reward.amount());}
        catch(ReflectiveOperationException|LinkageError unavailable){return CompletableFuture.completedFuture("PENDING");}
    }
}
