# Integrações e limites

## Cobblemon

A investigação utilizou o código público upstream de `Cobblemon-Global/Cobblemon`, branch `main`, HEAD `75bb1a6c2fe92ef54951fb68d16d21f290a9ee88`, em 13/11/2026. Isso **não confirma compatibilidade binária com 1.8.3**. A consulta de tags `*1.8*` não retornou uma tag correspondente; a API Modrinth foi bloqueada por política de rede. O projeto Shirokai-Crates fornecido pelo usuário declara o artefato `com.cobblemon:fabric:1.8.3+1.21.1` em `https://artefacts.cobblemon.com/releases`, também bloqueado pelo proxy. O descritor exige exatamente 1.8.3 para não alegar suporte a outras versões.

Fontes consultadas (nenhum código upstream foi copiado):

- [CobblemonEvents](https://github.com/Cobblemon-Global/Cobblemon/blob/main/common/src/main/kotlin/com/cobblemon/mod/common/api/events/CobblemonEvents.kt)
- [PokemonCapturedEvent](https://github.com/Cobblemon-Global/Cobblemon/blob/main/common/src/main/kotlin/com/cobblemon/mod/common/api/events/pokemon/PokemonCapturedEvent.kt)
- [BattleVictoryEvent](https://github.com/Cobblemon-Global/Cobblemon/blob/main/common/src/main/kotlin/com/cobblemon/mod/common/api/events/battles/BattleVictoryEvent.kt)
- [PokemonSpecies](https://github.com/Cobblemon-Global/Cobblemon/blob/main/common/src/main/kotlin/com/cobblemon/mod/common/api/pokemon/PokemonSpecies.kt)
- [Observable](https://github.com/Cobblemon-Global/Cobblemon/blob/main/common/src/main/kotlin/com/cobblemon/mod/common/api/reactive/Observable.kt)

A ponte tenta vincular os eventos oficiais via reflexão, validando o registro real de espécies no startup, incluindo namespaces de addons já registrados. API desconhecida, registro vazio ou exceção suspendem o recebimento de eventos e aparecem em status/logs. Espécies e tipos configurados são revalidados no reload contra o registro observado no startup. Essa validação ainda não acompanha datapack reload; precisa ser completada com o artefato real disponível.

CAPTURE usa somente POKEMON_CAPTURED, sem eventos de tentativa/cálculo canceláveis. DEFEAT usa BATTLE_VICTORY: exclui vitória por captura, PvP e NPC, exige perdedor WILD, Pokémon sem dono e HP confirmado zero. Vencedores jogadores recebem o evento; batalhas cooperativas precisam de teste. UUID do Pokémon é deduplicado permanentemente por jogador, inclusive entre captura/derrota. Liberar/recapturar o mesmo Pokémon não aumenta progresso após sua primeira contagem. A ponte depende da garantia do evento oficial de captura de que não se captura Pokémon já pertencente a treinador; esse contrato precisa ser verificado em 1.8.3. Não conta breeding, shiny, raids ou ginásios.

## LuckPerms

`Permissions` usa a API pública por ligação opcional: LuckPermsProvider, UserManager, PermissionHolder, CachedDataManager, CachedPermissionData e Tristate. Resolve o usuário online em cache e verifica a permissão no resgate e na entrega. Sem LuckPerms aplica o fallback documentado no README; API presente mas incompatível nega acesso. Ainda sem teste de integração real e sem placeholders de prefixo.

## Impactor 5.3.5

O usuário forneceu `Impactor-Fabric-5.3.5+1.21.1.jar`. A ABI foi conferida com javap e `scripts/verify-impactor-api.py`, sem executar o código do mod. SHA-256 e evidências em VALIDATION.md.

O adaptador usa somente APIs públicas:

1. `EconomyService.instance().currencies().currency(Key.key(id))` consulta exatamente `impactor:dollars` ou `impactor:tokens`. Não cria moedas nem usa fallback para a moeda primária.
2. `account(currency, uuid)` obtém a conta de forma assíncrona.
3. `Account.depositAsync(BigDecimal)` é invocado pelo executor do servidor: nesta versão, o método default pode chamar o depósito síncrono imediatamente.
4. Só após `EconomyTransaction.successful()` e `EconomyService.save(account)` concluir a entrega torna-se DONE.

Moeda/API ausente antes de efeitos: PENDING. Depósito/gravação sem confirmação: UNCERTAIN, sem repetição automática. A API inspecionada não oferece chave de idempotência externa para esse fluxo; o ledger local e a reconciliação limitam duplicações, mas não criam uma transação distribuída. Os testes do fluxo usam um provider controlado; depósitos no servidor real ainda não foram executados. Uma chamada externa que nunca complete mantém a entrega em EXECUTING; não há timeout que cancele um depósito em andamento com segurança.

`ImpactorEconomy` faz ligação opcional à API pública via reflexão validada, para que o mod carregue sem Impactor. O JAR de terceiros não é redistribuído no projeto.

## Shirokai Crates

Fonte fornecida pelo usuário: [istwi/Shirokai-Crates](https://github.com/istwi/Shirokai-Crates), commit `05b3995c4d4466ef17c6171ad07d4f0eab512db9`, versão declarada 0.2.1. O repositório foi inspecionado em checkout temporário; nenhuma alteração foi enviada a ele.

`ShirokaiCrates.service()` expõe CrateService. Porém `give(admin,target,id,count,true)` é void, exige permissão administrativa do ator, pode retornar sem entregar e não expõe a conclusão do checkpoint. Não é um contrato confiável para recompensas automáticas. Dar `shirokaicrates.give` aos jogadores permitiria emissão administrativa e não é usado como solução. Daily não acessa campos privados, não lê o segredo HMAC e não recria a assinatura/NBT dos fragmentos.

Foi preparado [shirokai-crates-grant-fragments.patch](../integrations/shirokai-crates-grant-fragments.patch) com uma extensão pública mínima de CrateService:

```
CompletableFuture<Boolean> grantFragments(ServerPlayerEntity target, String crateId, int count)
```

A própria implementação Crates cria o fragmento com `tokens.create(crate,true,count)`, verifica saúde, conexão, espaço e exclusão mútua, insere e aguarda seu checkpoint de playerdata. `false` garante ausência de efeito; falha após inserção produz um future excepcional; `true` confirma a gravação. Não há segredo ou assinatura no Daily. A extensão não adiciona comando, menu, módulo ou permissão de emissão ao jogador.

O patch passou em `git apply --check` contra o commit indicado; ainda precisa de revisão, compilação e teste no projeto Crates. `ShirokaiCratesAdapter` só chama essa extensão se ela estiver presente. Sem Crates/API/caixa/espaço, o prêmio fica PENDING; falha após chamada fica UNCERTAIN. A extensão não aceita chave idempotente: resgates incertos exigem conferência antes de reenvio.

Aplicação em um checkout de Crates sob responsabilidade do administrador:

```sh
git apply /caminho/Shirokai-Daily/integrations/shirokai-crates-grant-fragments.patch
./gradlew build
```

Instale o JAR resultante e suas dependências oficiais no servidor de validação. Este patch ainda não foi aplicado nem publicado no repositório remoto.

## Outros providers

`RewardAdapter` é uma extensão via ServiceLoader. O provider inicia na thread do servidor e retorna `CompletionStage<String>`; a conclusão pode ocorrer em outra thread sem acessar APIs do jogo. Um JAR de integração fornece `META-INF/services/dev.shirokai.daily.fabric.RewardAdapter` e implementa:

```
boolean supports(String type)
CompletionStage<String> deliver(ServerPlayerEntity player, Config.Reward reward, String transactionId)
```

DONE apenas após confirmação durável. PENDING somente se nenhum efeito ocorreu. UNCERTAIN se não puder garantir o resultado. O transactionId é estável e deve ser enviado a APIs que suportem idempotência. Providers externos têm prioridade sobre os adaptadores embutidos. Nunca bloqueie a thread do jogo nem feche executores globais. O campo `value` identifica a caixa em CRATE_FRAGMENT; DOLLARS e TOKENS sempre usam as moedas fixas da especificação.

## Perfil e menus

Inventário vanilla equivalente ao SGui, sem registro de screen handler próprio ou pacotes customizados. Cliques, shift-click, drag, hotbar, drop e double-click são consumidos pelo handler; só pickup esquerdo em botão conhecido dispara ação. Precisa de teste com cliente vanilla + mods exigidos por Cobblemon.

Perfil mostra apenas nome, período e página. Placeholders `%luckperms:prefix%`, `%dexrewards:player_dex_percent%%`, `%player:objective gyms%` e `%cobbledollars:balance%` não são interpretados automaticamente nem anunciados como disponíveis. Layout, controles, ícones por categoria/estado e templates de textos/lore são parametrizados no JSON; o botão voltar mantém os dados exigidos pela especificação. Botão voltar renderiza o texto/cor solicitados e executa `/menu` como jogador; requer um comando `/menu` já fornecido pelo servidor.

## AFK

Atividade: movimento acumulado de pelo menos 0,5 bloco, uso de item/bloco, ataque e quebra. Rotação sozinha e jitter abaixo do limiar não reiniciam o relógio. Troca de dimensão não cria nova sessão ou atividade. A amostragem tem resolução de 10 segundos; movimento entre amostras pode creditar o intervalo atual inteiro. Nenhum detector desses prova presença humana: macros de interação e movimentação suficiente continuam sendo uma limitação. Não há loop por jogador a cada tick; só um contador global de ticks.
