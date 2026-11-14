package dev.shirokai.daily.fabric;
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor;
import dev.shirokai.daily.core.integration.PublicRaidPredicates;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import java.util.Set;
/** Optional, read-only adapter to the existing PUBLIC Shirokai Raids API (0.1.13/0.1.20). */
final class ShirokaiRaidsAdapter {
 private final boolean installed=FabricLoader.getInstance().isModLoaded("shirokai_raids");
 private PublicRaidPredicates<BattleActor> api;
 boolean raidActor(BattleActor actor){
  if(!installed)return false;
  if(api==null)try{
   api=new PublicRaidPredicates<>(BattleActor.class,Class.forName("com.shirokai.raids.TraditionalRaidBattles"),Class.forName("com.shirokai.raids.ShirokaiRaids"));
  }catch(ReflectiveOperationException e){throw new IllegalStateException("Shirokai Raids public classification API unavailable; skipping this event",e);}
  return api.test(actor);
 }
 // Public entity-tag contract in Shirokai Raids / RaidCapture. Also survives actor/session cleanup.
 private static final Set<String> TAGS=Set.of("shirokai_raids_boss","shirokai_raid_boss","shirokai_raid_capture","shirokai_raid_throw");
 static boolean marked(Entity entity){return entity!=null&&entity.getCommandTags().stream().anyMatch(TAGS::contains);}
}
