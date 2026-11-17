package dev.shirokai.daily.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UserRewardProfileTest {
 @TempDir Path root;
 Config profile()throws Exception{
  Config.load(root.resolve("config"));
  Path source=Path.of("reward-profiles/tabelas-usuario/config/shirokai-daily");
  for(String file:List.of("daily.json","playtime.json","hunts.json","rewards.json"))Files.copy(source.resolve(file),root.resolve("config").resolve(file),StandardCopyOption.REPLACE_EXISTING);
  return Config.load(root.resolve("config"));
 }
 @Test void fullProfileLoadsWithFiveDifficultiesAndAllExactMilestones()throws Exception{
  var c=profile();assertEquals(30,c.daily().rewards().size());
  assertEquals(List.of(1L,2L,3L,5L,8L,12L,18L,24L,36L,48L,72L,100L,150L,200L,300L,400L,500L,750L,1000L,1250L,1500L,2000L,2500L,3000L,3500L,4000L,4500L,5000L),c.playtime().goals().stream().map(g->g.seconds()/3600).toList());
  assertEquals(Set.of("EASY","MEDIUM","HARD","VERY_HARD","EXTREME"),new HashSet<>(c.hunts().pool().stream().map(Config.Mission::difficulty).toList()));
  for(var row:c.daily().rewards().values())checkOdds(c,row);
  for(var goal:c.playtime().goals())checkOdds(c,goal.rewards());
  for(var hunt:c.hunts().pool())checkOdds(c,hunt.rewards());
  assertEquals(Set.of("caixa_inicial_rara","caixa_epica","caixa_paradoxo","caixa_ultra_beasts","caixa_mitica","caixa_lendaria","shiny"),new HashSet<>(c.rewards().values().stream().filter(r->r.type().equals("CRATE_KEY")).map(Config.Reward::value).toList()));
 }
 void checkOdds(Config c,List<String> ids){
  assertEquals(List.of(60,30,10),ids.stream().map(c.rewards()::get).filter(r->r.weight()>0).map(Config.Reward::weight).toList());
  for(var id:ids){var r=c.rewards().get(id);assertEquals(r.weight()==0?1:r.chance()==0?0:0.25,r.chance());}
 }
 @Test void milestone5000KeepsBothFixedPrizesAndDoesNotRedistributeRemovedVipOdds()throws Exception{
  var c=profile();var store=new Store(root.resolve("data"));var now=Instant.parse("2026-11-14T15:00:00Z");int empty=0;Set<String> bonuses=new HashSet<>();
  var engine=new Engine(c,store,new Random(42));
  for(int i=0;i<100;i++){
   UUID id=UUID.randomUUID();State state=new State();state.playtime.total=state.playtime.eligible=18_000_000;store.save(id,state);
   assertNotNull(engine.claim(id,"playtime","hours_5000",Set.of("shirokaidaily.playtime"),now));
   var pending=engine.pending(id,"playtime");assertEquals("RANDOM_LEGENDARY_SHINY_6IV",pending.getFirst().reward().type());assertEquals("TOKENS",pending.get(1).reward().type());assertEquals(500,pending.get(1).reward().amount());
   assertTrue(pending.stream().noneMatch(p->p.reward().type().equals("COMMAND")));
   if(pending.size()==2)empty++;else{assertEquals(3,pending.size());bonuses.add(pending.getLast().reward().type());}
   Engine restarted=new Engine(c,store,new Random(1));assertEquals(pending,restarted.pending(id,"playtime"));assertNull(restarted.claim(id,"playtime","hours_5000",Set.of("shirokaidaily.playtime"),now));
  }
  assertTrue(empty>0);assertEquals(Set.of("TOKENS","CRATE_KEY"),bonuses);
 }
 @Test void quarterChancePreservesFixedPrizesAndNeverRerollsSavedClaims()throws Exception{
  var c=profile();var now=Instant.parse("2026-11-17T15:00:00Z");
  for(double draw:List.of(0.1,0.3)){
   UUID id=UUID.randomUUID();var store=new Store(root.resolve("quarter-"+draw));
   var engine=new Engine(c,store,new Random(7){public double nextDouble(){return draw;}public long nextLong(long bound){return 0;}});
   String key=engine.claim(id,"daily","",Set.of("shirokaidaily.daily"),now);assertNotNull(key);
   var transaction=store.load(id).transactions.get(key);
   assertEquals(draw<0.25?2:1,transaction.deliveries.size());
   assertEquals("daily_1_fixed_1",transaction.deliveries.getFirst().rewardId);
   assertEquals(engine.pending(id,"daily"),new Engine(c,store,new Random(99)).pending(id,"daily"));
   assertNull(engine.claim(id,"daily","",Set.of("shirokaidaily.daily"),now));
  }
 }
 @Test void combinedDailyPrizesAndTemporaryVipGroupsArePreserved()throws Exception{
  var c=profile();for(int day:List.of(7,14,21,30))assertEquals(2,c.daily().rewards().get(day).stream().map(c.rewards()::get).filter(r->r.weight()==0).count());
  var commands=c.rewards().values().stream().filter(r->r.type().equals("COMMAND")).map(Config.Reward::value).toList();assertEquals(8,commands.size());
  assertTrue(commands.contains("lp user {uuid} parent addtemp magma 7d accumulate"));assertTrue(commands.contains("lp user {uuid} parent addtemp aqua 30d accumulate"));assertTrue(commands.contains("lp user {uuid} parent addtemp plasma 60d accumulate"));
  var lines=MenuText.rewardLines(c,c.playtime().goals().getLast().rewards(),Set.of());
  assertTrue(lines.stream().anyMatch(s->s.contains("Sem bônus")&&s.contains("77.5%")));
 }
}
