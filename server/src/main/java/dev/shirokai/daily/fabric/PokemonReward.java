package dev.shirokai.daily.fabric;

import com.cobblemon.mod.common.Cobblemon;
import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import dev.shirokai.daily.core.Config;
import net.minecraft.server.network.ServerPlayerEntity;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.UUID;

/** Direct storage insertion is a gift, never a capture event or a Hunts objective. */
final class PokemonReward {
    static String deliver(ServerPlayerEntity player,Config.Reward reward,String transaction) {
        if(reward.amount()!=1)return "PENDING";
        var pool=PokemonSpecies.getSpecies().stream()
                .filter(s->s.getImplemented()&&s.getLabels().contains("legendary"))
                .sorted(Comparator.comparing(s->s.getResourceIdentifier().toString())).toList();
        if(pool.isEmpty())return "PENDING";
        UUID id=UUID.nameUUIDFromBytes(("shirokai-daily/pokemon/"+transaction).getBytes(StandardCharsets.UTF_8));
        var storage=Cobblemon.INSTANCE.getStorage();
        var party=storage.getParty(player);var pc=storage.getPC(player);
        // A deterministic UUID also protects an explicitly reconciled retry of an already present gift.
        if(party.get(id)!=null||pc.get(id)!=null)return "DONE";
        var species=pool.get(Math.floorMod(id.hashCode(),pool.size()));
        var properties=new PokemonProperties();properties.setSpecies(species.getResourceIdentifier().toString());
        properties.setShiny(true);properties.setMinPerfectIVs(6);
        var pokemon=properties.create(player);pokemon.setUuid(id);
        for(Stats stat:Stats.values())pokemon.setIV(stat,31);
        // Cobblemon's public party API overflows to the player's PC and owns storage persistence.
        // Any exception escapes as UNCERTAIN; automatic retries never repeat an uncertain gift.
        return party.add(pokemon)?"DONE":"PENDING";
    }
}
