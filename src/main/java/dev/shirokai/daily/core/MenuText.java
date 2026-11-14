package dev.shirokai.daily.core;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Player-facing descriptions, shared by menus and their regression checks. */
public final class MenuText {
    private MenuText() {}
    public static Map<String,String> status(String status,String module){
        String color=switch(status){case "DISPONÍVEL"->"&a";case "EM PROGRESSO"->"&b";case "PRÓXIMO"->"&e";default->"&8";};
        String text=switch(status){case "DISPONÍVEL"->"Disponível";case "RESGATADO"->"Resgatado";case "EM PROGRESSO"->"Em progresso";case "PRÓXIMO"->"Em breve";default->"Bloqueado";};
        String hint=switch(status){case "DISPONÍVEL"->"Clique para resgatar.";case "RESGATADO"->"Recompensa já resgatada.";case "PRÓXIMO"->"Volte após a renovação diária.";case "EM PROGRESSO"->module.equals("playtime")?"Continue jogando para completar a meta.":"Complete a caçada para resgatar.";default->"Resgate os dias anteriores.";};
        return Map.of("status_color",color,"status_text",text,"hint_color",status.equals("DISPONÍVEL")?"&a":status.equals("EM PROGRESSO")?"&7":"&8","hint",hint);
    }
    public static String dailyStatus(boolean today,int day,int previous,int next){
        if(day<=previous&&(today||day!=next))return "RESGATADO";
        if(day==next)return today?"PRÓXIMO":"DISPONÍVEL";
        return "BLOQUEADO";
    }
    public static String objective(String action,int amount,String species,String type){
        String target=species.isEmpty()?"Pokémon":species;
        if(!type.isEmpty())target+=" do tipo "+type;
        return action+" &b"+amount+" "+target;
    }
    public static String renewal(Config config,String period,Instant now){
        if(period.equals("PERMANENT"))return "&7Meta única &8• &7sem prazo";
        Instant reset=period.equals("HOURLY")?config.nextHuntReset(now):period.equals("WEEKLY")?LocalDate.parse(config.week(now)).plusWeeks(1)
            .atTime(LocalTime.parse(config.general().resetTime())).atZone(ZoneId.of(config.general().zone())).toInstant():config.nextReset(now);
        return "&7Renova em: &b"+reset.atZone(ZoneId.of(config.general().zone())).format(DateTimeFormatter.ofPattern("dd/MM HH:mm"));
    }
    public static List<String> rewardLines(Config config,List<String> ids,Set<String> permissions){
        var eligible=ids.stream().map(config.rewards()::get).filter(Objects::nonNull).filter(r->Engine.allowed(r,permissions)).toList();
        var lines=new ArrayList<String>();
        for(var reward:eligible)if(reward.weight()==0)lines.add(prize(reward)+(reward.chance()<1?" &8("+percent(reward.chance()*100)+"%)":""));
        var weighted=eligible.stream().filter(r->r.weight()>0).toList();
        long total=weighted.stream().mapToLong(Config.Reward::weight).sum();
        if(total>0){
            lines.add(weighted.stream().allMatch(r->r.chance()==1)?"&7Bônus &8• &7uma destas opções:":"&7Bônus &8• &7até uma opção:");
            for(var reward:weighted)lines.add(prize(reward)+" &8("+percent(100.0*reward.weight()/total*reward.chance())+"%)");
        }
        if(lines.isEmpty())lines.add("&8Nenhuma recompensa disponível para você.");
        return List.copyOf(lines);
    }
    public static String render(String template,Map<String,String> values){
        for(var entry:values.entrySet())template=template.replace("{"+entry.getKey()+"}",entry.getValue());
        return template;
    }
    public static List<String> renderLines(List<String> templates,Map<String,String> values){
        return templates.stream().flatMap(line->Arrays.stream(render(line,values).split("\\R",-1))).toList();
    }
    private static String percent(double value){return String.format(Locale.ROOT,value==Math.rint(value)?"%.0f":"%.1f",value);}
    private static String prize(Config.Reward reward){
        String name=switch(reward.type()){
            case "COMMAND"->"Recompensa especial";case "DOLLARS"->"Dollars";case "TOKENS"->"Tokens";case "CRATE_FRAGMENT"->"Fragmentos de caixa";
            default->switch(reward.value()){
                case "minecraft:diamond"->"Diamante";case "minecraft:emerald"->"Esmeralda";case "minecraft:gold_ingot"->"Barra de ouro";case "minecraft:golden_apple"->"Maçã dourada";
                default->{String id=reward.value();String label=id.substring(id.indexOf(':')+1).replace('_',' ');yield label.isEmpty()?"Item":Character.toUpperCase(label.charAt(0))+label.substring(1);}
            };
        };
        return "&8• &a"+reward.amount()+"× &7"+name;
    }
}
