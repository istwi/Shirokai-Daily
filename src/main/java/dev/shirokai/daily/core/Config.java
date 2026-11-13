package dev.shirokai.daily.core;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Immutable configuration snapshots: reload only swaps a fully validated snapshot. */
public record Config(General general, Daily daily, Playtime playtime, Hunts hunts,
                     Map<String, Reward> rewards, Map<String, String> messages, Map<String, Menu> menus) {
    public record General(String zone, String resetTime, boolean daily, boolean playtime, boolean hunts,
                          Map<String,String> permissions,Map<String,Boolean> integrations) {
        public General(String zone,String resetTime,boolean daily,boolean playtime,boolean hunts){
            this(zone,resetTime,daily,playtime,hunts,Map.of(),Map.of("luckperms",true,"cobblemon",true,"rewards",true));
        }
    }
    public record Daily(int days, String streakPolicy, int graceDays, String completion,
                        Map<Integer,List<String>> rewards, List<String> vipRewards) {}
    public record Goal(String id, String period, long seconds, List<String> rewards) {}
    public record Playtime(boolean afk, int afkSeconds, List<Goal> goals) {}
    public record Mission(String id, String action, String species, String type, String difficulty,
                          int amount, int weight, List<String> rewards) {}
    public record Hunts(int dailyCount, int weeklyCount, boolean random, boolean retainCompleted,
                        boolean excludeLegendary, Set<String> whitelist, Set<String> blacklist, List<Mission> pool) {}
    public record Reward(String type, String value, int amount, double chance, int weight,
                         String permission, String condition) {}
    public record Menu(String title, String icon, int pageSize, int backSlot,
                       List<Integer> slots,int profileSlot,int pendingSlot,int previousSlot,int nextSlot,
                       Map<String,String> icons,Map<String,String> texts,Map<String,List<String>> lore) {
        public Menu(String title,String icon,int pageSize,int backSlot){
            this(title,icon,pageSize,backSlot,defaultSlots(),4,48,45,53,Map.of(),Map.of(),Map.of());
        }
        private static List<Integer> defaultSlots(){List<Integer> result=new ArrayList<>();for(int r=1;r<=4;r++)for(int c=1;c<=7;c++)result.add(r*9+c);return List.copyOf(result);}
    }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config load(Path path) throws IOException {
        Files.createDirectories(path.resolve("menus"));
        var rewards = new LinkedHashMap<String,Reward>();
        rewards.put("daily_item", new Reward("ITEM","minecraft:diamond",1,1,0,"",""));
        rewards.put("playtime_item",new Reward("ITEM","minecraft:emerald",2,1,0,"",""));
        rewards.put("hunt_item",new Reward("ITEM","minecraft:gold_ingot",4,1,0,"",""));
        rewards.put("bonus_emerald",new Reward("ITEM","minecraft:emerald",3,1,60,"",""));
        rewards.put("bonus_diamond",new Reward("ITEM","minecraft:diamond",2,1,30,"",""));
        rewards.put("bonus_apple",new Reward("ITEM","minecraft:golden_apple",1,1,10,"",""));
        Map<Integer,List<String>> days=new LinkedHashMap<>();
        for(int i=1;i<=7;i++) days.put(i,List.of("daily_item"));
        Map<String,Menu> menus=new LinkedHashMap<>();
        for(String m:List.of("daily","playtime","hunts")) menus.put(m,read(path.resolve("menus/"+m+".json"),Menu.class,defaultMenu(m)));
        Config c=new Config(
            read(path.resolve("config.json"),General.class,new General("America/Sao_Paulo","00:00",true,true,true)),
            read(path.resolve("daily.json"),Daily.class,new Daily(7,"RESET",1,"RESTART",days,List.of())),
            read(path.resolve("playtime.json"),Playtime.class,new Playtime(true,300,List.of(new Goal("first_30m","PERMANENT",1800,List.of("playtime_item","bonus_emerald","bonus_diamond","bonus_apple")),new Goal("daily_hour","DAILY",3600,List.of("playtime_item","bonus_emerald","bonus_diamond","bonus_apple")),new Goal("weekly_6h","WEEKLY",21600,List.of("playtime_item","bonus_emerald","bonus_diamond","bonus_apple"))))),
            read(path.resolve("hunts.json"),Hunts.class,new Hunts(3,2,true,false,true,Set.of(),Set.of(),List.of(
                new Mission("catch_any","CAPTURE","","","EASY",5,4,List.of("hunt_item","bonus_emerald","bonus_diamond","bonus_apple")),
                new Mission("catch_fire","CAPTURE","cobblemon:charmander","fire","MEDIUM",3,2,List.of("hunt_item","bonus_emerald","bonus_diamond","bonus_apple")),
                new Mission("defeat_any","DEFEAT","","","HARD",10,1,List.of("hunt_item","bonus_emerald","bonus_diamond","bonus_apple"))))),
            Map.copyOf(readMap(path.resolve("rewards.json"),Reward.class,rewards)),
            Map.copyOf(readMap(path.resolve("messages.json"),String.class,Map.of("claimed","Recompensa processada; confira as pendências.","unavailable","Indisponível ou já resgatado.","pending","Entrega pendente; tente novamente neste menu."))),Map.copyOf(menus));
        c.validate(); return c;
    }
    public static Menu defaultMenu(String module){
        Menu base=new Menu("✦ "+switch(module){case "daily"->"Recompensas diárias";case "playtime"->"Tempo de jogo";default->"Central de caçadas";},"minecraft:gray_stained_glass_pane",28,49);
        var texts=Map.of("daily","&b&lDIA {day} &8• &f{status}","playtime","&b&l{goal} &8• &f{status}","hunts","&b&l{action} &f{species}","profile","&b&l{player}","pending","&6&lEntregas pendentes");
        var lore=Map.of(
            "daily",List.of("&8RECOMPENSA DIÁRIA","","&7Sequência: &f{streak} dias &8• &7Reinícios: &f{resets}","","&6Prêmios &8› &f{rewards}","&7Próximo reset: &f{reset}","","&e{hint}"),
            "playtime",List.of("&8TEMPO DE JOGO • {period}","","&7Progresso: &b{progress} &7/ &f{target} min","&7Tempo elegível: &f{eligible} min","","&6Prêmios &8› &f{rewards}","","&e{hint}"),
            "hunts",List.of("&8CAÇADA • {period}","","&7Objetivo: &f{action} {target}× {species}","&7Tipo: &f{type} &8• &7Dificuldade: &f{difficulty}","","&7Progresso: &b{progress} &7/ &f{target}","&7Estado: &f{status}","","&6Prêmios &8› &f{rewards}","","&e{hint}"),
            "profile",List.of("&8SHIROKAI DAILY","","&7Página &f{page} &7de &f{pages}","&7Próximo reset: &f{reset}"),
            "pending",List.of("&7Tem prêmios aguardando entrega?","","&e➜ Clique para tentar receber."));
        List<Integer> content=module.equals("playtime")?List.of(20,22,24):List.of(19,20,21,22,23,24,25);
        return new Menu(base.title,base.icon,content.size(),base.backSlot,content,base.profileSlot,base.pendingSlot,base.previousSlot,base.nextSlot,base.icons,texts,lore);
    }
    private static <T> T read(Path p,Class<T> t,T fallback)throws IOException {
        if(!Files.exists(p)) Files.writeString(p,JSON.toJson(fallback),StandardOpenOption.CREATE_NEW);
        try(var reader=Files.newBufferedReader(p)){T result=JSON.fromJson(reader,t); if(result==null)throw new IllegalArgumentException("JSON vazio: "+p); return result;}
    }
    private static <T> Map<String,T> readMap(Path p,Class<T> t,Map<String,T> fallback)throws IOException {
        if(!Files.exists(p))Files.writeString(p,JSON.toJson(fallback),StandardOpenOption.CREATE_NEW);
        var result=new LinkedHashMap<String,T>();
        try(var reader=Files.newBufferedReader(p)){JsonObject o=JsonParser.parseReader(reader).getAsJsonObject();o.entrySet().forEach(e->result.put(e.getKey(),JSON.fromJson(e.getValue(),t)));}return result;
    }
    public void validate(){
        ZoneId.of(general.zone);LocalTime.parse(general.resetTime);
        require(general.permissions!=null&&general.integrations!=null,"Configuração de permissões/integrações ausente");
        general.permissions.forEach((key,value)->require(key!=null&&!key.isBlank()&&value!=null&&!value.isBlank(),"Nó de permissão inválido"));
        require(Set.of(7,14,30).contains(daily.days),"Calendário: 7, 14 ou 30");
        require(Set.of("RESET","PAUSE","GRACE").contains(daily.streakPolicy),"Política de streak inválida");
        require(Set.of("RESTART","STOP").contains(daily.completion),"Conclusão inválida");
        require(daily.graceDays>=0&&playtime.afkSeconds>=1,"Tolerância/AFK inválidos");
        require(hunts.dailyCount>=0&&hunts.dailyCount<=28&&hunts.weeklyCount>=0&&hunts.weeklyCount<=28,"Quantidade de hunts inválida");
        var ids=new HashSet<String>();
        for(Goal g:playtime.goals){require(ids.add(g.id)&&!g.id.isBlank()&&g.seconds>0&&Set.of("PERMANENT","DAILY","WEEKLY").contains(g.period),"Meta inválida/duplicada");refs(g.rewards);}
        ids.clear();
        for(Mission m:hunts.pool){require(ids.add(m.id)&&!m.id.isBlank()&&m.amount>0&&m.weight>0&&Set.of("CAPTURE","DEFEAT").contains(m.action)&&Set.of("EASY","MEDIUM","HARD").contains(m.difficulty),"Missão inválida/duplicada");refs(m.rewards);}
        require(hunts.dailyCount+hunts.weeklyCount==0||!hunts.pool.isEmpty(),"Pool vazio");
        for(int i=1;i<=daily.days;i++){require(daily.rewards.containsKey(i),"Falta recompensa dia "+i);refs(daily.rewards.get(i));}refs(daily.vipRewards);
        rewards.forEach((id,r)->require(!id.isBlank()&&Set.of("ITEM","COMMAND","DOLLARS","TOKENS","CRATE_FRAGMENT").contains(r.type)&&r.value!=null&&r.amount>0&&Double.isFinite(r.chance)&&r.chance>=0&&r.chance<=1&&r.weight>=0&&r.permission!=null&&r.condition!=null&&Set.of("","VIP").contains(r.condition),"Recompensa inválida: "+id));
        require(messages.keySet().containsAll(Set.of("claimed","unavailable","pending")),"Mensagens obrigatórias ausentes");
        menus.values().forEach(m->{
            require(m.pageSize>=1&&m.pageSize<=28&&m.slots!=null&&m.slots.size()>=m.pageSize&&m.slots.size()<=54&&m.icons!=null&&m.texts!=null&&m.lore!=null,"Menu inválido");
            Set<Integer> occupied=new HashSet<>();for(int slot:m.slots)require(slot>=0&&slot<54&&occupied.add(slot),"Slots de conteúdo inválidos");
            for(int slot:List.of(m.backSlot,m.profileSlot,m.pendingSlot,m.previousSlot,m.nextSlot))require(slot>=0&&slot<54&&occupied.add(slot),"Slots de controle inválidos/sobrepostos");
        });
    }
    private void refs(List<String> ids){require(ids!=null,"Recompensas ausentes");for(String id:ids)require(rewards.containsKey(id),"Recompensa desconhecida: "+id);}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    public LocalDate day(Instant now){return now.atZone(ZoneId.of(general.zone)).minusSeconds(LocalTime.parse(general.resetTime).toSecondOfDay()).toLocalDate();}
    public String week(Instant now){return day(now).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();}
    public Instant nextReset(Instant now){return day(now).plusDays(1).atTime(LocalTime.parse(general.resetTime)).atZone(ZoneId.of(general.zone)).toInstant();}
}
