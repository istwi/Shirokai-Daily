package dev.shirokai.daily.core.fixtures;
import java.util.*;
/** Test doubles of the published actor predicates, deliberately using a NON-PUBLIC renamed actor. */
public final class PublicRaidApiFixture {
 public abstract static class Actor {
  public final UUID pokemon=UUID.randomUUID();public final String source;public final boolean faulty;
  protected Actor(String source,boolean faulty){this.source=source;this.faulty=faulty;}
  public abstract String getType();
 }
 private static final class VeiledEncounter extends Actor {
  VeiledEncounter(String source,boolean faulty){super(source,faulty);}
  @Override public String getType(){return source.equals("npc")?"NPC":"WILD";}
 }
 public static Actor actor(String source){return new VeiledEncounter(source,false);}
 public static Actor faulty(){return new VeiledEncounter("raid",true);}
 public static final class CombatApi {
  public static boolean isBossActor(Actor actor){if(actor.faulty)throw new IllegalStateException("single-event integration failure");return actor.source.equals("raid");}
 }
 public static final class CaptureApi {
  public static boolean isCaptureActor(Actor actor){return actor.source.equals("raid-capture");}
 }
}
