package dev.shirokai.daily.core;
import java.util.*;
public final class State {
    public int schema=1;
    public Daily daily=new Daily();
    public Playtime playtime=new Playtime();
    public Hunts hunts=new Hunts();
    public Map<String,Transaction> transactions=new LinkedHashMap<>();
    public static final class Daily { public int day,streak; public String last=""; public long resets; }
    public static final class Playtime { public long total,eligible,daily,weekly; public String day="",week=""; public Set<String> claimed=new HashSet<>(); }
    public static final class Hunts { public String day="",week=""; public Map<String,Quest> quests=new LinkedHashMap<>(); public Set<String> seen=new HashSet<>(); }
    public static final class Quest { public Config.Mission mission; public String period,epoch; public int progress; public boolean claimed; public Quest(Config.Mission m,String p,String e){mission=m;period=p;epoch=e;} }
    public static final class Transaction { public String origin; public List<Delivery> deliveries=new ArrayList<>(); public Transaction(String o){origin=o;} }
    public static final class Delivery { public String rewardId,status="PENDING"; public Config.Reward reward; public Delivery(String id,Config.Reward r){rewardId=id;reward=r;} }
}
