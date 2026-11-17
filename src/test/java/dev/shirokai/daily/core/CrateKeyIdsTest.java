package dev.shirokai.daily.core;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CrateKeyIdsTest {
 @Test void historicalBasicDeliveriesResolveToTheConfirmedServerId(){
  assertEquals("caixa_inicial_rara",CrateKeyIds.resolve("inicial_rara",Set.of("caixa_inicial_rara","caixa_epica")));
  assertEquals("caixa_inicial_rara",CrateKeyIds.resolve("caixa_inicial_rara",Set.of("caixa_inicial_rara")));
 }
 @Test void exactIdsWinAndUnrelatedCratesAreNeverSubstituted(){
  assertEquals("inicial_rara",CrateKeyIds.resolve("inicial_rara",Set.of("inicial_rara","caixa_inicial_rara")));
  assertNull(CrateKeyIds.resolve("inicial_rara",Set.of("basica","caixa_epica")));
  assertNull(CrateKeyIds.resolve("caixa_epica",Set.of("caixa_inicial_rara")));
 }
}
