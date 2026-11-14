package dev.shirokai.daily.core;
import java.io.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.random.RandomGenerator;

/** All methods execute on one owned worker; no Minecraft API access here. */
public final class Engine {
    private Config config; private final Store store; private final RandomGenerator random;
    public Engine(Config config,Store store,RandomGenerator random){this.config=config;this.store=store;this.random=random;}
    public record Species(String id,Set<String> types,boolean legendary){}
    private List<Species> species=List.of();
    public void species(List<Species> registered){species=List.copyOf(registered);}
    public Config config(){return config;}
    public void reload(Config next){next.validate();config=next;}
    public State view(UUID player,Instant now)throws IOException{State s=store.load(player);roll(s,now);store.save(player,s);return s;}
    private void roll(State s,Instant now){
        String day=config.day(now).toString(),week=config.week(now);
        if(!s.playtime.day.equals(day)){s.playtime.day=day;s.playtime.daily=0;s.playtime.claimed.removeIf(k->k.startsWith("DAILY/"));}
        if(!s.playtime.week.equals(week)){s.playtime.week=week;s.playtime.weekly=0;s.playtime.claimed.removeIf(k->k.startsWith("WEEKLY/"));}
        if(config.general().hunts()){
            if(config.hunts().dailyCount()>0&&!s.hunts.day.equals(day)){s.hunts.day=day;renew(s,"DAILY",day,config.hunts().dailyCount());}
            if(config.hunts().weeklyCount()>0&&!s.hunts.week.equals(week)){s.hunts.week=week;renew(s,"WEEKLY",week,config.hunts().weeklyCount());}
            if(config.hunts().hourlyCount()>0){
                String hour=config.huntEpoch(now);
                if(!s.hunts.hour.equals(hour)){
                    // Do not advance the persisted epoch until real species are available.
                    if(!config.hunts().randomSpecies()||!eligibleSpecies().isEmpty()){
                        renew(s,"HOURLY",hour,config.hunts().hourlyCount());s.hunts.hour=hour;
                    }
                }
            }
        }
    }
    private List<Species> eligibleSpecies(){
        var h=config.hunts();return species.stream().filter(p->!h.excludeLegendary()||!p.legendary())
            .filter(p->!h.blacklist().contains(p.id())&&(h.whitelist().isEmpty()||h.whitelist().contains(p.id())))
            .filter(p->h.pool().stream().anyMatch(m->(m.species().isEmpty()||m.species().equals(p.id()))&&(m.type().isEmpty()||p.types().contains(m.type())))).toList();
    }
    private void renew(State s,String period,String epoch,int count){
        Set<String> previous=new HashSet<>();for(var q:s.hunts.quests.values())if(q.period.equals(period))previous.add(q.mission.species());
        s.hunts.quests.entrySet().removeIf(e->e.getValue().period.equals(period)&&(!config.hunts().retainCompleted()||e.getValue().claimed||e.getValue().progress<e.getValue().mission.amount()));
        Set<String> selectedSpecies=new HashSet<>();List<Species> candidates=eligibleSpecies();
        for(int i=0;i<count;i++){
            List<Config.Mission> pool=config.hunts().pool();if(pool.isEmpty())break;
            if(period.equals("HOURLY")&&config.hunts().randomSpecies()){
                pool=pool.stream().filter(m->candidates.stream().anyMatch(p->(m.species().isEmpty()||m.species().equals(p.id()))&&(m.type().isEmpty()||p.types().contains(m.type())))).toList();
                if(pool.isEmpty())break;
            }
            Config.Mission selected=pool.get(i%pool.size());
            if(config.hunts().random()){long bound=pool.stream().mapToLong(Config.Mission::weight).sum(),n=random.nextLong(bound);for(var m:pool){n-=m.weight();if(n<0){selected=m;break;}}}
            if(period.equals("HOURLY")&&config.hunts().randomSpecies()){
                Config.Mission template=selected;
                List<Species> options=candidates.stream().filter(p->(template.species().isEmpty()||template.species().equals(p.id()))&&(template.type().isEmpty()||p.types().contains(template.type()))).toList();
                List<Species> fresh=options.stream().filter(p->!selectedSpecies.contains(p.id())&&!previous.contains(p.id())).toList();
                if(fresh.isEmpty())fresh=options.stream().filter(p->!selectedSpecies.contains(p.id())).toList();
                if(fresh.isEmpty())continue; // Never show the same target twice in a rotation.
                Species chosen=fresh.get(random.nextInt(fresh.size()));selectedSpecies.add(chosen.id());
                selected=new Config.Mission(template.id(),template.action(),chosen.id(),template.type(),template.difficulty(),template.amount(),template.weight(),template.rewards());
            }
            s.hunts.quests.put(period+"/"+epoch+"/"+i,new State.Quest(selected,period,epoch));
        }
    }
    private boolean current(State.Quest q,Instant now){
        return switch(q.period){case "HOURLY"->config.hunts().hourlyCount()>0&&q.epoch.equals(config.huntEpoch(now));case "DAILY"->config.hunts().dailyCount()>0&&q.epoch.equals(config.day(now).toString());case "WEEKLY"->config.hunts().weeklyCount()>0&&q.epoch.equals(config.week(now));default->false;};
    }
    public void time(UUID id,long total,long eligible,Instant now)throws IOException{
        if(!config.general().playtime()||total<=0)return;
        if(eligible<0||eligible>total)throw new IllegalArgumentException("Tempo elegível inválido");
        State s=store.load(id);
        // Split sampled intervals at local reset boundaries, including Monday and DST changes.
        Instant cursor=now.minusSeconds(total);long left=total,eligibleLeft=eligible;
        while(left>0){
            roll(s,cursor);
            long span=Math.min(left,Math.max(1,java.time.Duration.between(cursor,config.nextReset(cursor)).getSeconds()));
            long active=Math.min(span,eligibleLeft);
            s.playtime.total=Math.addExact(s.playtime.total,span);
            s.playtime.eligible=Math.addExact(s.playtime.eligible,active);
            s.playtime.daily=Math.addExact(s.playtime.daily,active);
            s.playtime.weekly=Math.addExact(s.playtime.weekly,active);
            eligibleLeft-=active;left-=span;cursor=cursor.plusSeconds(span);
        }
        roll(s,now);store.save(id,s);
    }
    public record HuntEvent(String uniqueId,String action,String species,Set<String> types,boolean legendary,boolean owned,boolean confirmed){}
    public void hunt(UUID id,HuntEvent event,Instant now)throws IOException{
        if(!config.general().hunts()||!event.confirmed||event.owned||event.uniqueId==null||event.uniqueId.isBlank())return;
        var c=config.hunts();if(c.excludeLegendary()&&event.legendary||c.blacklist().contains(event.species)||!c.whitelist().isEmpty()&&!c.whitelist().contains(event.species))return;
        State s=store.load(id);roll(s,now);if(!s.hunts.seen.add(event.uniqueId))return;
        // Durable identities deliberately retained: pruning would permit repeated Pokémon/battle exploitation.
        for(var q:s.hunts.quests.values())if(current(q,now)&&!q.claimed&&q.progress<q.mission.amount()){
            var m=q.mission;if(m.action().equals(event.action)&&(m.species().isEmpty()||m.species().equals(event.species))&&(m.type().isEmpty()||event.types.contains(m.type())))q.progress++;
        }store.save(id,s);
    }
    public String claim(UUID id,String origin,String target,Set<String> permissions,Instant now)throws IOException{
        if(!permissions.contains("shirokaidaily."+origin))return null;
        State s=store.load(id);roll(s,now);List<String> rewards=new ArrayList<>();String key;
        switch(origin){
            case "daily" -> {
                if(!config.general().daily())return null;
                var d=s.daily;LocalDate today=config.day(now);if(d.last.equals(today.toString()))return null;
                if(d.day>=config.daily().days()&&config.daily().completion().equals("STOP"))return null;
                long gap=d.last.isEmpty()?0:ChronoUnit.DAYS.between(LocalDate.parse(d.last),today);
                if(gap<0)return null;
                boolean reset=gap>1&&(config.daily().streakPolicy().equals("RESET")||config.daily().streakPolicy().equals("GRACE")&&gap-1>config.daily().graceDays());
                if(reset){d.day=0;d.streak=0;d.resets++;}
                if(d.day>=config.daily().days())d.day=0;
                d.day++;d.streak++;d.last=today.toString();rewards.addAll(config.daily().rewards().get(d.day));
                if(permissions.contains("shirokaidaily.vip"))rewards.addAll(config.daily().vipRewards());
                key="daily/"+today;
            }
            case "playtime" -> {
                if(!config.general().playtime())return null;
                var g=config.playtime().goals().stream().filter(x->x.id().equals(target)).findFirst().orElse(null);if(g==null)return null;
                key=goalKey(g,now);if(s.playtime.claimed.contains(key)||progress(s,g)<g.seconds())return null;
                s.playtime.claimed.add(key);rewards.addAll(g.rewards());key="playtime/"+key;
            }
            case "hunts" -> {
                if(!config.general().hunts())return null;
                var q=s.hunts.quests.get(target);if(q==null||q.claimed||q.progress<q.mission.amount()||!current(q,now)&&!config.hunts().retainCompleted())return null;
                q.claimed=true;rewards.addAll(q.mission.rewards());key="hunts/"+target;
            }
            default -> {return null;}
        }
        // Daily reset admin does not erase this immutable claim key.
        if(s.transactions.containsKey(key))return null;
        var txn=new State.Transaction(origin);List<String> weighted=new ArrayList<>();
        for(String reward:rewards){var r=config.rewards().get(reward);if(!allowed(r,permissions))continue;if(r.weight()>0)weighted.add(reward);else if(random.nextDouble()<r.chance())txn.deliveries.add(new State.Delivery(reward,r));}
        long bound=weighted.stream().mapToLong(r->config.rewards().get(r).weight()).sum();
        if(bound>0){long n=random.nextLong(bound);for(String r:weighted){var spec=config.rewards().get(r);n-=spec.weight();if(n<0){if(random.nextDouble()<spec.chance())txn.deliveries.add(new State.Delivery(r,spec));break;}}}
        s.transactions.put(key,txn);store.save(id,s);return key;
    }
    public static boolean allowed(Config.Reward r,Set<String> permissions){return (r.permission().isEmpty()||permissions.contains(r.permission()))&&(!r.condition().equals("VIP")||permissions.contains("shirokaidaily.vip"));}
    public long progress(State s,Config.Goal g){return switch(g.period()){case "DAILY"->s.playtime.daily;case "WEEKLY"->s.playtime.weekly;default->s.playtime.eligible;};}
    public String goalKey(Config.Goal g,Instant now){return g.period()+"/"+(g.period().equals("DAILY")?config.day(now):g.period().equals("WEEKLY")?config.week(now):"all")+"/"+g.id();}
    public record Pending(String transaction,int index,Config.Reward reward){}
    public List<Pending> pending(UUID id,String origin)throws IOException{
        State s=store.load(id);List<Pending> result=new ArrayList<>();s.transactions.forEach((key,t)->{if(t.origin.equals(origin))for(int i=0;i<t.deliveries.size();i++)if(t.deliveries.get(i).status.equals("PENDING"))result.add(new Pending(key,i,t.deliveries.get(i).reward));});return result;
    }
    public boolean begin(UUID id,Pending p)throws IOException{return status(id,p,"PENDING","EXECUTING");}
    public void finish(UUID id,Pending p,String result)throws IOException{if(!Set.of("DONE","PENDING","UNCERTAIN").contains(result))throw new IllegalArgumentException();status(id,p,"EXECUTING",result);}
    private boolean status(UUID id,Pending p,String expected,String next)throws IOException{State s=store.load(id);var d=s.transactions.get(p.transaction).deliveries.get(p.index);if(!d.status.equals(expected))return false;d.status=next;store.save(id,s);return true;}
    /** Explicit administrative reconciliation after checking the external inventory/account. */
    public void reconcile(UUID id,String key,int index,String resolution)throws IOException{
        if(!Set.of("DONE","PENDING").contains(resolution))throw new IllegalArgumentException("Resolução inválida");
        State s=store.load(id);var transaction=s.transactions.get(key);
        if(transaction==null||index<0||index>=transaction.deliveries.size())throw new IllegalArgumentException("Transação inexistente");
        var delivery=transaction.deliveries.get(index);
        if(!Set.of("EXECUTING","UNCERTAIN").contains(delivery.status))throw new IllegalArgumentException("Somente entregas incertas podem ser reconciliadas");
        delivery.status=resolution;store.save(id,s);
    }
    public void admin(UUID id,String operation,long seconds,Instant now)throws IOException{
        State s=store.load(id);roll(s,now);
        switch(operation){case "daily-reset"->{s.daily.day=0;s.daily.streak=0;s.daily.resets++;}case "playtime-add"->{s.playtime.total=Math.addExact(s.playtime.total,seconds);s.playtime.eligible=Math.addExact(s.playtime.eligible,seconds);s.playtime.daily=Math.addExact(s.playtime.daily,seconds);s.playtime.weekly=Math.addExact(s.playtime.weekly,seconds);}case "playtime-set"->{s.playtime.total=seconds;s.playtime.eligible=seconds;}case "hunts-reset","hunts-reroll"->{s.hunts.quests.clear();renew(s,"DAILY",s.hunts.day,config.hunts().dailyCount());renew(s,"WEEKLY",s.hunts.week,config.hunts().weeklyCount());if(config.hunts().hourlyCount()>0)renew(s,"HOURLY",config.huntEpoch(now),config.hunts().hourlyCount());}default->throw new IllegalArgumentException(operation);}
        store.save(id,s);
    }
}
