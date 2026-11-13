package dev.shirokai.daily.fabric;
import dev.shirokai.daily.core.Engine;
import dev.shirokai.daily.core.Config;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.server.network.ServerPlayerEntity;

/** Guarded optional linkage. Unknown API shapes stop Hunts instead of accepting guessed events. */
final class CobblemonBridge implements AutoCloseable {
    private final ShirokaiDaily mod;
    private final List<Runnable> cleanup=new ArrayList<>();
    private final Set<String> species=new HashSet<>(),types=new HashSet<>();
    private String status="não iniciado"; private boolean healthy;
    CobblemonBridge(ShirokaiDaily mod){this.mod=mod;}
    String status(){return status;}
    void start(){
        try {
            Class<?> registry=Class.forName("com.cobblemon.mod.common.api.pokemon.PokemonSpecies");
            Object instance=registry.getField("INSTANCE").get(null);
            species.clear();types.clear();
            for(Object s:(Iterable<?>)get(instance,"getSpecies")){
                species.add(get(s,"getResourceIdentifier").toString());
                for(Object type:(Iterable<?>)get(s,"getTypes"))types.add(get(type,"getName").toString().toLowerCase(Locale.ROOT));
            }
            if(species.isEmpty())throw new IllegalStateException("Registro de espécies vazio");
            validate(mod.config());
            subscribe("POKEMON_CAPTURED",this::captured);subscribe("BATTLE_VICTORY",this::victory);
            healthy=true;status="API conectada, "+species.size()+" espécies; validar em servidor 1.8.3";
            ShirokaiDaily.LOG.info("Cobblemon: {}",status);
        }catch(Exception e){close();status="Hunts sem eventos: API incompatível";ShirokaiDaily.LOG.error(status,e);}
    }
    boolean registryReady(){return !species.isEmpty();}
    void validate(Config config){
        Set<String> configured=new HashSet<>();configured.addAll(config.hunts().whitelist());configured.addAll(config.hunts().blacklist());
        for(var mission:config.hunts().pool()){
            if(!mission.species().isEmpty())configured.add(mission.species());
            if(!mission.type().isEmpty()&&!types.contains(mission.type()))throw new IllegalArgumentException("Tipo não registrado: "+mission.type());
        }
        for(String name:configured)if(!species.contains(name))throw new IllegalArgumentException("Espécie não registrada: "+name+" (use namespace:espécie)");
    }
    private void subscribe(String field,Consumer<Object> callback)throws Exception {
        Object observable=Class.forName("com.cobblemon.mod.common.api.events.CobblemonEvents").getField(field).get(null);
        Class<?> function=Class.forName("kotlin.jvm.functions.Function1"),priority=Class.forName("com.cobblemon.mod.common.api.Priority");
        Object normal=priority.getField("NORMAL").get(null);
        Object handler=Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args)->{
            if(method.getName().equals("invoke")){
                if(healthy){try{callback.accept(args[0]);}catch(Exception e){healthy=false;status="Hunts pausadas: falha na API";ShirokaiDaily.LOG.error(status,e);}}
                return Class.forName("kotlin.Unit").getField("INSTANCE").get(null);
            }
            return switch(method.getName()){case "toString"->"ShirokaiDailyEventHandler";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
        });
        Object subscription=observable.getClass().getMethod("subscribe",priority,function).invoke(observable,normal,handler);
        cleanup.add(()->{try{get(subscription,"unsubscribe");}catch(Exception e){ShirokaiDaily.LOG.warn("Não foi possível cancelar inscrição Cobblemon",e);}});
    }
    private void captured(Object event){
        try {
            Object player=get(event,"getPlayer"),pokemon=get(event,"getPokemon");
            if(!(player instanceof ServerPlayerEntity p)||!mod.server().isOnThread())throw new IllegalStateException("Evento fora da thread do servidor");
            mod.hunt(p.getUuid(),event(pokemon,"pokemon/"+get(pokemon,"getUuid"),"CAPTURE",false));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private void victory(Object event){
        try {
            if(!mod.server().isOnThread())throw new IllegalStateException("Evento fora da thread do servidor");
            if((boolean)get(event,"getWasWildCapture"))return;
            List<UUID> winners=new ArrayList<>();
            for(Object actor:(Iterable<?>)get(event,"getWinners"))if(get(actor,"getType").toString().equals("PLAYER"))for(Object id:(Iterable<?>)get(actor,"getPlayerUUIDs"))winners.add((UUID)id);
            // PvP and NPC battles do not count. Only actually fainted, unowned wild Pokémon.
            for(Object loser:(Iterable<?>)get(event,"getLosers")){
                if(!get(loser,"getType").toString().equals("WILD"))continue;
                for(Object battlePokemon:(Iterable<?>)get(loser,"getPokemonList")){
                    if(((Number)get(battlePokemon,"getHealth")).intValue()>0)continue;
                    Object pokemon=get(battlePokemon,"getOriginalPokemon");if(!(boolean)get(pokemon,"isWild"))continue;
                    var progress=event(pokemon,"pokemon/"+get(pokemon,"getUuid"),"DEFEAT",false);
                    for(UUID player:winners)mod.hunt(player,progress);
                }
            }
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private Engine.HuntEvent event(Object pokemon,String id,String action,boolean owned)throws ReflectiveOperationException {
        String name=get(get(pokemon,"getSpecies"),"getResourceIdentifier").toString();if(!species.contains(name))throw new IllegalStateException("Espécie não registrada: "+name);
        Set<String> types=new HashSet<>();for(Object type:(Iterable<?>)get(pokemon,"getTypes"))types.add(get(type,"getName").toString().toLowerCase(Locale.ROOT));
        boolean legendary=(boolean)get(pokemon,"isLegendary")||(boolean)get(pokemon,"isMythical");
        return new Engine.HuntEvent(id,action,name,types,legendary,owned,true);
    }
    private static Object get(Object value,String method)throws ReflectiveOperationException{return value.getClass().getMethod(method).invoke(value);}
    @Override public void close(){healthy=false;cleanup.forEach(Runnable::run);cleanup.clear();}
}
