package dev.shirokai.daily.core;
import java.util.*;
/** Durable quarantine of Raid identities; separate from player progress and reward ledgers. */
public final class SourceExclusions {
 public int schema=1;
 public Set<UUID> battles=new HashSet<>();
 public Set<String> pokemon=new HashSet<>();
}
