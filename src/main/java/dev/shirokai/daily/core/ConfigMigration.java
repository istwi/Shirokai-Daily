package dev.shirokai.daily.core;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
/** Upgrade recognized shipped defaults only. Custom profiles and player snapshots are never overwritten. */
final class ConfigMigration {
 private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
 static void upgradeShippedDefaults(Path root)throws IOException{
  Map<Path,JsonObject> changes=new LinkedHashMap<>();
  JsonObject daily=read(root.resolve("daily.json"));
  if(daily!=null&&daily.get("days").getAsInt()==7){
   var days=daily.getAsJsonObject("rewards");boolean original=days!=null&&days.size()==7;
   if(original)for(int i=1;i<=7;i++)original&=Objects.equals(days.get(Integer.toString(i)),JSON.toJsonTree(List.of("daily_item")));
   if(original){daily.addProperty("days",30);for(int i=8;i<=30;i++)days.add(Integer.toString(i),JSON.toJsonTree(List.of("daily_item")));changes.put(root.resolve("daily.json"),daily);}
  }
  JsonObject play=read(root.resolve("playtime.json"));
  if(play!=null){
   var goals=play.getAsJsonArray("goals");
   List<Config.Goal> old=List.of(new Config.Goal("first_30m","PERMANENT",1800,List.of("playtime_item")),new Config.Goal("daily_hour","DAILY",3600,List.of("playtime_item")),new Config.Goal("weekly_6h","WEEKLY",21600,List.of("playtime_item")));
   if(Objects.equals(goals,JSON.toJsonTree(old))){play.add("goals",JSON.toJsonTree(Config.defaultPlaytime().goals()));changes.put(root.resolve("playtime.json"),play);}
  }
  JsonObject hunts=read(root.resolve("hunts.json"));
  if(hunts!=null){
   var pool=hunts.getAsJsonArray("pool");
   var old=List.of(new Config.Mission("catch_any","CAPTURE","","","EASY",5,4,List.of("hunt_item")),new Config.Mission("catch_fire","CAPTURE","","fire","MEDIUM",3,2,List.of("hunt_item")),new Config.Mission("defeat_any","DEFEAT","","","HARD",10,1,List.of("hunt_item")));
   if(Objects.equals(pool,JSON.toJsonTree(old))&&hunts.get("dailyCount").getAsInt()==3&&hunts.get("weeklyCount").getAsInt()==2){
    JsonObject next=JSON.toJsonTree(Config.defaultHunts()).getAsJsonObject();for(var e:next.entrySet())if(Set.of("dailyCount","weeklyCount","pool","hourlyCount","rotationSeconds","randomSpecies").contains(e.getKey()))hunts.add(e.getKey(),e.getValue());
    
    JsonObject rewards=read(root.resolve("rewards.json"));if(rewards!=null){for(var e:Map.of("easy",500,"medium",1000,"hard",1500).entrySet()){String key="hunt_dollars_"+e.getKey();if(!rewards.has(key))rewards.add(key,JSON.toJsonTree(new Config.Reward("DOLLARS","impactor:dollars",e.getValue(),1,0,"","")));}changes.put(root.resolve("rewards.json"),rewards);}
    changes.put(root.resolve("hunts.json"),hunts);
   }
  }
  for(String module:List.of("daily","playtime","hunts")){
   Path p=root.resolve("menus/"+module+".json");JsonObject menu=read(p);
   // Only replace the exact original layout. Administrator customizations remain untouched.
   JsonObject old=JSON.toJsonTree(new Config.Menu("✦ "+module,"minecraft:cyan_stained_glass_pane",28,49)).getAsJsonObject();old.remove("rows");
   boolean original=menu!=null&&menu.equals(old);old.addProperty("rows",6);
   if(original||menu!=null&&menu.equals(old))changes.put(p,JSON.toJsonTree(Config.defaultMenu(module)).getAsJsonObject());
  }
  for(var e:changes.entrySet()){
   Path p=e.getKey(),backup=p.resolveSibling(p.getFileName()+".pre-alpha3.bak");if(!Files.exists(backup))Files.copy(p,backup);
   Path tmp=p.resolveSibling(p.getFileName()+".upgrade.tmp");Files.writeString(tmp,JSON.toJson(e.getValue()));Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }
 }
 private static JsonObject read(Path path)throws IOException{if(!Files.exists(path))return null;try(var reader=Files.newBufferedReader(path)){return JsonParser.parseReader(reader).getAsJsonObject();}}
}
