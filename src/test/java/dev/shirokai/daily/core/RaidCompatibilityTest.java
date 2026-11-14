package dev.shirokai.daily.core;
import dev.shirokai.daily.core.integration.*;
import dev.shirokai.daily.core.fixtures.PublicRaidApiFixture;
import dev.shirokai.daily.core.fixtures.PublicRaidApiFixture.Actor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
class RaidCompatibilityTest {
 @TempDir Path root;Config config;Store store;Engine engine;RaidBattleGate<Actor> gate;
 PublicRaidPredicates<Actor> raids;final AtomicLong clock=new AtomicLong();final List<String> warnings=new ArrayList<>();
 final EventIsolation isolated=new EventIsolation(clock::get,(message,error)->warnings.add(message));
 final Instant now=Instant.parse("2026-11-14T15:00:00Z");final UUID player=UUID.randomUUID();
 final Set<String> permissions=Set.of("shirokaidaily.hunts");
 @BeforeEach void setup()throws Exception{
  Config base=Config.load(root.resolve("config"));
  var hunts=new Config.Hunts(2,0,false,false,false,Set.of(),Set.of(),List.of(new Config.Mission("wild_defeat","DEFEAT","","","EASY",1,1,List.of("hunt_item")),new Config.Mission("wild_capture","CAPTURE","","","EASY",1,1,List.of("hunt_item"))));
  config=new Config(base.general(),base.daily(),base.playtime(),hunts,base.rewards(),base.messages(),base.menus());store=new Store(root.resolve("data"));engine=new Engine(config,store,new Random(42));
  raids=new PublicRaidPredicates<>(Actor.class,PublicRaidApiFixture.CombatApi.class,PublicRaidApiFixture.CaptureApi.class);gate=gate(engine);
 }
 RaidBattleGate<Actor> gate(Engine target){return new RaidBattleGate<>((id,pokemon)->{try{target.excludeRaid(id,pokemon);}catch(Exception e){throw new IllegalStateException(e);}});}
 boolean allow(UUID battle,List<Actor> actors){return gate.allow(battle,actors,a->new RaidBattleGate.Evidence(raids.test(a),Set.of(a.pokemon)),a->Set.of(a.pokemon));}
 Engine.HuntEvent event(UUID pokemon,UUID battle,String action,Engine.HuntOrigin origin){return new Engine.HuntEvent("pokemon/"+pokemon,action,"cobblemon:bulbasaur",Set.of("grass"),false,false,true,battle,origin);}
 void victory(UUID battle,List<Actor> actors,Set<UUID> players){
  isolated.run("victory",()->{
   if(!allow(battle,actors))return;
   // Callers translate public WILD actor outcomes only, as the typed Cobblemon adapter does.
   for(Actor a:actors)if(a.getType().equals("WILD"))for(UUID id:players)try{engine.hunt(id,event(a.pokemon,battle,"DEFEAT",Engine.HuntOrigin.NORMAL),now);}catch(Exception e){throw new IllegalStateException(e);}
  });
 }
 int progress(UUID id,String action)throws Exception{return engine.view(id,now).hunts.quests.values().stream().filter(q->q.mission.action().equals(action)).mapToInt(q->q.progress).sum();}
 @Test void normalWildVictoryStillCountsAndCanBeClaimed()throws Exception{
  var wild=PublicRaidApiFixture.actor("wild");victory(UUID.randomUUID(),List.of(wild),Set.of(player));assertEquals(1,progress(player,"DEFEAT"));
  String key=engine.view(player,now).hunts.quests.entrySet().stream().filter(e->e.getValue().mission.action().equals("DEFEAT")).findFirst().orElseThrow().getKey();assertNotNull(engine.claim(player,"hunts",key,permissions,now));assertEquals(1,engine.pending(player,"hunts").size());
 }
 @Test void raidVictoryAndBossVictoryCannotGrantProgressOrRewards()throws Exception{
  victory(UUID.randomUUID(),List.of(PublicRaidApiFixture.actor("raid")),Set.of(player));assertEquals(0,progress(player,"DEFEAT"));
  for(String key:engine.view(player,now).hunts.quests.keySet())assertNull(engine.claim(player,"hunts",key,permissions,now));assertTrue(engine.pending(player,"hunts").isEmpty());
 }
 @Test void aRaidActorAnywhereInTheBattleExcludesAllParticipants()throws Exception{
  Set<UUID> players=new LinkedHashSet<>();for(int i=0;i<100;i++)players.add(UUID.randomUUID());
  victory(UUID.randomUUID(),List.of(PublicRaidApiFixture.actor("wild"),PublicRaidApiFixture.actor("raid")),players);
  for(UUID id:players){assertEquals(0,progress(id,"DEFEAT"));assertTrue(engine.pending(id,"hunts").isEmpty());}
 }
 @Test void raidCaptureBattleAndCaptureIdentityAreExcluded()throws Exception{
  Actor target=PublicRaidApiFixture.actor("raid-capture");UUID battle=UUID.randomUUID();assertFalse(allow(battle,List.of(target)));
  engine.hunt(player,event(target.pokemon,null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);assertEquals(0,progress(player,"CAPTURE"));
 }
 @Test void ordinaryCaptureWorksAfterAnUnrelatedRaidFailure()throws Exception{
  victory(UUID.randomUUID(),List.of(PublicRaidApiFixture.faulty()),Set.of(player));engine.hunt(player,event(UUID.randomUUID(),null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);
  assertEquals(1,progress(player,"CAPTURE"));assertEquals(1,isolated.failures());
 }
 @Test void isolatedFailureNeverDisablesFutureWildVictories()throws Exception{
  UUID failed=UUID.randomUUID();victory(failed,List.of(PublicRaidApiFixture.actor("wild"),PublicRaidApiFixture.faulty()),Set.of(player));assertEquals(0,progress(player,"DEFEAT"));
  victory(UUID.randomUUID(),List.of(PublicRaidApiFixture.actor("wild")),Set.of(player));assertEquals(1,progress(player,"DEFEAT"));
  assertFalse(allow(failed,List.of(PublicRaidApiFixture.actor("wild"))));
 }
 @Test void actorRenameAndPrivateImplementationNeedNoAccessibleOverride()throws Exception{
  Actor privateActor=PublicRaidApiFixture.actor("raid");assertFalse(java.lang.reflect.Modifier.isPublic(privateActor.getClass().getModifiers()));assertFalse(privateActor.getClass().getSimpleName().contains("BossActor"));
  assertThrows(IllegalAccessException.class,()->privateActor.getClass().getMethod("getType").invoke(privateActor));
  assertEquals("WILD",privateActor.getType());assertTrue(raids.test(privateActor));assertFalse(allow(UUID.randomUUID(),List.of(privateActor)));
 }
 @Test void npcBattlesKeepTheirExistingExclusion()throws Exception{victory(UUID.randomUUID(),List.of(PublicRaidApiFixture.actor("npc")),Set.of(player));assertEquals(0,progress(player,"DEFEAT"));}
 @Test void duplicateWildEventsNeverProgressTwiceAfterRestart()throws Exception{
  Actor wild=PublicRaidApiFixture.actor("wild");UUID battle=UUID.randomUUID();victory(battle,List.of(wild),Set.of(player));
  engine=new Engine(config,store,new Random(2));gate=gate(engine);victory(battle,List.of(wild),Set.of(player));assertEquals(1,progress(player,"DEFEAT"));assertEquals(1,engine.view(player,now).hunts.seen.size());
 }
 @Test void durableRaidQuarantineBlocksLateEventsAfterSessionCleanupAndRestart()throws Exception{
  Actor boss=PublicRaidApiFixture.actor("raid");UUID battle=UUID.randomUUID();assertFalse(allow(battle,List.of(boss)));
  engine=new Engine(config,store,new Random(9));
  engine.hunt(player,event(boss.pokemon,battle,"DEFEAT",Engine.HuntOrigin.NORMAL),now);engine.hunt(player,event(boss.pokemon,null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);
  assertEquals(0,progress(player,"DEFEAT"));assertEquals(0,progress(player,"CAPTURE"));
  engine.hunt(player,event(UUID.randomUUID(),battle,"DEFEAT",Engine.HuntOrigin.NORMAL),now);assertEquals(0,progress(player,"DEFEAT"));
 }
 @Test void explicitlyRaidOrUnknownSourcesAreRejectedByCoreDefenseInDepth()throws Exception{
  for(var origin:List.of(Engine.HuntOrigin.RAID,Engine.HuntOrigin.UNKNOWN))for(String action:List.of("CAPTURE","DEFEAT"))engine.hunt(player,event(UUID.randomUUID(),UUID.randomUUID(),action,origin),now);
  assertEquals(0,progress(player,"DEFEAT"));assertEquals(0,progress(player,"CAPTURE"));assertTrue(engine.pending(player,"hunts").isEmpty());
 }
 @Test void repeatedFailuresAreRateLimitedButHandlingRemainsOperational(){
  for(int i=0;i<100;i++)isolated.run("victory",()->{throw new IllegalStateException("broken event");});assertEquals(1,warnings.size());
  assertTrue(isolated.run("victory",()->{}));clock.set(Duration.ofMinutes(1).toNanos()+1);isolated.run("victory",()->{throw new IllegalStateException("again");});assertEquals(2,warnings.size());assertTrue(warnings.get(1).contains("99"));
 }
 @Test void corruptExclusionJournalIsPreservedInsteadOfResettingRaidHistory()throws Exception{
  Path journal=root.resolve("data/excluded-raid-sources.json");Files.writeString(journal,"{broken}");assertThrows(Exception.class,()->new Engine(config,store,new Random(1)));assertEquals("{broken}",Files.readString(journal));
 }
 @Test void parallelPrivateRaidBattlesDenyEveryPlayerAndPersistEveryClone()throws Exception{
  List<UUID> players=new ArrayList<>();List<Actor> bosses=new ArrayList<>();
  for(int i=0;i<3;i++){UUID id=UUID.randomUUID();Actor boss=PublicRaidApiFixture.actor("raid");players.add(id);bosses.add(boss);victory(UUID.randomUUID(),List.of(boss),Set.of(id));}
  assertEquals(3,store.loadExclusions().battles.size());engine=new Engine(config,store,new Random(3));
  for(int i=0;i<3;i++){UUID id=players.get(i);engine.hunt(id,event(bosses.get(i).pokemon,UUID.randomUUID(),"DEFEAT",Engine.HuntOrigin.NORMAL),now);assertEquals(0,progress(id,"DEFEAT"));assertTrue(engine.pending(id,"hunts").isEmpty());}
 }
 @Test void linkFailureOrAssertionDoesNotPropagateToOtherModsOrLaterEvents(){
  assertFalse(isolated.run("victory",()->{throw new NoSuchMethodError("changed optional API");}));
  assertFalse(isolated.run("capture",()->{throw new AssertionError("one bad event");}));
  assertTrue(isolated.run("victory",()->{}));assertEquals(2,isolated.failures());
 }
 @Test void raidCaptureWithoutBattleIsStillBlockedByDurablePokemonIdentity()throws Exception{
  UUID target=UUID.randomUUID();engine.excludeRaid(null,Set.of(target));engine=new Engine(config,store,new Random(9));
  engine.hunt(player,event(target,null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);assertEquals(0,progress(player,"CAPTURE"));
  engine.hunt(player,event(UUID.randomUUID(),null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);assertEquals(1,progress(player,"CAPTURE"));
 }
 @Test void aRaidPokemonOwnedByPlayerDoesNotBlockTheirOrdinaryWildHunt()throws Exception{
  UUID trophy=UUID.randomUUID();engine.excludeRaid(UUID.randomUUID(),Set.of(trophy));
  UUID normalBattle=UUID.randomUUID();var ordinaryGate=new RaidBattleGate<String>((id,ids)->{throw new AssertionError("Ordinary battle incorrectly rejected");});assertTrue(ordinaryGate.allow(normalBattle,List.of("player","wild"),role->new RaidBattleGate.Evidence(RaidBattleGate.knownRaidTarget(role.equals("player")?"PLAYER":"WILD",role.equals("wild"),role.equals("player")),Set.of(trophy))));
  engine.hunt(player,event(UUID.randomUUID(),normalBattle,"DEFEAT",Engine.HuntOrigin.NORMAL),now);assertEquals(1,progress(player,"DEFEAT"));
 }
 @Test void anUnownedRaidTargetCannotBeReclassifiedAsOrdinaryWild(){
  assertTrue(RaidBattleGate.knownRaidTarget("WILD",true,true));assertFalse(RaidBattleGate.knownRaidTarget("WILD",false,true));assertFalse(RaidBattleGate.knownRaidTarget("PLAYER",false,true));
 }
 @Test void aFailedRaidClassificationCannotLeakThroughALaterCaptureWithoutBattleId()throws Exception{
  Actor broken=PublicRaidApiFixture.faulty();victory(UUID.randomUUID(),List.of(broken),Set.of(player));
  engine=new Engine(config,store,new Random(8));engine.hunt(player,event(broken.pokemon,null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);assertEquals(0,progress(player,"CAPTURE"));
  engine.hunt(player,event(UUID.randomUUID(),null,"CAPTURE",Engine.HuntOrigin.NORMAL),now);assertEquals(1,progress(player,"CAPTURE"));
 }
 @Test void aRaidLossIsQuarantinedEvenWhenNoPlayerWon()throws Exception{
  UUID battle=UUID.randomUUID();victory(battle,List.of(PublicRaidApiFixture.actor("raid")),Set.of());
  assertTrue(store.loadExclusions().battles.contains(battle));assertEquals(0,progress(player,"DEFEAT"));assertTrue(engine.pending(player,"hunts").isEmpty());
 }
 @Test void unrelatedOwnedTeamPokemonAreNeverAddedToRaidSourceIdentities(){
  assertFalse(RaidBattleGate.sourceIdentity(false,false,false));assertTrue(RaidBattleGate.sourceIdentity(true,false,false));assertTrue(RaidBattleGate.sourceIdentity(false,true,false));assertTrue(RaidBattleGate.sourceIdentity(false,false,true));
 }
}
