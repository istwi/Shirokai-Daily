package dev.shirokai.daily.core.integration;
import java.time.Duration;
import java.util.*;
import java.util.function.*;
/** A bad event is skipped, never disables future subscriptions. Logs at most once/minute per kind. */
public final class EventIsolation {
 private final LongSupplier clock;private final BiConsumer<String,Throwable> warn;
 private final Map<String,Long> last=new HashMap<>(),suppressed=new HashMap<>();private long failures;
 public EventIsolation(LongSupplier clock,BiConsumer<String,Throwable> warn){this.clock=clock;this.warn=warn;}
 public boolean run(String kind,Runnable event){
  try{event.run();return true;}
  catch(Throwable error){
   if(error instanceof VirtualMachineError fatal)throw fatal;
   if(error instanceof ThreadDeath fatal)throw fatal;
   failures++;long now=clock.getAsLong();Long prior=last.get(kind);
   if(prior==null||now-prior>=Duration.ofMinutes(1).toNanos()){
    long skipped=suppressed.getOrDefault(kind,0L);last.put(kind,now);suppressed.put(kind,0L);
    warn.accept("Hunts: evento "+kind+" ignorado; demais eventos continuam ativos (repetições suprimidas="+skipped+")",error);
   }else suppressed.merge(kind,1L,Long::sum);
   return false;
  }
 }
 public long failures(){return failures;}
}
