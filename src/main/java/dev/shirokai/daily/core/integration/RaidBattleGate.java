package dev.shirokai.daily.core.integration;
import java.util.*;
import java.util.function.*;
/** Classifies ALL actors before any credit; remembers Raid and unreadable battles across callback order. */
public final class RaidBattleGate<A> {
 public record Evidence(boolean raid,Set<UUID> pokemon){public Evidence{pokemon=Set.copyOf(pokemon);}}
 private final Set<UUID> blocked=new HashSet<>();
 private final BiConsumer<UUID,Set<UUID>> quarantine;
 public RaidBattleGate(BiConsumer<UUID,Set<UUID>> quarantine){this.quarantine=quarantine;}
 // A legitimately captured Raid Pokémon in a PLAYER team is not itself a Raid battle.
 public static boolean sourceIdentity(boolean unowned,boolean alreadyExcluded,boolean marked){return unowned||alreadyExcluded||marked;}
 public static boolean knownRaidTarget(String actorType,boolean unowned,boolean known){return actorType.equals("WILD")&&unowned&&known;}
 public void block(UUID battle,Set<UUID> pokemon){if(blocked.add(battle))quarantine.accept(battle,Set.copyOf(pokemon));}
 public boolean allow(UUID battle,Iterable<A> actors,Function<A,Evidence> inspect){return allow(battle,actors,inspect,ignored->Set.of());}
 public boolean allow(UUID battle,Iterable<A> actors,Function<A,Evidence> inspect,Function<A,Set<UUID>> unownedTargets){
  if(blocked.contains(battle))return false;
  List<A> snapshot=new ArrayList<>();actors.forEach(snapshot::add);
  Set<UUID> targets=new HashSet<>(),uncertainTargets=new HashSet<>();boolean raid=false;
  try{
   // Snapshot public target identities before invoking optional integrations. An unreadable Raid
   // cannot subsequently leak through a capture callback lacking a battle ID.
   for(A actor:snapshot)uncertainTargets.addAll(unownedTargets.apply(actor));
   for(A actor:snapshot){Evidence evidence=inspect.apply(actor);if(evidence.raid()){raid=true;targets.addAll(evidence.pokemon());}}
  }catch(RuntimeException|Error error){block(battle,uncertainTargets);throw error;}
  if(raid){block(battle,targets);return false;}return true;
 }
}
