package dev.shirokai.daily.core;
import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class DailyBonusAndMilestonesTest {
 @TempDir Path root;
 Config config;Store store;Engine engine;
 final Instant now=Instant.parse("2026-11-14T15:00:00Z");
 final Set<String> permissions=Set.of("shirokaidaily.daily","shirokaidaily.playtime");
 @BeforeEach void setup()throws Exception{config=Config.load(root.resolve("config"));store=new Store(root.resolve("data"));engine=new Engine(config,store,new Random(42));}
 void legacy(String file)throws Exception{try(var input=getClass().getResourceAsStream("/migrations/alpha5/"+file)){assertNotNull(input);Files.write(root.resolve("config").resolve(file),input.readAllBytes());}}
 @Test void dailyUsesExactlyOneBonusWithSameOddsAndFreezesTheDraw()throws Exception{
  assertEquals(List.of(60,30,10),List.of("bonus_emerald","bonus_diamond","bonus_apple").stream().map(id->config.rewards().get(id).weight()).toList());
  for(int day=1;day<=30;day++)assertEquals(List.of("daily_item","bonus_emerald","bonus_diamond","bonus_apple"),config.daily().rewards().get(day));
  Set<String> outcomes=new HashSet<>();
  for(int i=0;i<40;i++){
   UUID id=UUID.randomUUID();assertNotNull(engine.claim(id,"daily","",permissions,now));var before=engine.pending(id,"daily");assertEquals(2,before.size());
   assertEquals(1,before.getFirst().reward().amount());assertEquals("minecraft:diamond",before.getFirst().reward().value());outcomes.add(before.get(1).reward().value());
   Engine restart=new Engine(config,store,new Random(999));assertEquals(before,restart.pending(id,"daily"));assertNull(restart.claim(id,"daily","",permissions,now));
  }assertEquals(Set.of("minecraft:emerald","minecraft:diamond","minecraft:golden_apple"),outcomes);
 }
 @Test void alpha5MigrationKeepsExistingClaimsAndDoesNotAddRetroactiveBonuses()throws Exception{
  legacy("daily.json");legacy("playtime.json");UUID id=UUID.randomUUID();State state=new State();state.daily.day=7;state.daily.streak=7;state.daily.last=config.day(now).toString();
  var transaction=new State.Transaction("daily");transaction.deliveries.add(new State.Delivery("daily_item",config.rewards().get("daily_item")));state.transactions.put("daily/"+config.day(now),transaction);store.save(id,state);
  Path player=root.resolve("data").resolve(id+".json");String prior=Files.readString(player);Config migrated=Config.load(root.resolve("config"));assertEquals(prior,Files.readString(player));
  Engine next=new Engine(migrated,store,new Random(1));assertNull(next.claim(id,"daily","",permissions,now));assertEquals(1,next.pending(id,"daily").size());
  assertNotNull(next.claim(id,"daily","",permissions,now.plusSeconds(86400)));assertEquals(3,next.pending(id,"daily").size());assertEquals(28,migrated.playtime().goals().size());
 }
 @Test void allFourRowsAreFilledAndOldThresholdIdsRemainStable(){
  var goals=config.playtime().goals();var menu=config.menus().get("playtime");assertEquals(28,goals.size());assertEquals(menu.slots().size(),goals.size());assertEquals(1,(goals.size()+menu.pageSize()-1)/menu.pageSize());
  assertEquals(28,new HashSet<>(goals.stream().map(Config.Goal::id).toList()).size());assertEquals(5000*3600L,goals.getLast().seconds());
  for(int i=1;i<goals.size();i++)assertTrue(goals.get(i-1).seconds()<goals.get(i).seconds());
  for(long hours:new long[]{1,6,12,24,60,100,250,500,1000})assertTrue(goals.stream().anyMatch(g->g.id().equals("hours_"+hours)&&g.seconds()==hours*3600));
 }
 @Test void aClaimedThousandHourMilestoneCannotBeReclaimedAfterExpansion()throws Exception{
  Config.Playtime old;
  try(var in=getClass().getResourceAsStream("/migrations/alpha5/playtime.json");var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){old=new Gson().fromJson(reader,Config.Playtime.class);}
  Config prior=new Config(config.general(),config.daily(),old,config.hunts(),config.rewards(),config.messages(),config.menus());UUID id=UUID.randomUUID();State state=new State();state.playtime.total=state.playtime.eligible=1000*3600L;store.save(id,state);
  Engine first=new Engine(prior,store,new Random(1));assertNotNull(first.claim(id,"playtime","hours_1000",permissions,now));var pending=first.pending(id,"playtime");
  first.reload(config);assertNull(first.claim(id,"playtime","hours_1000",permissions,now));assertEquals(pending,first.pending(id,"playtime"));assertNull(first.claim(id,"playtime","hours_5000",permissions,now));
  assertNotNull(first.claim(id,"playtime","hours_800",permissions,now));assertEquals(1000*3600L,first.view(id,now).playtime.eligible);
 }
 @Test void customDailyRewardsArePreserved()throws Exception{
  Path path=root.resolve("config/daily.json");JsonObject daily=JsonParser.parseString(Files.readString(path)).getAsJsonObject();JsonObject rewards=daily.getAsJsonObject("rewards");
  for(int day=1;day<=30;day++)rewards.add(Integer.toString(day),new Gson().toJsonTree(List.of("playtime_item")));String custom=daily.toString();Files.writeString(path,custom);
  assertEquals(List.of("playtime_item"),Config.load(root.resolve("config")).daily().rewards().get(1));assertEquals(custom,Files.readString(path));assertFalse(Files.exists(root.resolve("config/daily.json.pre-alpha6.bak")));
 }
 @Test void migrationsAndBackupsAreIdempotent()throws Exception{
  legacy("daily.json");legacy("playtime.json");Config.load(root.resolve("config"));Path daily=root.resolve("config/daily.json"),play=root.resolve("config/playtime.json"),backup=root.resolve("config/daily.json.pre-alpha6.bak");
  String dailyBefore=Files.readString(daily),playBefore=Files.readString(play),backupBefore=Files.readString(backup);Config.load(root.resolve("config"));
  assertEquals(dailyBefore,Files.readString(daily));assertEquals(playBefore,Files.readString(play));assertEquals(backupBefore,Files.readString(backup));
 }
}
