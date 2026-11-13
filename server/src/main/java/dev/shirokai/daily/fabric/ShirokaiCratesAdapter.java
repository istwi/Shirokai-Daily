package dev.shirokai.daily.fabric;
import dev.shirokai.daily.core.Config;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.concurrent.*;
/** Requires the explicit public API extension in integrations/shirokai-crates-grant-fragments.patch. */
final class ShirokaiCratesAdapter implements RewardAdapter {
    public boolean supports(String type){return type.equals("CRATE_FRAGMENT");}
    @SuppressWarnings("unchecked") public CompletionStage<String> deliver(ServerPlayerEntity player,Config.Reward reward,String transactionId){
        Object service;java.lang.reflect.Method grant;
        try {
            service=Class.forName("dev.shirokaicrates.ShirokaiCrates").getMethod("service").invoke(null);
            if(service==null)return CompletableFuture.completedFuture("PENDING");
            grant=Class.forName("dev.shirokaicrates.service.CrateService").getMethod("grantFragments",ServerPlayerEntity.class,String.class,int.class);
        }catch(ReflectiveOperationException|LinkageError missing){return CompletableFuture.completedFuture("PENDING");}
        try {
            // Only Crates creates/signs the item. Daily never reads or recreates its signing material.
            var result=(CompletionStage<Boolean>)grant.invoke(service,player,reward.value(),reward.amount());
            return result.handle((success,error)->error!=null?"UNCERTAIN":Boolean.TRUE.equals(success)?"DONE":"PENDING");
        }catch(ReflectiveOperationException|LinkageError failure){return CompletableFuture.completedFuture("UNCERTAIN");}
    }
}
