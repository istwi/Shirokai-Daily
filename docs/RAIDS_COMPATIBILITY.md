# Daily + Shirokai Raids — alpha.7

Alvo: Minecraft Fabric 1.21.1, Java 21, Cobblemon 1.8.3 e Shirokai Raids 0.1.20-dev. Atualize somente o Daily. O código, o JAR instalado, as configurações e as recompensas do Raids não precisam ser alterados.

## Causa e correção

Alpha.6 executava `actor.getClass().getMethod(...).invoke(actor)`. Um ator pode ter métodos públicos e ainda ser uma classe privada: era o caso do ator de boss do Raids. Resolver `getType()` nessa implementação privada provocava IllegalAccessException. O catch do callback ainda definia `healthy=false`, interrompendo todas as Hunts posteriores.

CobblemonBridge agora é compilado contra a API pública exata do Cobblemon 1.8.3. Getters de atores, Pokémon, batalhas e eventos são invocados diretamente pelos tipos públicos; o helper reflexivo `get()` foi removido. Não há `setAccessible`, acesso a campos privados nem comparação de nome simples com BossActor.

Para o mod opcional Raids, o adaptador valida e vincula somente dois métodos estáticos PÚBLICOS, com assinatura pública BattleActor:

- `TraditionalRaidBattles.isBossActor(BattleActor)`;
- `ShirokaiRaids.isCaptureActor(BattleActor)`.

São predicados sem alteração de estado, presentes no código 0.1.20-dev e no JAR recompilado desse código. Essa ligação opcional evita exigir Raids em servidores que não o usam. Não se reflete sobre atores concretos ou classes internas. Além dos predicados, usam-se os marcadores públicos de entidade/bola do Raids: `shirokai_raids_boss`, `shirokai_raid_boss`, `shirokai_raid_capture` e `shirokai_raid_throw`.

## Barreira antes do progresso

O Daily observa os eventos de início (PRE e POST), vitória e captura com prioridade HIGHEST, sem cancelar ou modificar o evento. Todos os atores da batalha são inspecionados antes de encaminhar qualquer crédito. Um ator de Raid basta para excluir a batalha inteira, independentemente de ele ser vencedor/perdedor ou de quantos jogadores participem.

São guardadas as identidades da batalha, dos Pokémon de boss/captura e das cópias privadas dos participantes. Isso cobre a limpeza das sessões do Raids antes do callback de vitória e eventos tardios. Uma falha de classificação coloca somente aquela batalha e seus alvos sem dono em quarentena; ela não é presumida normal. Pokémon legítimos já capturados de Raid e usados na equipe PLAYER não transformam futuras batalhas selvagens em Raids.

O núcleo também recusa origens RAID/UNKNOWN e identidades já excluídas, mesmo se algum callback posterior chegar como NORMAL. A exclusão persistente fica em `shirokai-daily-data/excluded-raid-sources.json`. A escrita é feita pelo executor próprio de armazenamento, com fsync do arquivo e substituição atômica; a sincronização de diretório continua omitida no Windows. Não há I/O de arquivo adicional na thread do jogo.

A deduplicação por jogador e UUID do Pokémon permanece persistente. O novo arquivo não substitui nem apaga os snapshots por UUID, metas, transações, entregas pendentes ou permissões. Faça backup da pasta inteira de dados. Não há tentativa de desfazer automaticamente progresso histórico da alpha.6, pois ele não contém informações suficientes para separar créditos legítimos e indevidos.

## Falhas isoladas e não interferência

Uma falha comum de callback, reflexão pública opcional ou linkage descarta somente o evento afetado. Eventos seguintes continuam sendo atendidos; não se muda `healthy` por falha de batalha nem se limpa o catálogo. Logs mostram a primeira falha e no máximo uma repetição por minuto por categoria, com contador de mensagens suprimidas. Erros fatais da VM não são tratados como erro comum de integração.

O filtro não altera HP, XP, Pokémon, inventários ou recompensas do Raids; não cancela eventos nem encerra batalhas. Eventos de Raids não chegam ao motor de progresso das Hunts e, portanto, não liberam novos prêmios/XP configurados como recompensas de Hunts. Daily, Playtime, recompensas legítimas anteriores e o funcionamento do Raids permanecem independentes.

Batalhas normais continuam exigindo vitória confirmada, perdedor WILD sem dono e HP zero; perdas de NPC/jogador e vitória por captura não viram derrota de Pokémon selvagem. Capturas confirmadas normais continuam nos objetivos CAPTURE. Só uma missão DEFEAT ativa avança por vitória selvagem: os padrões atuais de captura não passam a contar derrotas.

## Evidência executada

- Build/remapJar do Daily com API pública Cobblemon 1.8.3.
- 91 verificações do Daily, incluindo 21 regressões específicas de compatibilidade: vitória selvagem, NPC, vitória/derrota de Raid, boss privado com outro nome, 100 participantes num evento, três sessões privadas paralelas, captura de Raid com/sem batalha, captura normal após falha, falha num ator posterior sem crédito parcial, não propagação de erro comum/linkage/assertion, limitação de logs, replay e reinício, histórico de exclusão corrompido preservado e equipe PLAYER com Pokémon originado de Raid.
- Reprodução de IllegalAccessException em fixture com ator privado; o mesmo objeto passa pelos predicados públicos sem abrir acesso reflexivo.
- Raids 0.1.20-dev recompilado SEM mudanças a partir de `istwi/Shirokai-Raids`, commit `1a5e9ab1f0f0372750ff8d7ae4bfdde03da0a92e`; ABI conferida com `scripts/verify-raids-api.py`. O JAR de auditoria não é redistribuído pelo Daily.
- 125 testes disponíveis do próprio Raids passaram, sem falhas ou ignorados; o checkout auditado permaneceu sem alterações.

As regressões usam o núcleo real de progresso/quarentena e fixtures de atores/APIs públicas. Não são sessões reais com clientes Minecraft nem ensaios do motor de batalha. A compilação confirma as assinaturas do adaptador; a verificação em servidor com clientes permanece uma etapa de instalação.

## Conferência no servidor

1. Faça backup, pare o servidor e substitua somente o JAR Daily, mantendo Raids 0.1.20-dev.
2. Com objetivos DEFEAT e CAPTURE de teste ativos, vença um selvagem e faça uma captura normal; confira avanço apenas do objetivo correspondente.
3. Participe de uma Raid, vença/perca e conclua sua captura especial, com vários participantes: nenhum desses eventos deve avançar Hunts ou liberar seus prêmios.
4. Após a Raid, repita uma batalha e captura normais. Use também um Pokémon legítimo capturado de Raid em sua equipe: a vitória contra outro selvagem deve continuar elegível.
5. Confira `shirokaidaily status` e logs; uma falha isolada não deve pausar o módulo. Reinicie e confira que não houve repetição de progresso ou recompensas.
