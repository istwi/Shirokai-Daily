package dev.shirokai.daily.core;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class StoreTest {
 @TempDir Path directory;
 @Test void nativePlatformCanReplaceAndReloadSnapshot()throws Exception{
  Store store=new Store(directory);UUID id=UUID.randomUUID();State s=new State();s.playtime.total=10;store.save(id,s);
  s.playtime.total=20;store.save(id,s);assertEquals(20,new Store(directory).load(id).playtime.total);
  assertFalse(Files.exists(directory.resolve(id+".tmp")));
 }
 @Test void windowsCompatibleSaveKeepsClaimsAndPlaytimeAcrossRestart()throws Exception{
  Config config=Config.load(directory.resolve("config"));Path data=directory.resolve("data");UUID id=UUID.randomUUID();Instant now=Instant.parse("2026-11-13T15:00:00Z");
  Engine engine=new Engine(config,new Store(data,false),new Random(1));
  assertNotNull(engine.claim(id,"daily","",Set.of("shirokaidaily.daily"),now));engine.time(id,10,10,now);
  Engine restarted=new Engine(config,new Store(data,false),new Random(2));
  assertNull(restarted.claim(id,"daily","",Set.of("shirokaidaily.daily"),now));assertEquals(10,restarted.view(id,now).playtime.total);
 }
 @Test void writeFailureIsNotHiddenAndPreviousSnapshotSurvives()throws Exception{
  Store store=new Store(directory,false);UUID id=UUID.randomUUID();State s=new State();s.playtime.total=10;store.save(id,s);
  Files.createDirectory(directory.resolve(id+".tmp"));s.playtime.total=99;
  assertThrows(IOException.class,()->store.save(id,s));assertEquals(10,store.load(id).playtime.total);
 }
}
