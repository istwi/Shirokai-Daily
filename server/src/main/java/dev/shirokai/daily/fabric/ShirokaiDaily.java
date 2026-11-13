package dev.shirokai.daily.fabric;
import dev.shirokai.daily.core.*;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.Vec3d;
import com.mojang.brigadier.arguments.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.slf4j.*;
import static net.minecraft.server.command.CommandManager.*;

public final class ShirokaiDaily implements DedicatedServerModInitializer {
    static final Logger LOG=LoggerFactory.getLogger("shirokai_daily");
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"shirokai-daily-storage"));
    private final Map<UUID,Activity> sessions=new HashMap<>();
    private final List<RewardAdapter> adapters=new ArrayList<>();
    private final Set<UUID> delivering=new HashSet<>();
    private Path configDir; private Engine engine; private volatile Config config; private MinecraftServer server; private CobblemonBridge bridge;
    private int ticks;
    @Override public void onInitializeServer(){
        configDir=FabricLoader.getInstance().getConfigDir().resolve("shirokai-daily");
        try{config=Config.load(configDir);engine=new Engine(config,new Store(FabricLoader.getInstance().getGameDir().resolve("shirokai-daily-data")),new java.security.SecureRandom());}
        catch(Exception e){io.shutdown();throw new IllegalStateException("Configuração inválida; dados preservados",e);}
        Permissions.configure(config.general());
        ServiceLoader.load(RewardAdapter.class).forEach(adapters::add);
        adapters.add(new ImpactorAdapter());
        adapters.add(new ShirokaiCratesAdapter());
        ServerLifecycleEvents.SERVER_STARTED.register(s->{server=s;bridge=new CobblemonBridge(this);if(config.general().integrations().getOrDefault("cobblemon",true))bridge.start();});
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{
            for(var p:s.getPlayerManager().getPlayerList())sample(p);
            if(bridge!=null)bridge.close();io.shutdown();
            // Storage jobs never wait for the game thread, so shutdown cannot deadlock a delivery.
            try{if(!io.awaitTermination(30,TimeUnit.SECONDS))LOG.error("Timeout salvando dados; consulte os logs antes de reiniciar");}catch(InterruptedException e){Thread.currentThread().interrupt();}
        });
        ServerPlayConnectionEvents.JOIN.register((h,sender,s)->sessions.put(h.player.getUuid(),new Activity(System.nanoTime(),h.player.getX(),h.player.getY(),h.player.getZ(),h.player.getWorld().getRegistryKey().getValue().toString())));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{sample(h.player);sessions.remove(h.player.getUuid());});
        ServerTickEvents.END_SERVER_TICK.register(s->{if(++ticks%200==0)for(var p:s.getPlayerManager().getPlayerList())sample(p);});
        UseItemCallback.EVENT.register((p,w,h)->{if(p instanceof ServerPlayerEntity sp)active(sp);return TypedActionResult.pass(p.getStackInHand(h));});
        AttackEntityCallback.EVENT.register((p,w,h,e,hit)->{if(p instanceof ServerPlayerEntity sp)active(sp);return ActionResult.PASS;});
        UseBlockCallback.EVENT.register((p,w,h,hit)->{if(p instanceof ServerPlayerEntity sp)active(sp);return ActionResult.PASS;});
        PlayerBlockBreakEvents.AFTER.register((w,p,pos,state,entity)->{if(p instanceof ServerPlayerEntity sp)active(sp);});
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{
            for(String module:List.of("daily","playtime","hunts"))dispatcher.register(literal(module).requires(s->s.getEntity() instanceof ServerPlayerEntity p&&Permissions.has(p,"shirokaidaily."+module)).executes(c->{open(c.getSource().getPlayerOrThrow(),module,0);return 1;}));
            var root=literal("shirokaidaily").requires(s->s.getEntity()==null?s.hasPermissionLevel(2):s.getEntity() instanceof ServerPlayerEntity p&&Permissions.has(p,"shirokaidaily.admin"));
            root.then(literal("reload").executes(c->{var source=c.getSource();work(()->Config.load(configDir),next->{engineReload(next,source);});return 1;}));
            root.then(literal("status").executes(c->{c.getSource().sendFeedback(()->Text.literal("Shirokai Daily alpha | módulos="+config.general()+" | Cobblemon="+(bridge==null?"aguardando":bridge.status())+" | adaptadores="+adapters.size()),false);return 1;}));
            root.then(literal("status").then(argument("jogador",StringArgumentType.word()).executes(c->{UUID id=resolve(StringArgumentType.getString(c,"jogador"));work(()->engine.view(id,Instant.now()),s->c.getSource().sendFeedback(()->Text.literal("Transações: "+s.transactions.entrySet().stream().skip(Math.max(0,s.transactions.size()-20)).map(e->e.getKey()+"="+e.getValue().deliveries.stream().map(d->d.status).toList()).toList()),false));return 1;})));
            root.then(literal("reconcile").then(argument("uuid",StringArgumentType.word())
                .then(argument("transaction",StringArgumentType.string())
                .then(argument("index",IntegerArgumentType.integer(0))
                .then(argument("resolution",StringArgumentType.word()).executes(c->{
                    UUID id=UUID.fromString(StringArgumentType.getString(c,"uuid"));String key=StringArgumentType.getString(c,"transaction");
                    int index=IntegerArgumentType.getInteger(c,"index");String resolution=StringArgumentType.getString(c,"resolution");
                    String actor=c.getSource().getName();
                    work(()->{engine.reconcile(id,key,index,resolution);LOG.warn("Reconciliação por {}: {} {} {} -> {}",actor,id,key,index,resolution);return true;},ok->c.getSource().sendFeedback(()->Text.literal("Reconciliação registrada. PENDING pode ser retirado no menu de origem."),true));return 1;
                }))))));
            for(String module:List.of("daily","playtime","hunts")){
                var branch=literal(module);
                for(String action:module.equals("playtime")?List.of("add","set"):module.equals("hunts")?List.of("reset","reroll"):List.of("reset")){
                    var player=argument("jogador",StringArgumentType.word());
                    if(module.equals("playtime"))player.then(argument("minutos",LongArgumentType.longArg(0,Long.MAX_VALUE/60)).executes(c->{admin(c.getSource(),module+"-"+action,StringArgumentType.getString(c,"jogador"),LongArgumentType.getLong(c,"minutos")*60);return 1;}));
                    else player.executes(c->{admin(c.getSource(),module+"-"+action,StringArgumentType.getString(c,"jogador"),0);return 1;});
                    branch.then(literal(action).then(player));
                }root.then(branch);
            }dispatcher.register(root);
        });
    }
    private void engineReload(Config next,ServerCommandSource source){
        try{if(next.general().hunts()&&bridge!=null&&bridge.registryReady())bridge.validate(next);}
        catch(IllegalArgumentException invalid){source.sendError(Text.literal(invalid.getMessage()));return;}
        work(()->{engine.reload(next);return next;},value->{
            boolean hadBridge=config.general().integrations().getOrDefault("cobblemon",true);
            config=value;Permissions.configure(value.general());
            boolean needsBridge=value.general().integrations().getOrDefault("cobblemon",true);
            if(bridge!=null&&hadBridge!=needsBridge){if(needsBridge)bridge.start();else bridge.close();}
            source.sendFeedback(()->Text.literal("Configuração validada e recarregada; progresso preservado."),true);});
    }
    private void admin(ServerCommandSource source,String action,String name,long seconds){
        UUID id=resolve(name);work(()->{engine.admin(id,action,seconds,Instant.now());return true;},ok->source.sendFeedback(()->Text.literal("Atualizado: "+id),true));
    }
    private UUID resolve(String name){try{return UUID.fromString(name);}catch(IllegalArgumentException ignored){}
        var online=server.getPlayerManager().getPlayer(name);if(online!=null)return online.getUuid();
        // Do not issue blocking profile lookups or invent offline UUIDs on online-mode servers.
        throw new IllegalArgumentException("Jogador offline: use o UUID completo");
    }
    void active(ServerPlayerEntity p){var s=sessions.get(p.getUuid());if(s!=null)s.interaction(System.nanoTime());}
    private void sample(ServerPlayerEntity p){
        var s=sessions.get(p.getUuid());if(s==null)return;
        Activity.Sample sampled=s.sample(System.nanoTime(),p.getX(),p.getY(),p.getZ(),p.getWorld().getRegistryKey().getValue().toString(),config.playtime().afk(),config.playtime().afkSeconds());
        long elapsed=sampled.total(),eligible=sampled.eligible();if(elapsed==0)return;
        UUID id=p.getUuid();Instant end=Instant.now();
        work(()->{engine.time(id,elapsed,eligible,end);return true;},ignored->{});
    }
    Set<String> permissions(ServerPlayerEntity p){
        var names=new HashSet<>(List.of("shirokaidaily.daily","shirokaidaily.playtime","shirokaidaily.hunts","shirokaidaily.vip"));
        config.rewards().values().forEach(r->{if(!r.permission().isEmpty())names.add(r.permission());});
        names.removeIf(n->!Permissions.has(p,n));return Set.copyOf(names);
    }
    public void open(ServerPlayerEntity p,String module,int page){
        if(!enabled(module)||!Permissions.has(p,"shirokaidaily."+module)){p.sendMessage(Text.literal("Módulo indisponível."),false);return;}
        active(p);UUID id=p.getUuid();work(()->engine.view(id,Instant.now()),state->{if(!p.isDisconnected())Menus.open(this,p,module,page,state,config);});
    }
    private boolean enabled(String module){return switch(module){case "daily"->config.general().daily();case "playtime"->config.general().playtime();case "hunts"->config.general().hunts();default->false;};}
    void claim(ServerPlayerEntity p,String origin,String target){
        if(!enabled(origin)||!Permissions.has(p,"shirokaidaily."+origin))return;
        UUID id=p.getUuid();Set<String> perms=permissions(p);
        work(()->engine.claim(id,origin,target,perms,Instant.now()),key->{if(key==null)p.sendMessage(Text.literal(config.messages().get("unavailable")),false);else{deliver(p,origin);p.sendMessage(Text.literal(config.messages().get("claimed")),false);}open(p,origin,0);});
    }
    void deliver(ServerPlayerEntity p,String origin){
        if(!enabled(origin)||!Permissions.has(p,"shirokaidaily."+origin)||!delivering.add(p.getUuid()))return;
        work(()->engine.pending(p.getUuid(),origin),list->deliverNext(p,list,0));
    }
    private void deliverNext(ServerPlayerEntity p,List<Engine.Pending> list,int index){
        UUID id=p.getUuid();if(index==list.size()||p.isDisconnected()){delivering.remove(id);return;}
        var pending=list.get(index);
        String origin=pending.transaction().split("/",2)[0];
        if(!enabled(origin)||!Permissions.has(p,"shirokaidaily."+origin)){delivering.remove(id);return;}
        if(!Engine.allowed(pending.reward(),permissions(p))){deliverNext(p,list,index+1);return;}
        work(()->engine.begin(id,pending),started->{
            if(!started){deliverNext(p,list,index+1);return;}
            CompletionStage<String> result;
            try{result=p.isDisconnected()?CompletableFuture.completedFuture("PENDING"):give(p,pending.reward(),id+"/"+pending.transaction()+"/"+pending.index());}
            catch(Exception|LinkageError e){LOG.error("Entrega incerta {} {}",id,pending,e);result=CompletableFuture.completedFuture("UNCERTAIN");}
            result.whenComplete((outcome,error)->{
                String finalResult=error!=null||!Set.of("DONE","PENDING","UNCERTAIN").contains(outcome==null?"":outcome)?"UNCERTAIN":outcome;
                work(()->{engine.finish(id,pending,finalResult);LOG.info("Entrega {} {} {}",id,pending.transaction()+"/"+pending.index(),finalResult);return true;},ok->deliverNext(p,list,index+1));
            });
        });
    }
    private CompletionStage<String> give(ServerPlayerEntity p,Config.Reward r,String key)throws Exception{
        if(r.type().equals("ITEM"))return CompletableFuture.completedFuture(giveItem(p,r));
        if(r.type().equals("COMMAND")){
            if(!p.getGameProfile().getName().matches("[A-Za-z0-9_]{1,16}"))return CompletableFuture.completedFuture("PENDING");
            String command=r.value().replace("{uuid}",p.getUuidAsString()).replace("{player}",p.getGameProfile().getName()).replace("{amount}",Integer.toString(r.amount())).replace("{transaction}",key);
            if(command.startsWith("/"))command=command.substring(1);
            int result=server.getCommandManager().getDispatcher().execute(command,server.getCommandSource().withLevel(4));
            return CompletableFuture.completedFuture(result>0?"DONE":"UNCERTAIN");
        }
        if(config.general().integrations().getOrDefault("rewards",true))for(var adapter:adapters)if(adapter.supports(r.type()))return adapter.deliver(p,r,key);
        return CompletableFuture.completedFuture("PENDING");
    }
    private String giveItem(ServerPlayerEntity p,Config.Reward r){
        Identifier identifier=Identifier.tryParse(r.value());if(identifier==null||!Registries.ITEM.containsId(identifier))return "PENDING";
        Item item=Registries.ITEM.get(identifier);if(item==Items.AIR)return "PENDING";
        int remaining=r.amount();for(int i=0;i<36;i++){ItemStack s=p.getInventory().getStack(i);remaining-=s.isEmpty()?item.getMaxCount():s.isOf(item)&&s.getComponents().equals(new ItemStack(item).getComponents())?Math.max(0,s.getMaxCount()-s.getCount()):0;}
        if(remaining>0)return "PENDING";
        int count=r.amount();while(count>0){int n=Math.min(item.getMaxCount(),count);ItemStack stack=new ItemStack(item,n);if(!p.getInventory().insertStack(stack)||!stack.isEmpty())return "UNCERTAIN";count-=n;}
        p.getInventory().markDirty();return "DONE";
    }
    void hunt(UUID id,Engine.HuntEvent event){if(!config.general().integrations().getOrDefault("cobblemon",true))return;work(()->{engine.hunt(id,event,Instant.now());return true;},ignored->{});}
    <T> void work(Callable<T> operation,Consumer<T> done){
        if(io.isShutdown())return;
        io.submit(()->{try{T result=operation.call();if(server!=null)server.execute(()->{try{done.accept(result);}catch(Exception e){LOG.error("Falha na operação de jogo",e);}});}catch(Exception e){LOG.error("Operação cancelada; consulte dados/logs antes de tentar novamente",e);}});
    }
    Config config(){return config;}
    MinecraftServer server(){return server;}
}
