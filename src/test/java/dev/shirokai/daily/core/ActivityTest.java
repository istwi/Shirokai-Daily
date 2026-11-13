package dev.shirokai.daily.core;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ActivityTest {
 @Test void idleStopsAtThreshold(){Activity a=new Activity(0,0,0,0,"world");assertEquals(new Activity.Sample(310,300),a.sample(310_000_000_000L,0,0,0,"world",true,300));assertEquals(0,a.sample(320_000_000_000L,0,0,0,"world",true,300).eligible());}
 @Test void microJitterDoesNotRestartAfk(){Activity a=new Activity(0,0,0,0,"world");for(int i=10;i<=320;i+=10)a.sample(i*1_000_000_000L,0.01,0,0,"world",true,300);assertEquals(0,a.sample(330_000_000_000L,-0.01,0,0,"world",true,300).eligible());}
 @Test void movementAndInteractionRestartActivity(){Activity a=new Activity(0,0,0,0,"world");assertEquals(310,a.sample(310_000_000_000L,1,0,0,"world",true,300).eligible());a.interaction(620_000_000_000L);assertEquals(310,a.sample(620_000_000_000L,1,0,0,"world",true,300).eligible());}
 @Test void dimensionChangeDoesNotCountTwice(){Activity a=new Activity(0,0,0,0,"world");assertEquals(10,a.sample(10_000_000_000L,100,0,0,"nether",false,300).total());assertEquals(0,a.sample(10_000_000_000L,100,0,0,"nether",false,300).total());}
 @Test void reconnectNeverCountsOfflineTime(){Activity a=new Activity(0,0,0,0,"world");assertEquals(10,a.sample(10_000_000_000L,0,0,0,"world",false,300).total());Activity reconnected=new Activity(100_000_000_000L,0,0,0,"world");assertEquals(10,reconnected.sample(110_000_000_000L,0,0,0,"world",false,300).total());}
}
