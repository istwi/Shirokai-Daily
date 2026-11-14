package dev.shirokai.daily.core;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MenuTextTest {
    @TempDir Path root;
    @Test void earlierDaysStayClaimedWhenTheNextDayIsAvailable(){
        assertEquals("RESGATADO",MenuText.dailyStatus(false,1,2,3));
        assertEquals("DISPONÍVEL",MenuText.dailyStatus(false,3,2,3));
        assertEquals("BLOQUEADO",MenuText.dailyStatus(false,4,2,3));
        assertEquals("PRÓXIMO",MenuText.dailyStatus(true,3,2,3));
        assertEquals("DISPONÍVEL",MenuText.dailyStatus(false,1,7,1));
        assertEquals("BLOQUEADO",MenuText.dailyStatus(false,2,0,1));
    }
    @Test void weeklyAndPermanentDescriptionsDoNotPromiseADailyReset()throws Exception{
        Config c=Config.load(root);Instant friday=Instant.parse("2026-11-13T15:00:00Z");
        assertEquals("&7Renova em: &b16/11 00:00",MenuText.renewal(c,"WEEKLY",friday));
        assertEquals("&7Renova em: &b14/11 00:00",MenuText.renewal(c,"DAILY",friday));
        assertEquals("&7Meta única &8• &7sem prazo",MenuText.renewal(c,"PERMANENT",friday));
    }
    @Test void objectiveExplainsSpeciesAndTypeFilters(){
        assertEquals("Capture &b3 Charmander do tipo Fogo",MenuText.objective("Capture",3,"Charmander","Fogo"));
        assertEquals("Capture &b3 Pokémon do tipo Fogo",MenuText.objective("Capture",3,"","Fogo"));
        assertEquals("Derrote &b10 Pokémon",MenuText.objective("Derrote",10,"",""));
    }
    @Test void rewardLoreShowsEligibleOddsWithoutExposingCommands()throws Exception{
        Config c=Config.load(root);var rewards=new HashMap<>(c.rewards());
        rewards.put("private",new Config.Reward("ITEM","minecraft:nether_star",1,1,100,"private.permission",""));
        rewards.put("command",new Config.Reward("COMMAND","secret internal command {uuid}",1,1,0,"",""));
        c=new Config(c.general(),c.daily(),c.playtime(),c.hunts(),rewards,c.messages(),c.menus());
        String lines=String.join("\n",MenuText.rewardLines(c,List.of("hunt_item","bonus_emerald","bonus_diamond","bonus_apple","private","command"),Set.of()));
        assertTrue(lines.contains("&a4× &7Barra de ouro"));
        assertTrue(lines.contains("60%"));assertTrue(lines.contains("30%"));assertTrue(lines.contains("10%"));
        assertFalse(lines.contains("nether"));assertFalse(lines.contains("secret"));assertFalse(lines.contains("{uuid}"));
        assertTrue(lines.contains("Recompensa especial"));
    }
    @Test void multilineRewardPlaceholderKeepsBlankLinesAndColors(){
        assertEquals(List.of("&6Recompensas","","&a1× Diamante","&a3× Esmeralda","&aClique"),MenuText.renderLines(
            List.of("&6Recompensas","","{reward_lines}","{hint_color}{hint}"),Map.of("reward_lines","&a1× Diamante\n&a3× Esmeralda","hint_color","&a","hint","Clique")));
    }
    @Test void shippedOldTemplatesUpgradeWithoutChangingCustomTextOrFiles()throws Exception{
        Config.load(root);Path file=root.resolve("menus/hunts.json");
        JsonObject json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        json.getAsJsonObject("texts").addProperty("hunts","&b&l{action} &f{species}");
        json.getAsJsonObject("texts").addProperty("profile","&dMeu perfil personalizado");
        JsonArray old=new JsonArray();for(String line:List.of("&7Tem prêmios aguardando entrega?","","&e➜ Clique para tentar receber."))old.add(line);
        json.getAsJsonObject("lore").add("pending",old);
        String saved=json.toString();Files.writeString(file,saved);
        var menu=Config.load(root).menus().get("hunts");
        assertEquals("&b{species}",menu.texts().get("hunts"));
        assertEquals("&dMeu perfil personalizado",menu.texts().get("profile"));
        assertEquals(Config.defaultMenu("hunts").lore().get("pending"),menu.lore().get("pending"));
        assertEquals(saved,Files.readString(file));
    }
}
