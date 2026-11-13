package dev.shirokai.daily.fabric;
import dev.shirokai.daily.core.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import java.time.*;
import java.util.*;

/** Only the vanilla generic container protocol; all inventory gestures are consumed server-side. */
final class Menus {
    record Entry(String icon,String title,List<String> lore,Runnable click){}
    static void open(ShirokaiDaily mod,ServerPlayerEntity player,String module,int page,State state,Config config){
        Config.Menu settings=config.menus().get(module);List<Entry> entries=new ArrayList<>();Instant now=Instant.now();
        switch(module){
            case "daily" -> {
                var daily=state.daily;boolean today=daily.last.equals(config.day(now).toString());
                long gap=daily.last.isEmpty()?0:java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(daily.last),config.day(now));
                boolean missed=gap>1&&(config.daily().streakPolicy().equals("RESET")||config.daily().streakPolicy().equals("GRACE")&&gap-1>config.daily().graceDays());
                int previous=missed?0:daily.day;
                int next=previous>=config.daily().days()?(config.daily().completion().equals("STOP")?-1:1):previous+1;
                for(int i=1;i<=config.daily().days();i++){
                    int day=i;boolean available=!today&&day==next;
                    String status=today&&day<=daily.day?"RESGATADO":available?"DISPONÍVEL":day==next?"PRÓXIMO":"BLOQUEADO";
                    entries.add(entry(settings,"daily",vars("day",i,"status",status,"streak",daily.streak,"resets",daily.resets,"rewards",rewards(config,config.daily().rewards().get(i)),"reset",reset(config,now)),available?"minecraft:lime_dye":status.equals("RESGATADO")?"minecraft:gray_dye":"minecraft:light_blue_dye","Dia "+i+" • "+status,List.of("Streak: "+daily.streak+" • Resets: "+daily.resets,"Recompensas: "+config.daily().rewards().get(i),"Reset: "+config.nextReset(now).atZone(ZoneId.of(config.general().zone()))),available?()->mod.claim(player,module,""):()->{}));
                }
            }
            case "playtime" -> {
                for(var goal:config.playtime().goals()){
                    long progress=switch(goal.period()){case "DAILY"->state.playtime.daily;case "WEEKLY"->state.playtime.weekly;default->state.playtime.eligible;};
                    String key=goal.period()+"/"+(goal.period().equals("DAILY")?config.day(now):goal.period().equals("WEEKLY")?config.week(now):"all")+"/"+goal.id();
                    boolean claimed=state.playtime.claimed.contains(key),available=!claimed&&progress>=goal.seconds();
                    entries.add(entry(settings,"playtime",vars("goal",label(goal.id()),"status",claimed?"RESGATADO":available?"DISPONÍVEL":"EM PROGRESSO","progress",progress/60,"target",goal.seconds()/60,"total",state.playtime.total/60,"eligible",state.playtime.eligible/60,"period",label(goal.period()),"rewards",rewards(config,goal.rewards())),available?"minecraft:lime_dye":"minecraft:clock",goal.id()+" • "+(claimed?"RESGATADO":available?"DISPONÍVEL":"EM PROGRESSO"),List.of(goal.period()+" • "+progress/60+" / "+goal.seconds()/60+" min","Total: "+state.playtime.total/60+" min • Elegível: "+state.playtime.eligible/60+" min","Recompensas: "+goal.rewards()),available?()->mod.claim(player,module,goal.id()):()->{}));
                }
            }
            case "hunts" -> state.hunts.quests.forEach((id,q)->{
                boolean available=!q.claimed&&q.progress>=q.mission.amount();var m=q.mission;
                Entry hunt=entry(settings,"hunts",vars("mission",m.id(),"status",q.claimed?"RESGATADO":available?"DISPONÍVEL":"EM PROGRESSO","action",label(m.action()),"species",m.species().isEmpty()?"Qualquer Pokémon":label(m.species()),"type",m.type().isEmpty()?"Qualquer":label(m.type()),"difficulty",label(m.difficulty()),"progress",q.progress,"target",m.amount(),"period",label(q.period),"epoch",q.epoch,"rewards",rewards(config,m.rewards())),available?"minecraft:lime_dye":"minecraft:compass",m.id()+" • "+(q.claimed?"RESGATADO":available?"DISPONÍVEL":"EM PROGRESSO"),List.of(m.action()+" • "+(m.species().isEmpty()?"Qualquer espécie":m.species()),"Tipo: "+(m.type().isEmpty()?"qualquer":m.type())+" • "+m.difficulty(),q.progress+" / "+m.amount()+" • "+q.period,"Período: "+q.epoch,"Recompensas: "+m.rewards()),available?()->mod.claim(player,module,id):()->{});
                // An explicitly configured icon takes priority over the automatic Pokémon model.
                if(!m.species().isEmpty()&&!settings.icons().containsKey("hunts")&&!settings.icons().containsKey("hunts."+(q.claimed?"RESGATADO":available?"DISPONÍVEL":"EM PROGRESSO")))
                    hunt=new Entry("pokemon:"+m.species(),hunt.title,hunt.lore,hunt.click);
                entries.add(hunt);
            });
        }
        int pages=Math.max(1,(entries.size()+settings.pageSize()-1)/settings.pageSize()),current=Math.max(0,Math.min(page,pages-1));
        var inventory=new SimpleInventory(54);var actions=new HashMap<Integer,Runnable>();
        ItemStack filler=item(settings.icon()," ",List.of());for(int i=0;i<54;i++)inventory.setStack(i,filler.copy());
        List<Integer> slots=settings.slots();
        for(int i=0;i<settings.pageSize();i++){int index=current*settings.pageSize()+i;if(index>=entries.size())break;Entry e=entries.get(index);int slot=slots.get(i);inventory.setStack(slot,item(e.icon,e.title,e.lore));actions.put(slot,e.click);}
        Entry profile=entry(settings,"profile",vars("player",player.getGameProfile().getName(),"page",current+1,"pages",pages,"module",module,"reset",reset(config,now)),"minecraft:player_head",player.getGameProfile().getName(),List.of("✦ "+module,"Página "+(current+1)+" / "+pages,"Reset diário: "+config.nextReset(now).atZone(ZoneId.of(config.general().zone())).toLocalTime()),()->{});
        inventory.setStack(settings.profileSlot(),item(profile.icon,profile.title,profile.lore));
        ItemStack back=new ItemStack(Items.ARROW);
        back.set(DataComponentTypes.CUSTOM_NAME,Text.literal("VOLTAR AO MENU").formatted(Formatting.YELLOW,Formatting.BOLD));
        back.set(DataComponentTypes.LORE,new LoreComponent(List.of(Text.literal("Retornar ao menu principal.").formatted(Formatting.GRAY),Text.empty(),Text.literal("➜ Clique para voltar!").formatted(Formatting.YELLOW))));
        inventory.setStack(settings.backSlot(),back);
        actions.put(settings.backSlot(),()->{player.closeHandledScreen();player.getServer().getCommandManager().executeWithPrefix(player.getCommandSource(),"menu");});
        Entry pending=entry(settings,"pending",Map.of(),"minecraft:chest","Entregas pendentes",List.of("Clique para tentar entregar novamente.","Moedas/fragmentos exigem integração oficial."),()->{});
        inventory.setStack(settings.pendingSlot(),item(pending.icon,pending.title,pending.lore));
        actions.put(settings.pendingSlot(),()->mod.deliver(player,module));
        if(current>0){inventory.setStack(settings.previousSlot(),item(settings.icons().getOrDefault("previous","minecraft:arrow"),settings.texts().getOrDefault("previous","Anterior"),settings.lore().getOrDefault("previous",List.of())));actions.put(settings.previousSlot(),()->mod.open(player,module,current-1));}
        if(current+1<pages){inventory.setStack(settings.nextSlot(),item(settings.icons().getOrDefault("next","minecraft:arrow"),settings.texts().getOrDefault("next","Próxima"),settings.lore().getOrDefault("next",List.of())));actions.put(settings.nextSlot(),()->mod.open(player,module,current+1));}
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory((sync,inv,p)->new LockedMenu(sync,inv,inventory,actions),Text.literal(settings.title()).formatted(Formatting.AQUA)));
    }
    private static Map<String,String> vars(Object... args){var result=new HashMap<String,String>();for(int i=0;i<args.length;i+=2)result.put(args[i].toString(),String.valueOf(args[i+1]));return result;}
    private static String render(String template,Map<String,String> values){for(var e:values.entrySet())template=template.replace("{"+e.getKey()+"}",e.getValue());return template;}
    private static Entry entry(Config.Menu settings,String key,Map<String,String> values,String icon,String title,List<String> lore,Runnable action){
        String status=values.getOrDefault("status","");
        values=new HashMap<>(values);
        values.put("hint",switch(status){case "DISPONÍVEL"->"➜ Clique para resgatar!";case "RESGATADO"->"✓ Recompensa já resgatada";case "EM PROGRESSO"->"Complete o objetivo para resgatar.";default->"Aguarde para desbloquear.";});
        String configuredIcon=settings.icons().getOrDefault(key+"."+status,settings.icons().getOrDefault(key,icon));
        Map<String,String> renderedValues=values;
        return new Entry(configuredIcon,render(settings.texts().getOrDefault(key,Config.defaultMenu("hunts").texts().getOrDefault(key,title)),values),settings.lore().getOrDefault(key,Config.defaultMenu("hunts").lore().getOrDefault(key,lore)).stream().map(line->render(line,renderedValues)).toList(),action);
    }
    private static String reset(Config config,Instant now){return config.nextReset(now).atZone(ZoneId.of(config.general().zone())).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"));}
    private static String label(String value){
        value=value.substring(value.indexOf(':')+1);
        return switch(value){case "fire"->"Fogo";case "water"->"Água";case "grass"->"Planta";case "electric"->"Elétrico";case "ice"->"Gelo";case "fighting"->"Lutador";case "poison"->"Veneno";case "ground"->"Terra";case "flying"->"Voador";case "psychic"->"Psíquico";case "bug"->"Inseto";case "rock"->"Pedra";case "ghost"->"Fantasma";case "dragon"->"Dragão";case "dark"->"Sombrio";case "steel"->"Aço";case "fairy"->"Fada";case "normal"->"Normal";case "first_30m"->"Primeiros 30 minutos";case "daily_hour"->"Uma hora por dia";case "weekly_6h"->"Seis horas na semana";case "diamond"->"Diamante";case "emerald"->"Esmeralda";case "gold_ingot"->"Barra de ouro";case "golden_apple"->"Maçã dourada";case "CAPTURE"->"Capture";case "DEFEAT"->"Derrote";case "EASY"->"Fácil";case "MEDIUM"->"Média";case "HARD"->"Difícil";case "DAILY"->"Diária";case "WEEKLY"->"Semanal";case "PERMANENT"->"Permanente";default->{String name=value.substring(value.indexOf(':')+1).replace('_',' ');yield name.isEmpty()?name:Character.toUpperCase(name.charAt(0))+name.substring(1);}};
    }
    private static String rewards(Config config,List<String> ids){
        List<String> descriptions=new ArrayList<>();boolean random=false;
        for(String id:ids){var reward=config.rewards().get(id);if(reward==null)continue;
            if(reward.weight()>0){random=true;continue;}
            String prize=switch(reward.type()){case "COMMAND"->"Recompensa especial";case "DOLLARS"->"Dollars";case "TOKENS"->"Tokens";case "CRATE_FRAGMENT"->"Fragmentos de caixa";default->label(reward.value());};
            descriptions.add(reward.amount()+"× "+prize+(reward.chance()<1?" ("+Math.round(reward.chance()*100)+"%)":""));
        }
        if(random)descriptions.add("1 bônus aleatório");return String.join(" + ",descriptions);
    }
    /** Legacy color codes in administrator templates; reset italics on vanilla item text. */
    private static Text styled(String value,Formatting base){
        var result=Text.empty();var style=net.minecraft.text.Style.EMPTY.withColor(base).withItalic(false);StringBuilder part=new StringBuilder();
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);Formatting code=(c=='&'||c=='§')&&i+1<value.length()?Formatting.byCode(value.charAt(i+1)):null;
            if(code!=null){result.append(Text.literal(part.toString()).setStyle(style));part.setLength(0);i++;
                style=code==Formatting.RESET?net.minecraft.text.Style.EMPTY.withColor(base).withItalic(false):code.isColor()?net.minecraft.text.Style.EMPTY.withColor(code).withItalic(false):style.withFormatting(code);
            }else part.append(c);
        }
        return result.append(Text.literal(part.toString()).setStyle(style));
    }
    private static ItemStack item(String id,String name,List<String> lore){
        ItemStack stack=id.startsWith("pokemon:")?PokemonModels.create(id.substring(8)):ItemStack.EMPTY;
        if(stack.isEmpty()){
            Identifier identifier=Identifier.tryParse(id);Item icon=identifier==null?Items.PAPER:Registries.ITEM.get(identifier);if(icon==Items.AIR)icon=Items.PAPER;
            stack=new ItemStack(id.startsWith("pokemon:")?Items.COMPASS:icon);
        }
        stack.set(DataComponentTypes.CUSTOM_NAME,styled(name,Formatting.AQUA));
        stack.set(DataComponentTypes.LORE,new LoreComponent(lore.stream().map(line->styled(line,Formatting.GRAY)).toList()));return stack;
    }
    private static final class LockedMenu extends GenericContainerScreenHandler {
        private final Map<Integer,Runnable> actions;
        LockedMenu(int sync,PlayerInventory player,Inventory inventory,Map<Integer,Runnable> actions){super(ScreenHandlerType.GENERIC_9X6,sync,player,inventory,6);this.actions=actions;}
        @Override public void onSlotClick(int slot,int button,SlotActionType type,PlayerEntity player){if(type==SlotActionType.PICKUP&&button==0&&slot>=0&&slot<54){Runnable action=actions.get(slot);if(action!=null)action.run();}sendContentUpdates();}
        @Override public ItemStack quickMove(PlayerEntity player,int slot){return ItemStack.EMPTY;}
        @Override public boolean canUse(PlayerEntity player){return true;}
    }
}
