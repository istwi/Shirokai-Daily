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
                        boolean excludeLegendary, Set<String> whitelist, Set<String> blacklist, List<Mission> pool,
                        int hourlyCount, int rotationSeconds, boolean randomSpecies) {
        public Hunts(int dailyCount,int weeklyCount,boolean random,boolean retainCompleted,boolean excludeLegendary,Set<String> whitelist,Set<String> blacklist,List<Mission> pool){
            this(dailyCount,weeklyCount,random,retainCompleted,excludeLegendary,whitelist,blacklist,pool,0,0,false);
        }
    }
    public record Reward(String type, String value, int amount, double chance, int weight,
                         String permission, String condition) {}
    public record Menu(String title, String icon, int pageSize, int backSlot,
                       List<Integer> slots,int profileSlot,int pendingSlot,int previousSlot,int nextSlot,
                       Map<String,String> icons,Map<String,String> texts,Map<String,List<String>> lore,int rows) {
        public Menu(String title,String icon,int pageSize,int backSlot){
            this(title,icon,pageSize,backSlot,defaultSlots(),4,48,45,53,Map.of(),Map.of(),Map.of(),6);
        }
        private static List<Integer> defaultSlots(){List<Integer> result=new ArrayList<>();for(int r=1;r<=4;r++)for(int c=1;c<=7;c++)result.add(r*9+c);return List.copyOf(result);}
    }
    public static Daily defaultDaily(){
        Map<Integer,List<String>> days=new LinkedHashMap<>();for(int i=1;i<=30;i++)days.put(i,List.of("daily_item","bonus_emerald","bonus_diamond","bonus_apple"));
        return new Daily(30,"RESET",1,"RESTART",days,List.of());
    }
    public static Playtime defaultPlaytime(){
        return new Playtime(true,300,java.util.stream.LongStream.of(1,3,6,12,24,36,48,60,80,100,150,200,250,350,500,650,800,1000,1250,1500,1750,2000,2500,3000,3500,4000,4500,5000)
                .mapToObj(hours->new Goal("hours_"+hours,"PERMANENT",hours*3600,List.of("playtime_item","bonus_emerald","bonus_diamond","bonus_apple"))).toList());
    }
    public static Hunts defaultHunts(){return new Hunts(0,0,true,false,true,Set.of(),Set.of(),List.of(
        new Mission("capture_easy","CAPTURE","","","EASY",1,4,List.of("hunt_dollars_easy","bonus_emerald","bonus_diamond","bonus_apple")),
        new Mission("capture_medium","CAPTURE","","","MEDIUM",2,2,List.of("hunt_dollars_medium","bonus_emerald","bonus_diamond","bonus_apple")),
        new Mission("capture_hard","CAPTURE","","","HARD",3,1,List.of("hunt_dollars_hard","bonus_emerald","bonus_diamond","bonus_apple"))),3,3600,true);}
    private static Menu layoutMenu(String module){
        if(module.equals("hunts"))return new Menu("✦ Hunts","minecraft:gray_stained_glass_pane",7,22,List.of(11,13,15,10,12,14,16),4,23,18,26,Map.of(),Map.of(),Map.of(),3);
        if(module.equals("daily")){
            List<Integer> slots=new ArrayList<>();for(int i=9;i<=35;i++)slots.add(i);slots.addAll(List.of(39,40,41));
            return new Menu("✦ Daily","minecraft:gray_stained_glass_pane",30,49,slots,4,48,45,53,Map.of(),Map.of(),Map.of(),6);
        }
        return new Menu("✦ Playtime","minecraft:gray_stained_glass_pane",28,49);
    }
    public static Menu defaultMenu(String module){
        Menu base=layoutMenu(module);
        var texts=Map.of("daily","&bDia {day}","playtime","&b{goal}","hunts","&b{species}","profile","&b{player}","pending","&6Entregas pendentes","previous","&bPágina anterior","next","&bPróxima página");
        var lore=Map.of(
            "daily",List.of("&8Recompensa diária","","&7Sequência: &b{streak} dias","&7Situação: {status_color}{status_text}","","&6Recompensas","{reward_lines}","","&7Próxima renovação: &b{reset}","","{hint_color}{hint}"),
            "playtime",List.of("&8Tempo de jogo • {period}","","&7Progresso: &b{progress} &8/ &b{target}","&7Situação: {status_color}{status_text}","","&6Recompensas","{reward_lines}","","{renewal}","","{hint_color}{hint}"),
            "hunts",List.of("&8Caçada • {period}","","&7{objective}","&7Dificuldade: &b{difficulty}","&7Progresso: &b{progress} &8/ &b{target}","&7Situação: {status_color}{status_text}","","&6Recompensas","{reward_lines}","","{renewal}","","{hint_color}{hint}"),
            "profile",List.of("&8Shirokai Daily","","&7Página &b{page} &8/ &b{pages}","&7Renovação diária: &b{reset}"),
            "pending",List.of("&7Receba os prêmios que ficaram pendentes.","&7Libere espaço no inventário se necessário.","","&eClique para tentar receber."),
            "previous",List.of("&7Ver a página anterior."),"next",List.of("&7Ver a próxima página."));
        return new Menu(base.title,base.icon,base.pageSize,base.backSlot,base.slots,base.profileSlot,base.pendingSlot,base.previousSlot,base.nextSlot,base.icons,texts,lore,base.rows);
    }
    /** Upgrade only exact shipped templates in memory; never overwrite administrator files. */
    private static Menu upgradeDefaultMenu(String module,Menu menu){
        var legacyTexts=Map.of("daily","&b&lDIA {day} &8• &f{status}","playtime","&b&l{goal} &8• &f{status}","hunts","&b&l{action} &f{species}","profile","&b&l{player}","pending","&6&lEntregas pendentes");
        var legacyLore=Map.of(
            "daily",List.of("&8RECOMPENSA DIÁRIA","","&7Sequência: &f{streak} dias &8• &7Reinícios: &f{resets}","","&6Prêmios &8› &f{rewards}","&7Próximo reset: &f{reset}","","&e{hint}"),
            "playtime",List.of("&8TEMPO DE JOGO • {period}","","&7Progresso: &b{progress} &7/ &f{target} min","&7Tempo elegível: &f{eligible} min","","&6Prêmios &8› &f{rewards}","","&e{hint}"),
            "hunts",List.of("&8CAÇADA • {period}","","&7Objetivo: &f{action} {target}× {species}","&7Tipo: &f{type} &8• &7Dificuldade: &f{difficulty}","","&7Progresso: &b{progress} &7/ &f{target}","&7Estado: &f{status}","","&6Prêmios &8› &f{rewards}","","&e{hint}"),
            "profile",List.of("&8SHIROKAI DAILY","","&7Página &f{page} &7de &f{pages}","&7Próximo reset: &f{reset}"),
            "pending",List.of("&7Tem prêmios aguardando entrega?","","&e➜ Clique para tentar receber."));
        Menu current=defaultMenu(module);
        var texts=new HashMap<>(menu.texts());var lore=new HashMap<>(menu.lore());
        legacyTexts.forEach((key,value)->{if(value.equals(texts.get(key)))texts.put(key,current.texts().get(key));});
        legacyLore.forEach((key,value)->{if(value.equals(lore.get(key)))lore.put(key,current.lore().get(key));});
        return new Menu(menu.title(),menu.icon(),menu.pageSize(),menu.backSlot(),menu.slots(),menu.profileSlot(),menu.pendingSlot(),menu.previousSlot(),menu.nextSlot(),Map.copyOf(menu.icons()),Map.copyOf(texts),Map.copyOf(lore),menu.rows());
    }
    private static Menu normalize(Menu m){return m.rows==0?new Menu(m.title,m.icon,m.pageSize,m.backSlot,m.slots,m.profileSlot,m.pendingSlot,m.previousSlot,m.nextSlot,m.icons,m.texts,m.lore,6):m;}
    public String huntEpoch(Instant now){return Long.toString(Math.floorDiv(now.getEpochSecond(),hunts.rotationSeconds));}
    public Instant nextHuntReset(Instant now){return hunts.hourlyCount>0?Instant.ofEpochSecond((Math.floorDiv(now.getEpochSecond(),hunts.rotationSeconds)+1)*hunts.rotationSeconds):nextReset(now);}
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config load(Path path) throws IOException {
        Files.createDirectories(path.resolve("menus"));
        ConfigMigration.upgradeShippedDefaults(path);
        var rewards = new LinkedHashMap<String,Reward>();
        rewards.put("daily_item", new Reward("ITEM","minecraft:diamond",1,1,0,"",""));
        rewards.put("playtime_item",new Reward("ITEM","minecraft:emerald",2,1,0,"",""));
        rewards.put("hunt_item",new Reward("ITEM","minecraft:gold_ingot",4,1,0,"",""));
        rewards.put("bonus_emerald",new Reward("ITEM","minecraft:emerald",3,1,60,"",""));
        rewards.put("bonus_diamond",new Reward("ITEM","minecraft:diamond",2,1,30,"",""));
        rewards.put("bonus_apple",new Reward("ITEM","minecraft:golden_apple",1,1,10,"",""));
        rewards.put("hunt_dollars_easy",new Reward("DOLLARS","impactor:dollars",500,1,0,"",""));
        rewards.put("hunt_dollars_medium",new Reward("DOLLARS","impactor:dollars",1000,1,0,"",""));
        rewards.put("hunt_dollars_hard",new Reward("DOLLARS","impactor:dollars",1500,1,0,"",""));
        Map<String,Menu> menus=new LinkedHashMap<>();
        for(String m:List.of("daily","playtime","hunts")) menus.put(m,upgradeDefaultMenu(m,normalize(read(path.resolve("menus/"+m+".json"),Menu.class,defaultMenu(m)))));
        Config c=new Config(
            read(path.resolve("config.json"),General.class,new General("America/Sao_Paulo","00:00",true,true,true)),
            read(path.resolve("daily.json"),Daily.class,defaultDaily()),
            read(path.resolve("playtime.json"),Playtime.class,defaultPlaytime()),
            read(path.resolve("hunts.json"),Hunts.class,defaultHunts()),
            Map.copyOf(readMap(path.resolve("rewards.json"),Reward.class,rewards)),
            Map.copyOf(readMap(path.resolve("messages.json"),String.class,Map.of("claimed","Recompensa processada; confira as pendências.","unavailable","Indisponível ou já resgatado.","pending","Entrega pendente; tente novamente neste menu."))),Map.copyOf(menus));
        c.validate(); return c;
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
        require(hunts.hourlyCount>=0&&hunts.hourlyCount<=7&&(hunts.hourlyCount==0||hunts.rotationSeconds>=60),"Rotation/count de Hunts inválidos");
        require(playtime.goals.size()<=menus.get("playtime").pageSize,"Metas de Playtime devem caber em uma página");
        var ids=new HashSet<String>();
        for(Goal g:playtime.goals){require(ids.add(g.id)&&!g.id.isBlank()&&g.seconds>0&&g.seconds<=18_000_000&&Set.of("PERMANENT","DAILY","WEEKLY").contains(g.period),"Meta inválida/duplicada");refs(g.rewards);}
        ids.clear();
        for(Mission m:hunts.pool){require(ids.add(m.id)&&!m.id.isBlank()&&m.amount>0&&m.weight>0&&Set.of("CAPTURE","DEFEAT").contains(m.action)&&Set.of("EASY","MEDIUM","HARD","VERY_HARD","EXTREME").contains(m.difficulty),"Missão inválida/duplicada");refs(m.rewards);}
        require(hunts.dailyCount+hunts.weeklyCount+hunts.hourlyCount==0||!hunts.pool.isEmpty(),"Pool vazio");
        for(int i=1;i<=daily.days;i++){require(daily.rewards.containsKey(i),"Falta recompensa dia "+i);refs(daily.rewards.get(i));}refs(daily.vipRewards);
        rewards.forEach((id,r)->require(!id.isBlank()&&Set.of("ITEM","COMMAND","DOLLARS","TOKENS","CRATE_FRAGMENT","CRATE_KEY","RANDOM_LEGENDARY_SHINY_6IV").contains(r.type)&&r.value!=null&&r.amount>0&&Double.isFinite(r.chance)&&r.chance>=0&&r.chance<=1&&r.weight>=0&&r.permission!=null&&r.condition!=null&&Set.of("","VIP").contains(r.condition),"Recompensa inválida: "+id));
        rewards.forEach((id,r)->{
            if(r.type.equals("CRATE_KEY"))require(!r.value.isBlank(),"ID de caixa ausente: "+id);
            if(r.type.equals("RANDOM_LEGENDARY_SHINY_6IV"))require(r.amount==1&&r.value.equals("legendary"),"Lendário aleatório: use amount=1, value=legendary: "+id);
        });
        require(messages.keySet().containsAll(Set.of("claimed","unavailable","pending")),"Mensagens obrigatórias ausentes");
        menus.values().forEach(m->{
            require(m.pageSize>=1&&m.pageSize<=45&&m.slots!=null&&m.slots.size()>=m.pageSize&&m.slots.size()<=54&&m.icons!=null&&m.texts!=null&&m.lore!=null,"Menu inválido");
            require(m.rows>=1&&m.rows<=6,"Linhas de menu inválidas");
            Set<Integer> occupied=new HashSet<>();for(int slot:m.slots)require(slot>=0&&slot<m.rows*9&&occupied.add(slot),"Slots de conteúdo inválidos");
            for(int slot:List.of(m.backSlot,m.profileSlot,m.pendingSlot,m.previousSlot,m.nextSlot))require(slot>=0&&slot<m.rows*9&&occupied.add(slot),"Slots de controle inválidos/sobrepostos");
        });
    }
    private void refs(List<String> ids){require(ids!=null,"Recompensas ausentes");for(String id:ids)require(rewards.containsKey(id),"Recompensa desconhecida: "+id);}
    private static void require(boolean value,String message){if(!value)throw new IllegalArgumentException(message);}
    public LocalDate day(Instant now){return now.atZone(ZoneId.of(general.zone)).minusSeconds(LocalTime.parse(general.resetTime).toSecondOfDay()).toLocalDate();}
    public String week(Instant now){return day(now).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();}
    public Instant nextReset(Instant now){return day(now).plusDays(1).atTime(LocalTime.parse(general.resetTime)).atZone(ZoneId.of(general.zone)).toInstant();}
}
