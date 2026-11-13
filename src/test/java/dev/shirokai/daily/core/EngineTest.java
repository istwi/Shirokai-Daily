package dev.shirokai.daily.core;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class EngineTest {
 @TempDir Path root; Config config;Store store;Engine engine; UUID player=UUID.randomUUID();
 Instant now=Instant.parse("2026-11-13T15:00:00Z");Set<String> permissions=Set.of("shirokaidaily.daily","shirokaidaily.playtime","shirokaidaily.hunts");
 @BeforeEach void setup()throws Exception{config=Config.load(root.resolve("config"));store=new Store(root.resolve("data"));engine=new Engine(config,store,new Random(42));}
 Config withDaily(String policy,int grace,String completion){return new Config(config.general(),new Config.Daily(7,policy,grace,completion,config.daily().rewards(),config.daily().vipRewards()),config.playtime(),config.hunts(),config.rewards(),config.messages(),config.menus());}
 @Test void dailyClaimIsUniqueAndSurvivesRestart()throws Exception{
  String key=engine.claim(player,"daily","",permissions,now);assertNotNull(key);assertNull(engine.claim(player,"daily","",permissions,now));
  engine=new Engine(config,store,new Random(3));assertNull(engine.claim(player,"daily","",permissions,now));assertEquals(1,engine.view(player,now).daily.streak);assertEquals(1,engine.pending(player,"daily").size());
 }
 @Test void resetUsesSaoPauloCalendarNot24Hours()throws Exception{
  Instant before=Instant.parse("2026-11-14T02:59:59Z"),after=before.plusSeconds(1);
  assertNotNull(engine.claim(player,"daily","",permissions,before));assertNotNull(engine.claim(player,"daily","",permissions,after));assertEquals(2,engine.view(player,after).daily.streak);
 }
 @Test void resetStreakAfterMiss()throws Exception{engine.claim(player,"daily","",permissions,now);engine.claim(player,"daily","",permissions,now.plusSeconds(3*86400));var s=engine.view(player,now.plusSeconds(3*86400));assertEquals(1,s.daily.streak);assertEquals(1,s.daily.resets);}
 @Test void pausePreservesStreak()throws Exception{engine.reload(withDaily("PAUSE",0,"RESTART"));engine.claim(player,"daily","",permissions,now);engine.claim(player,"daily","",permissions,now.plusSeconds(3*86400));assertEquals(2,engine.view(player,now.plusSeconds(3*86400)).daily.streak);}
 @Test void graceAllowsOneMissingDay()throws Exception{engine.reload(withDaily("GRACE",1,"RESTART"));engine.claim(player,"daily","",permissions,now);engine.claim(player,"daily","",permissions,now.plusSeconds(2*86400));assertEquals(2,engine.view(player,now.plusSeconds(2*86400)).daily.streak);engine.claim(player,"daily","",permissions,now.plusSeconds(5*86400));assertEquals(1,engine.view(player,now.plusSeconds(5*86400)).daily.streak);}
 @Test void cyclesRestartAndStop()throws Exception{
  for(int i=0;i<8;i++)assertNotNull(engine.claim(player,"daily","",permissions,now.plusSeconds(i*86400L)));
  assertEquals(1,engine.view(player,now.plusSeconds(7*86400)).daily.day);
  engine.reload(withDaily("RESET",1,"STOP"));UUID other=UUID.randomUUID();for(int i=0;i<7;i++)assertNotNull(engine.claim(other,"daily","",permissions,now.plusSeconds(i*86400L)));assertNull(engine.claim(other,"daily","",permissions,now.plusSeconds(7*86400L)));
 }
 @Test void vipEvaluatedAtClaim()throws Exception{
  var d=config.daily();engine.reload(new Config(config.general(),new Config.Daily(d.days(),d.streakPolicy(),d.graceDays(),d.completion(),d.rewards(),List.of("daily_item")),config.playtime(),config.hunts(),config.rewards(),config.messages(),config.menus()));
  engine.claim(player,"daily","",permissions,now);assertEquals(1,engine.pending(player,"daily").size());
  Set<String> vip=new HashSet<>(permissions);vip.add("shirokaidaily.vip");UUID other=UUID.randomUUID();engine.claim(other,"daily","",vip,now);assertEquals(2,engine.pending(other,"daily").size());
 }
 @Test void playtimeHasIndependentClaimsAndPeriods()throws Exception{
  engine.time(player,4000,3700,now);assertNotNull(engine.claim(player,"playtime","first_30m",permissions,now));assertNull(engine.claim(player,"playtime","first_30m",permissions,now));assertNotNull(engine.claim(player,"playtime","daily_hour",permissions,now));
  var next=engine.view(player,now.plusSeconds(86400));assertEquals(4000,next.playtime.total);assertEquals(3700,next.playtime.eligible);assertEquals(0,next.playtime.daily);assertEquals(3700,next.playtime.weekly);
  engine.time(player,3700,3700,now.plusSeconds(86400));assertNotNull(engine.claim(player,"playtime","daily_hour",permissions,now.plusSeconds(86400)));assertNull(engine.claim(player,"playtime","first_30m",permissions,now.plusSeconds(86400)));
 }
 @Test void sampledTimeIsSplitAtReset()throws Exception{Instant end=Instant.parse("2026-11-14T03:00:10Z");engine.time(player,20,20,end);var s=engine.view(player,end);assertEquals(20,s.playtime.total);assertEquals(10,s.playtime.daily);}
 @Test void weeklyResetOnMonday()throws Exception{engine.time(player,4000,3700,now);assertEquals(0,engine.view(player,Instant.parse("2026-11-16T04:00:00Z")).playtime.weekly);}
 @Test void afkTimeExcludedFromEligible()throws Exception{engine.time(player,1800,0,now);assertNull(engine.claim(player,"playtime","first_30m",permissions,now));assertEquals(1800,engine.view(player,now).playtime.total);}
 Engine.HuntEvent event(String id,String action,boolean confirmed,boolean owned,boolean legendary){return new Engine.HuntEvent(id,action,"cobblemon:charmander",Set.of("fire"),legendary,owned,confirmed);}
 @Test void huntsRejectCancelledOwnedLegendaryAndDuplicates()throws Exception{
  for(int i=0;i<10;i++){engine.hunt(player,event("cancel"+i,"CAPTURE",false,false,false),now);engine.hunt(player,event("own"+i,"CAPTURE",true,true,false),now);engine.hunt(player,event("legend"+i,"CAPTURE",true,false,true),now);}
  engine.hunt(player,event("one","CAPTURE",true,false,false),now);engine.hunt(player,event("one","CAPTURE",true,false,false),now);
  var state=engine.view(player,now);assertEquals(5,state.hunts.quests.size());for(var q:state.hunts.quests.values())assertEquals(q.mission.action().equals("CAPTURE")?1:0,q.progress);
 }
 @Test void huntsProgressAndClaimIsUnique()throws Exception{
  for(int i=0;i<12;i++){engine.hunt(player,event("c"+i,"CAPTURE",true,false,false),now);engine.hunt(player,event("d"+i,"DEFEAT",true,false,false),now);}
  var s=engine.view(player,now);for(var entry:s.hunts.quests.entrySet()){assertEquals(entry.getValue().mission.amount(),entry.getValue().progress);assertNotNull(engine.claim(player,"hunts",entry.getKey(),permissions,now));assertNull(engine.claim(player,"hunts",entry.getKey(),permissions,now));}
 }
 @Test void missionsResetAndDurableDedupSurvivesReset()throws Exception{engine.hunt(player,event("one","CAPTURE",true,false,false),now);Instant tomorrow=now.plusSeconds(86400);engine.hunt(player,event("one","CAPTURE",true,false,false),tomorrow);for(var q:engine.view(player,tomorrow).hunts.quests.values())if(q.period.equals("DAILY"))assertEquals(0,q.progress);}
 @Test void pendingDeliveryFreezesRewardAndNeverRetriesExecuting()throws Exception{
  engine.claim(player,"daily","",permissions,now);var p=engine.pending(player,"daily").getFirst();assertTrue(engine.begin(player,p));assertFalse(engine.begin(player,p));
  engine=new Engine(config,store,new Random(1));assertTrue(engine.pending(player,"daily").isEmpty());assertEquals("EXECUTING",store.load(player).transactions.get(p.transaction()).deliveries.getFirst().status);
  engine.finish(player,p,"PENDING");assertEquals(p.reward(),engine.pending(player,"daily").getFirst().reward());engine.begin(player,p);engine.finish(player,p,"DONE");assertTrue(engine.pending(player,"daily").isEmpty());
 }
 @Test void missingPermissionDoesNotConsumeClaim()throws Exception{assertNull(engine.claim(player,"daily","",Set.of(),now));assertNotNull(engine.claim(player,"daily","",permissions,now));}
 @Test void reloadDoesNotEraseStateAndBadJsonFails()throws Exception{
  engine.claim(player,"daily","",permissions,now);engine.reload(Config.load(root.resolve("config")));assertNull(engine.claim(player,"daily","",permissions,now));Files.writeString(root.resolve("config/daily.json"),"{bad}");assertThrows(Exception.class,()->Config.load(root.resolve("config")));assertEquals(1,engine.view(player,now).daily.day);
 }
 @Test void corruptDataIsNotOverwritten()throws Exception{Path file=root.resolve("data/"+player+".json");Files.writeString(file,"{broken}");assertThrows(Exception.class,()->engine.view(player,now));assertEquals("{broken}",Files.readString(file));}
 @Test void adminDailyResetDoesNotDuplicateSameDay()throws Exception{engine.claim(player,"daily","",permissions,now);engine.admin(player,"daily-reset",0,now);assertNull(engine.claim(player,"daily","",permissions,now));}
 @Test void disabledModulesKeepData()throws Exception{engine.claim(player,"daily","",permissions,now);engine.reload(new Config(new Config.General("America/Sao_Paulo","00:00",false,false,false),config.daily(),config.playtime(),config.hunts(),config.rewards(),config.messages(),config.menus()));assertNull(engine.claim(player,"daily","",permissions,now.plusSeconds(86400)));assertEquals(1,engine.view(player,now).daily.day);}

 Config withHunts(Config.Hunts h){return new Config(config.general(),config.daily(),config.playtime(),h,config.rewards(),config.messages(),config.menus());}
 @Test void whitelistAndBlacklistFilterBeforeProgress()throws Exception{
  var h=config.hunts();engine.reload(withHunts(new Config.Hunts(1,0,false,false,false,Set.of("cobblemon:bulbasaur"),Set.of(),h.pool())));
  engine.hunt(player,event("not-whitelisted","CAPTURE",true,false,false),now);assertEquals(0,engine.view(player,now).hunts.quests.values().iterator().next().progress);
  engine.reload(withHunts(new Config.Hunts(1,0,false,false,false,Set.of(),Set.of("cobblemon:charmander"),h.pool())));
  engine.hunt(player,event("blacklisted","CAPTURE",true,false,false),now);assertEquals(0,engine.view(player,now).hunts.quests.values().iterator().next().progress);
 }
 @Test void speciesAndTypeMissionsRequireMatchingEvent()throws Exception{
  var pool=List.of(new Config.Mission("specific","CAPTURE","cobblemon:bulbasaur","grass","EASY",1,1,List.of("hunt_item")));
  engine.reload(withHunts(new Config.Hunts(1,0,false,false,true,Set.of(),Set.of(),pool)));
  engine.hunt(player,event("fire","CAPTURE",true,false,false),now);assertEquals(0,engine.view(player,now).hunts.quests.values().iterator().next().progress);
  engine.hunt(player,new Engine.HuntEvent("grass","CAPTURE","cobblemon:bulbasaur",Set.of("grass","poison"),false,false,true),now);
  assertEquals(1,engine.view(player,now).hunts.quests.values().iterator().next().progress);
 }
 @Test void completedMissionCanBeRetainedAfterReset()throws Exception{
  var pool=List.of(new Config.Mission("once","CAPTURE","","","EASY",1,1,List.of("hunt_item")));
  engine.reload(withHunts(new Config.Hunts(1,0,false,true,true,Set.of(),Set.of(),pool)));
  engine.hunt(player,event("one","CAPTURE",true,false,false),now);String old=engine.view(player,now).hunts.quests.keySet().iterator().next();
  Instant next=now.plusSeconds(86400);assertEquals(2,engine.view(player,next).hunts.quests.size());assertNotNull(engine.claim(player,"hunts",old,permissions,next));
 }
 @Test void completedMissionExpiresWhenRetentionDisabled()throws Exception{
  var pool=List.of(new Config.Mission("once","CAPTURE","","","EASY",1,1,List.of("hunt_item")));
  engine.reload(withHunts(new Config.Hunts(1,0,false,false,true,Set.of(),Set.of(),pool)));
  engine.hunt(player,event("one","CAPTURE",true,false,false),now);String old=engine.view(player,now).hunts.quests.keySet().iterator().next();
  assertNull(engine.claim(player,"hunts",old,permissions,now.plusSeconds(86400)));
 }
 @Test void fixedRewardsAndWeightedDrawAreFrozenAcrossReload()throws Exception{
  Map<String,Config.Reward> rewards=new HashMap<>(config.rewards());
  rewards.put("rare",new Config.Reward("ITEM","minecraft:diamond",64,1,1,"",""));
  rewards.put("common",new Config.Reward("ITEM","minecraft:emerald",1,1,10,"",""));
  Map<Integer,List<String>> days=new HashMap<>();for(int day=1;day<=7;day++)days.put(day,List.of("daily_item","rare","common"));
  var d=new Config.Daily(7,"RESET",1,"RESTART",days,List.of());engine.reload(new Config(config.general(),d,config.playtime(),config.hunts(),rewards,config.messages(),config.menus()));
  engine.claim(player,"daily","",permissions,now);var before=engine.pending(player,"daily");assertEquals(2,before.size());assertEquals("minecraft:diamond",before.getFirst().reward().value());
  engine.reload(config);assertEquals(before,engine.pending(player,"daily"));
 }
 @Test void rewardPermissionsAndConditionsExcludeUnauthorizedDraws()throws Exception{
  assertFalse(Engine.allowed(new Config.Reward("ITEM","minecraft:stone",1,1,0,"permission.missing",""),permissions));
  assertFalse(Engine.allowed(new Config.Reward("ITEM","minecraft:stone",1,1,0,"","VIP"),permissions));
  assertTrue(Engine.allowed(config.rewards().get("daily_item"),permissions));
 }
 @Test void adminReconciliationRequiresUncertainDelivery()throws Exception{
  engine.claim(player,"daily","",permissions,now);var p=engine.pending(player,"daily").getFirst();
  assertThrows(IllegalArgumentException.class,()->engine.reconcile(player,p.transaction(),p.index(),"DONE"));
  engine.begin(player,p);engine.reconcile(player,p.transaction(),p.index(),"PENDING");assertEquals(1,engine.pending(player,"daily").size());
  engine.begin(player,p);engine.reconcile(player,p.transaction(),p.index(),"DONE");assertTrue(engine.pending(player,"daily").isEmpty());
  assertThrows(IllegalArgumentException.class,()->engine.reconcile(player,p.transaction(),p.index(),"PENDING"));
 }
}
