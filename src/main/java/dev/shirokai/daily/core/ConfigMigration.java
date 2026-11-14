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
  if(daily!=null&&Set.of(7,30).contains(daily.get("days").getAsInt())){
   var days=daily.getAsJsonObject("rewards");int count=daily.get("days").getAsInt();boolean original=days!=null&&days.size()==count;
   if(original)for(int i=1;i<=count;i++)original&=Objects.equals(days.get(Integer.toString(i)),JSON.toJsonTree(List.of("daily_item")));
   if(original){daily.addProperty("days",30);daily.add("rewards",JSON.toJsonTree(Config.defaultDaily().rewards()));changes.put(root.resolve("daily.json"),daily);}
  }
  JsonObject play=read(root.resolve("playtime.json"));
  if(play!=null){
   var goals=play.getAsJsonArray("goals");
   List<Config.Goal> old=List.of(new Config.Goal("first_30m","PERMANENT",1800,List.of("playtime_item")),new Config.Goal("daily_hour","DAILY",3600,List.of("playtime_item")),new Config.Goal("weekly_6h","WEEKLY",21600,List.of("playtime_item")));
   if(Objects.equals(goals,JSON.toJsonTree(old))||shipped("playtime.json",goals,"goals")){play.add("goals",JSON.toJsonTree(Config.defaultPlaytime().goals()));changes.put(root.resolve("playtime.json"),play);}
  }
  JsonObject hunts=read(root.resolve("hunts.json"));
  if(hunts!=null){
   var pool=hunts.getAsJsonArray("pool");
   var old=List.of(new Config.Mission("catch_any","CAPTURE","","","EASY",5,4,List.of("hunt_item")),new Config.Mission("catch_fire","CAPTURE","","fire","MEDIUM",3,2,List.of("hunt_item")),new Config.Mission("defeat_any","DEFEAT","","","HARD",10,1,List.of("hunt_item")));
   if((Objects.equals(pool,JSON.toJsonTree(old))||shipped("hunts.json",pool,"pool"))&&hunts.get("dailyCount").getAsInt()==3&&hunts.get("weeklyCount").getAsInt()==2){
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
   JsonObject next=JSON.toJsonTree(Config.defaultMenu(module)).getAsJsonObject();
   if((original||menu!=null&&menu.equals(old)||shipped("menus/"+module+".json",menu,null))&&!next.equals(menu))changes.put(p,next);
  }
  Path playMenu=root.resolve("menus/playtime.json");
  if(changes.containsKey(root.resolve("playtime.json"))&&!changes.containsKey(playMenu)){
   JsonObject custom=read(playMenu);
   if(custom!=null&&custom.get("pageSize").getAsInt()<Config.defaultPlaytime().goals().size())changes.remove(root.resolve("playtime.json"));
  }
  if(changes.containsKey(root.resolve("daily.json"))||changes.containsKey(root.resolve("playtime.json"))||changes.containsKey(root.resolve("hunts.json"))){
   Path p=root.resolve("rewards.json");JsonObject rewards=changes.containsKey(p)?changes.get(p):read(p);
   if(rewards!=null){
    var bonuses=Map.of("bonus_emerald",new Config.Reward("ITEM","minecraft:emerald",3,1,60,"",""),"bonus_diamond",new Config.Reward("ITEM","minecraft:diamond",2,1,30,"",""),"bonus_apple",new Config.Reward("ITEM","minecraft:golden_apple",1,1,10,"",""));
    bonuses.forEach((key,value)->{if(!rewards.has(key))rewards.add(key,JSON.toJsonTree(value));});changes.put(p,rewards);
   }
  }
  // Add reward definitions before profiles referencing them; interruption cannot leave missing IDs.
  List<Map.Entry<Path,JsonObject>> ordered=new ArrayList<>(changes.entrySet());ordered.sort(Comparator.comparingInt(e->e.getKey().getFileName().toString().equals("rewards.json")?0:1));
  for(var e:ordered){
   Path p=e.getKey(),backup=p.resolveSibling(p.getFileName()+".pre-alpha6.bak");if(!Files.exists(backup))Files.copy(p,backup);
   Path tmp=p.resolveSibling(p.getFileName()+".upgrade.tmp");Files.writeString(tmp,JSON.toJson(e.getValue()));Files.move(tmp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }
 }
 private static boolean shipped(String file,JsonElement actual,String field)throws IOException{
  if(actual==null)return false;
  for(String version:List.of("alpha3","alpha4","alpha5"))try(var in=ConfigMigration.class.getResourceAsStream("/migrations/"+version+"/"+file)){
   if(in==null)continue;
   try(var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){
    JsonObject expected=JsonParser.parseReader(reader).getAsJsonObject();if(actual.equals(field==null?expected:expected.get(field)))return true;
   }
  }return false;
 }
 private static JsonObject read(Path path)throws IOException{if(!Files.exists(path))return null;try(var reader=Files.newBufferedReader(path)){return JsonParser.parseReader(reader).getAsJsonObject();}}
}
