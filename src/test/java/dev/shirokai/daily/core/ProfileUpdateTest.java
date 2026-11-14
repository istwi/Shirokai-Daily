package dev.shirokai.daily.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ProfileUpdateTest {
 @TempDir Path root;
 final UUID player=UUID.randomUUID();final Instant before=Instant.parse("2026-11-14T15:59:59Z");
 final Set<String> permissions=Set.of("shirokaidaily.daily","shirokaidaily.playtime","shirokaidaily.hunts");
 final Gson json=new GsonBuilder().setPrettyPrinting().create();
 Config config;Store store;Engine engine;
 @BeforeEach void setup()throws Exception{config=Config.load(root.resolve("config"));store=new Store(root.resolve("data"));engine=new Engine(config,store,new Random(42));catalog(engine);}
 void catalog(Engine e){e.species(java.util.stream.IntStream.range(0,12).mapToObj(i->new Engine.Species("addon:species_"+i,Set.of("grass"),false)).toList());}
 @Test void defaultsShowThirtyDaysHoursAndSmallGreyHunts(){
  assertEquals(30,config.daily().days());assertEquals(30,config.daily().rewards().size());assertEquals(30,config.menus().get("daily").pageSize());
  assertEquals(List.of(1L,6L,12L,24L,60L,100L,250L,500L,1000L),config.playtime().goals().stream().map(g->g.seconds()/3600).toList());
  assertTrue(config.playtime().goals().size()<=config.menus().get("playtime").pageSize());
  assertEquals(3,config.menus().get("hunts").rows());assertEquals(3600,config.hunts().rotationSeconds());assertEquals(3,config.hunts().hourlyCount());
  config.menus().values().forEach(m->{assertEquals("minecraft:gray_stained_glass_pane",m.icon());assertTrue(m.slots().stream().allMatch(i->i<m.rows()*9));});
 }
 @Test void thirtyDayCycleHasNoDuplicateClaim()throws Exception{
  for(int day=0;day<30;day++){Instant now=before.plusSeconds(day*86400L);assertNotNull(engine.claim(player,"daily","",permissions,now));assertNull(engine.claim(player,"daily","",permissions,now));}
  assertEquals(30,engine.view(player,before.plusSeconds(29*86400L)).daily.day);
  engine.claim(player,"daily","",permissions,before.plusSeconds(30*86400L));assertEquals(1,engine.view(player,before.plusSeconds(30*86400L)).daily.day);
 }
 @Test void savedHourlyDrawSurvivesReopenReloadAndRestart()throws Exception{
  State s=engine.view(player,before);var targets=s.hunts.quests.values().stream().map(q->q.mission.species()).toList();assertEquals(3,new HashSet<>(targets).size());
  Engine restart=new Engine(config,store,new Random(1));catalog(restart);restart.reload(Config.load(root.resolve("config")));
  assertEquals(targets,restart.view(player,before).hunts.quests.values().stream().map(q->q.mission.species()).toList());
 }
 @Test void hourBoundaryChangesTargetsWithoutRepeatWhenPoolAllows()throws Exception{
  State old=engine.view(player,before);var targets=old.hunts.quests.values().stream().map(q->q.mission.species()).toList();
  State next=engine.view(player,before.plusSeconds(1));assertNotEquals(old.hunts.hour,next.hunts.hour);assertEquals(3,next.hunts.quests.size());
  next.hunts.quests.values().forEach(q->assertFalse(targets.contains(q.mission.species())));
  assertEquals(Instant.parse("2026-11-14T16:00:00Z"),config.nextHuntReset(before));
 }
 @Test void onlyTargetCapturesCountAndDollarClaimRemainsManual()throws Exception{
  State s=engine.view(player,before);var entry=s.hunts.quests.entrySet().iterator().next();var m=entry.getValue().mission;
  engine.hunt(player,new Engine.HuntEvent("wrong","CAPTURE","addon:wrong",Set.of("grass"),false,false,true),before);assertEquals(0,engine.view(player,before).hunts.quests.get(entry.getKey()).progress);
  for(int i=0;i<m.amount();i++)engine.hunt(player,new Engine.HuntEvent("ok"+i,"CAPTURE",m.species(),Set.of("grass"),false,false,true),before);
  assertTrue(engine.pending(player,"hunts").isEmpty());assertNotNull(engine.claim(player,"hunts",entry.getKey(),permissions,before));assertNull(engine.claim(player,"hunts",entry.getKey(),permissions,before));
  var reward=engine.pending(player,"hunts").getFirst().reward();assertEquals("DOLLARS",reward.type());assertEquals("impactor:dollars",reward.value());
 }
 @Test void staleClaimExpiresButAlreadyClaimedDeliverySurvivesRotation()throws Exception{
  State s=engine.view(player,before);var entry=s.hunts.quests.entrySet().iterator().next();for(int i=0;i<entry.getValue().mission.amount();i++)engine.hunt(player,new Engine.HuntEvent("capture"+i,"CAPTURE",entry.getValue().mission.species(),Set.of("grass"),false,false,true),before);
  String key=engine.claim(player,"hunts",entry.getKey(),permissions,before);assertNotNull(key);
  engine.view(player,before.plusSeconds(1));assertNull(engine.claim(player,"hunts",entry.getKey(),permissions,before.plusSeconds(1)));assertEquals(2,engine.pending(player,"hunts").size());
 }
 @Test void noRegistryDoesNotCommitAnEmptyRotation()throws Exception{
  Engine unavailable=new Engine(config,store,new Random(9));assertEquals("",unavailable.view(player,before).hunts.hour);
  catalog(unavailable);assertEquals(3,unavailable.view(player,before).hunts.quests.size());
 }
 @Test void generationRespectsRegistryExclusionsAndDoesNotDuplicateSmallPool()throws Exception{
  engine.species(List.of(new Engine.Species("addon:normal",Set.of("grass"),false),new Engine.Species("addon:legendary",Set.of("grass"),true)));
  State s=engine.view(player,before);assertEquals(1,s.hunts.quests.size());assertEquals("addon:normal",s.hunts.quests.values().iterator().next().mission.species());
 }
 void write(String file,Object object)throws Exception{Files.writeString(root.resolve("config").resolve(file),json.toJson(object));}
 @Test void recognizedOldDefaultsUpgradeWithBackupAndWithoutTouchingPlayerData()throws Exception{
  Map<Integer,List<String>> rewards=new LinkedHashMap<>();for(int i=1;i<=7;i++)rewards.put(i,List.of("daily_item"));
  write("daily.json",new Config.Daily(7,"RESET",1,"RESTART",rewards,List.of()));
  write("playtime.json",new Config.Playtime(true,300,List.of(new Config.Goal("first_30m","PERMANENT",1800,List.of("playtime_item")),new Config.Goal("daily_hour","DAILY",3600,List.of("playtime_item")),new Config.Goal("weekly_6h","WEEKLY",21600,List.of("playtime_item")))));
  for(String module:List.of("daily","playtime","hunts")){JsonObject menu=json.toJsonTree(new Config.Menu("✦ "+module,"minecraft:cyan_stained_glass_pane",28,49)).getAsJsonObject();menu.remove("rows");write("menus/"+module+".json",menu);}
  engine.time(player,3600,3600,before);Path playerFile=root.resolve("data").resolve(player+".json");String data=Files.readString(playerFile);
  Config next=Config.load(root.resolve("config"));assertEquals(30,next.daily().days());assertEquals(9,next.playtime().goals().size());assertEquals(3,next.menus().get("hunts").rows());assertEquals(data,Files.readString(playerFile));
  Path backup=root.resolve("config/daily.json.pre-alpha5.bak");String original=Files.readString(backup);Config.load(root.resolve("config"));assertEquals(original,Files.readString(backup));
 }
 @Test void customDailyAndMenuArePreserved()throws Exception{
  Map<Integer,List<String>> rewards=new LinkedHashMap<>();for(int i=1;i<=7;i++)rewards.put(i,List.of("playtime_item"));write("daily.json",new Config.Daily(7,"PAUSE",1,"RESTART",rewards,List.of()));
  write("menus/daily.json",new Config.Menu("Meu calendário","minecraft:stone",28,49));
  Config next=Config.load(root.resolve("config"));assertEquals(7,next.daily().days());assertEquals("minecraft:stone",next.menus().get("daily").icon());assertFalse(Files.exists(root.resolve("config/daily.json.pre-alpha5.bak")));
 }
 @Test void oldHuntsUpgradeToHourlyDollarsAndKeepPendingSnapshot()throws Exception{
  Config.Hunts old=new Config.Hunts(3,2,true,false,true,Set.of(),Set.of(),List.of(new Config.Mission("catch_any","CAPTURE","","","EASY",5,4,List.of("hunt_item")),new Config.Mission("catch_fire","CAPTURE","","fire","MEDIUM",3,2,List.of("hunt_item")),new Config.Mission("defeat_any","DEFEAT","","","HARD",10,1,List.of("hunt_item"))));
  JsonObject legacy=json.toJsonTree(old).getAsJsonObject();legacy.remove("hourlyCount");legacy.remove("rotationSeconds");legacy.remove("randomSpecies");write("hunts.json",legacy);
  State state=new State();var transaction=new State.Transaction("hunts");transaction.deliveries.add(new State.Delivery("hunt_item",config.rewards().get("hunt_item")));state.transactions.put("hunts/legacy",transaction);store.save(player,state);
  Config upgraded=Config.load(root.resolve("config"));assertEquals(3,upgraded.hunts().hourlyCount());assertEquals(0,upgraded.hunts().dailyCount());assertEquals(3600,upgraded.hunts().rotationSeconds());
  upgraded.hunts().pool().forEach(m->assertEquals("DOLLARS",upgraded.rewards().get(m.rewards().getFirst()).type()));
  assertEquals("ITEM",store.load(player).transactions.get("hunts/legacy").deliveries.getFirst().reward.type());assertTrue(Files.exists(root.resolve("config/hunts.json.pre-alpha5.bak")));
 }
 @Test void maxPlaytimeGoalIsOneThousandHours()throws Exception{
  var tooLong=new Config.Playtime(true,300,List.of(new Config.Goal("too_long","PERMANENT",3_600_001,List.of("playtime_item"))));
  var bad=new Config(config.general(),config.daily(),tooLong,config.hunts(),config.rewards(),config.messages(),config.menus());assertThrows(IllegalArgumentException.class,bad::validate);
 }
 @Test void publishedAlpha4ProfilesMigrateWithoutDroppingBonusDefinitions()throws Exception{
  for(String file:List.of("playtime.json","hunts.json","menus/daily.json","menus/playtime.json","menus/hunts.json")){
   try(var input=getClass().getResourceAsStream("/migrations/alpha4/"+file)){assertNotNull(input);Files.write(root.resolve("config").resolve(file),input.readAllBytes());}
  }
  var rewards=new LinkedHashMap<>(config.rewards());rewards.keySet().removeIf(key->key.startsWith("hunt_dollars_"));write("rewards.json",rewards);
  Config next=Config.load(root.resolve("config"));assertEquals(3,next.menus().get("hunts").rows());assertEquals(9,next.playtime().goals().size());assertEquals(3,next.hunts().hourlyCount());
  assertTrue(next.playtime().goals().getFirst().rewards().contains("bonus_diamond"));assertTrue(next.hunts().pool().getFirst().rewards().contains("bonus_apple"));
  next.hunts().pool().forEach(m->m.rewards().forEach(id->assertTrue(next.rewards().containsKey(id))));assertEquals("&b{species}",next.menus().get("hunts").texts().get("hunts"));
 }
 @Test void hourlyRenewalAndHourTemplatesRetainPolishedLore(){
  assertEquals("&7Renova em: &b14/11 13:00",MenuText.renewal(config,"HOURLY",before));
  String lore=String.join("\n",MenuText.renderLines(config.menus().get("playtime").lore().get("playtime"),Map.of("progress","1h","target","6h")));
  assertTrue(lore.contains("1h"));assertTrue(lore.contains("6h"));assertFalse(lore.contains("6h min"));
 }
 @Test void customizedSmallPlaytimeMenuDoesNotReceiveTooManyMigratedGoals()throws Exception{
  for(String file:List.of("playtime.json","menus/playtime.json"))try(var input=getClass().getResourceAsStream("/migrations/alpha4/"+file)){assertNotNull(input);Files.write(root.resolve("config").resolve(file),input.readAllBytes());}
  Path path=root.resolve("config/menus/playtime.json");JsonObject menu=JsonParser.parseString(Files.readString(path)).getAsJsonObject();menu.addProperty("title","Meu menu personalizado");Files.writeString(path,json.toJson(menu));
  Config next=Config.load(root.resolve("config"));assertEquals(3,next.playtime().goals().size());assertEquals("Meu menu personalizado",next.menus().get("playtime").title());
 }
}
