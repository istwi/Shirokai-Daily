package dev.shirokai.daily.fabric;

import dev.shirokai.daily.core.Config;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;
import java.nio.file.Files;
import java.util.Map;

/** Uses only Crates' public snapshot and signed token factory (audited against 0.2.1).
 * No console/player impersonation, private fields, fabricated NBT or copied signing bytes.
 */
final class CrateKeys {
    static String deliver(ServerPlayerEntity player, Config.Reward reward) {
        boolean mutated=false;
        try {
            if(player.isDisconnected())return "PENDING";
            Object service=Class.forName("dev.shirokaicrates.ShirokaiCrates").getMethod("service").invoke(null);
            if(service==null||!(boolean)service.getClass().getMethod("healthy").invoke(service))return "PENDING";
            Object snapshot=service.getClass().getMethod("snapshot").invoke(service);
            Object crate=((Map<?,?>)snapshot.getClass().getMethod("crates").invoke(snapshot)).get(reward.value());
            if(crate==null)return "PENDING";
            // Refuse to initialize or replace Crates' own world secret. Only its factory opens it.
            var secret=player.getServer().getSavePath(WorldSavePath.ROOT).resolve("shirokaicrates/token-secret.bin");
            if(!Files.isRegularFile(secret)||Files.isSymbolicLink(secret))return "PENDING";
            Class<?> factory=Class.forName("dev.shirokaicrates.service.Tokens");
            Class<?> model=Class.forName("dev.shirokaicrates.config.Models$Crate");
            Object tokens=factory.getConstructor(java.nio.file.Path.class).newInstance(secret);
            ItemStack sample=(ItemStack)factory.getMethod("create",model,boolean.class,int.class).invoke(tokens,crate,false,1);
            if(sample.isEmpty()||!(boolean)factory.getMethod("matches",ItemStack.class,model,boolean.class).invoke(tokens,sample,crate,false))return "PENDING";
            int capacity=0;
            for(int i=0;i<36;i++) {
                ItemStack stack=player.getInventory().getStack(i);
                if(stack.isEmpty())capacity+=sample.getMaxCount();
                else if(ItemStack.areItemsAndComponentsEqual(stack,sample))capacity+=Math.max(0,stack.getMaxCount()-stack.getCount());
            }
            if(capacity<reward.amount())return "PENDING";
            for(int remaining=reward.amount();remaining>0;) {
                int count=Math.min(remaining,sample.getMaxCount());ItemStack stack=sample.copyWithCount(count);
                mutated=true;
                if(!player.getInventory().insertStack(stack)||!stack.isEmpty())return "UNCERTAIN";
                remaining-=count;
            }
            player.getInventory().markDirty();
            return "DONE";
        } catch(ReflectiveOperationException|LinkageError|RuntimeException error) {
            if(mutated)ShirokaiDaily.LOG.error("Entrega de chave incerta para {}",player.getUuid(),error);
            return mutated?"UNCERTAIN":"PENDING";
        }
    }
}
