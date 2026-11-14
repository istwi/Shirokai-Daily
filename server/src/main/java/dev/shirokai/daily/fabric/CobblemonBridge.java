package dev.shirokai.daily.fabric;
import com.cobblemon.mod.common.api.Priority;
import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.battles.model.actor.*;
import com.cobblemon.mod.common.api.events.CobblemonEvents;
import com.cobblemon.mod.common.api.events.battles.BattleVictoryEvent;
import com.cobblemon.mod.common.api.events.pokemon.PokemonCapturedEvent;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.reactive.Observable;
import com.cobblemon.mod.common.api.reactive.ObservableSubscription;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.*;
import dev.shirokai.daily.core.*;
import dev.shirokai.daily.core.integration.EventIsolation;
import dev.shirokai.daily.core.integration.RaidBattleGate;
import net.minecraft.item.*;
import java.util.*;
import java.util.function.Consumer;

/** Typed Cobblemon PUBLIC API; private actor implementations are never reflected on. */
final class CobblemonBridge implements AutoCloseable {
 private final ShirokaiDaily mod;
 private final ShirokaiRaidsAdapter raids=new ShirokaiRaidsAdapter();
 private final EventIsolation events=new EventIsolation(System::nanoTime,(message,error)->ShirokaiDaily.LOG.warn(message,error));
 private final List<ObservableSubscription<?>> subscriptions=new ArrayList<>();
 private final Map<String,Species> registered=new HashMap<>();
 private final Map<String,ItemStack> models=new HashMap<>();
 private final Set<String> types=new HashSet<>();
 private final Set<UUID> excludedPokemon=new HashSet<>();
 private final RaidBattleGate<BattleActor> battleGate=new RaidBattleGate<>(this::exclude);
 private String status="não iniciado";private boolean healthy;
 CobblemonBridge(ShirokaiDaily mod){this.mod=mod;}
 String status(){return status+" | eventos ignorados="+events.failures();}
 void start(){
  close();
  try{
   registered.clear();models.clear();types.clear();List<Engine.Species> catalog=new ArrayList<>();
   for(Species s:PokemonSpecies.getSpecies()){
    String id=s.getResourceIdentifier().toString();registered.put(id,s);Set<String> pokemonTypes=new HashSet<>();
    for(var type:s.getTypes()){String name=type.getName().toLowerCase(Locale.ROOT);types.add(name);pokemonTypes.add(name);}
    if(s.getImplemented())catalog.add(new Engine.Species(id,Set.copyOf(pokemonTypes),s.getLabels().contains("legendary")||s.getLabels().contains("mythical")));
   }
   if(registered.isEmpty())throw new IllegalStateException("Registro de espécies vazio");
   validate(mod.config());mod.species(catalog);
   // Observe at creation, before any other mod can remove sessions/entities at battle end.
   subscribe("battle-start-pre",CobblemonEvents.BATTLE_STARTED_PRE,e->observe(e.getBattle()));
   subscribe("battle-start-post",CobblemonEvents.BATTLE_STARTED_POST,e->observe(e.getBattle()));
   subscribe("capture",CobblemonEvents.POKEMON_CAPTURED,this::captured);
   subscribe("victory",CobblemonEvents.BATTLE_VICTORY,this::victory);
   healthy=true;status="API pública conectada, "+registered.size()+" espécies; filtro de Raids ativo";ShirokaiDaily.LOG.info("Cobblemon: {}",status);
  }catch(Exception|LinkageError e){close();status="Hunts sem eventos: falha de inicialização da API";ShirokaiDaily.LOG.error(status,e);}
 }
 private <T> void subscribe(String kind,Observable<T> observable,Consumer<T> callback){
  subscriptions.add(observable.subscribe(Priority.HIGHEST,(Consumer<T>)event->{if(healthy)events.run(kind,()->callback.accept(event));}));
 }
 private void serverThread(){if(!mod.server().isOnThread())throw new IllegalStateException("Evento Cobblemon fora da thread do servidor; ignorado");}
 private void exclude(UUID battle,Set<UUID> pokemon){
  excludedPokemon.addAll(pokemon);mod.excludeRaid(battle,Set.copyOf(pokemon));
 }
 /** Public API calls are evaluated for all actors, including actors that did not lose. */
 private boolean observe(PokemonBattle battle){
  serverThread();return battleGate.allow(battle.getBattleId(),battle.getActors(),actor->{
   var entity=actor instanceof EntityBackedBattleActor<?> backed?backed.getEntity():null;
   boolean raid=ShirokaiRaidsAdapter.marked(entity);
   if(!raid)raid=raids.raidActor(actor);
   Set<UUID> pokemon=new HashSet<>();
   if(entity instanceof PokemonEntity anchor){
    UUID id=anchor.getPokemon().getUuid();pokemon.add(id);
    if(!raid)raid=RaidBattleGate.knownRaidTarget(actor.getType().name(),anchor.getPokemon().isWild(),excludedPokemon.contains(id));
   }
   for(var bp:actor.getPokemonList()){
    UUID original=bp.getOriginalPokemon().getUuid(),effected=bp.getEffectedPokemon().getUuid();
    boolean known=excludedPokemon.contains(original)||excludedPokemon.contains(effected);
    if(RaidBattleGate.sourceIdentity(bp.getOriginalPokemon().isWild(),known,ShirokaiRaidsAdapter.marked(bp.getEntity()))){pokemon.add(original);pokemon.add(effected);}
    if(!raid)raid=RaidBattleGate.knownRaidTarget(actor.getType().name(),bp.getOriginalPokemon().isWild(),known)||ShirokaiRaidsAdapter.marked(bp.getEntity());
   }
   return new RaidBattleGate.Evidence(raid,pokemon);
  },actor->{
   Set<UUID> targets=new HashSet<>();
   if(actor instanceof EntityBackedBattleActor<?> backed&&backed.getEntity() instanceof PokemonEntity anchor&&anchor.getPokemon().isWild())targets.add(anchor.getPokemon().getUuid());
   for(var bp:actor.getPokemonList())if(bp.getOriginalPokemon().isWild()){
    targets.add(bp.getOriginalPokemon().getUuid());targets.add(bp.getEffectedPokemon().getUuid());
   }return targets;
  });
 }
 private void captured(PokemonCapturedEvent event){
  serverThread();Pokemon pokemon=event.getPokemon();
  if(excludedPokemon.contains(pokemon.getUuid())||ShirokaiRaidsAdapter.marked(event.getPokeBallEntity())||ShirokaiRaidsAdapter.marked(pokemon.getEntity())){
   exclude(null,Set.of(pokemon.getUuid()));return;
  }
  mod.hunt(event.getPlayer().getUuid(),event(pokemon,"CAPTURE",null));
 }
 private record Credit(UUID player,Engine.HuntEvent event){}
 private void victory(BattleVictoryEvent event){
  PokemonBattle battle=event.getBattle();
  if(!observe(battle)||event.getWasWildCapture())return;
  // Reflection on actor.getClass() caused IllegalAccessException for private Raid actors.
  // Invocation is now through the public BattleActor API, after the Raid exclusion barrier.
  Set<UUID> winners=new LinkedHashSet<>();
  for(BattleActor actor:event.getWinners())if(actor.getType()==ActorType.PLAYER)actor.getPlayerUUIDs().forEach(winners::add);
  List<Credit> staged=new ArrayList<>();Set<UUID> stagedPokemon=new HashSet<>();
  try{
   for(BattleActor loser:event.getLosers()){
    if(loser.getType()!=ActorType.WILD)continue; // NPC and player losses retain their existing exclusion.
    for(var bp:loser.getPokemonList()){
     Pokemon pokemon=bp.getOriginalPokemon();if(bp.getHealth()>0||!pokemon.isWild()||!stagedPokemon.add(pokemon.getUuid()))continue;
     Engine.HuntEvent progress=event(pokemon,"DEFEAT",battle.getBattleId());for(UUID player:winners)staged.add(new Credit(player,progress));
    }
   }
  }catch(RuntimeException|Error error){battleGate.block(battle.getBattleId(),Set.of());throw error;}
  // No partial credit if a later actor in the same event cannot be inspected.
  for(Credit credit:staged)mod.hunt(credit.player(),credit.event());
 }
 private Engine.HuntEvent event(Pokemon pokemon,String action,UUID battle){
  String species=pokemon.getSpecies().getResourceIdentifier().toString();if(!registered.containsKey(species))throw new IllegalStateException("Espécie não registrada: "+species);
  Set<String> types=new HashSet<>();for(var type:pokemon.getTypes())types.add(type.getName().toLowerCase(Locale.ROOT));
  return new Engine.HuntEvent("pokemon/"+pokemon.getUuid(),action,species,types,pokemon.isLegendary()||pokemon.isMythical(),false,true,battle,Engine.HuntOrigin.NORMAL);
 }
 ItemStack model(String id){
  Species species=registered.get(id);if(species==null)return new ItemStack(Items.PAPER);ItemStack cached=models.get(id);
  if(cached==null){
   try{cached=PokemonItem.from(species,new String[0]);}
   catch(Exception|LinkageError error){events.run("model",()->{throw new IllegalStateException("Modelo Cobblemon indisponível: "+id,error);});cached=new ItemStack(Items.PAPER);}
   models.put(id,cached.copy());
  }return cached.copy();
 }
 boolean registryReady(){return !registered.isEmpty();}
 void validate(Config config){
  Set<String> configured=new HashSet<>();configured.addAll(config.hunts().whitelist());configured.addAll(config.hunts().blacklist());
  for(var mission:config.hunts().pool()){
   if(!mission.species().isEmpty())configured.add(mission.species());
   if(!mission.type().isEmpty()&&!types.contains(mission.type()))throw new IllegalArgumentException("Tipo não registrado: "+mission.type());
  }
  for(String id:configured)if(!registered.containsKey(id))throw new IllegalArgumentException("Espécie não registrada: "+id);
 }
 @Override public void close(){healthy=false;for(var subscription:subscriptions)subscription.unsubscribe();subscriptions.clear();mod.species(List.of());}
}
