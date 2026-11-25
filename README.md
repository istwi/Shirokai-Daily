# ✦ Shirokai Daily

## Chaves e chances — 0.1.0-alpha.12

Corrigido o ID da Básica para `caixa_inicial_rara`, com compatibilidade para entregas antigas pendentes com `inicial_rara`. Os 32 prêmios de chaves do Daily e 43 do Playtime foram conferidos contra a lista do servidor. As mensagens agora distinguem entrega confirmada, pendência e resultado incerto; o sorteio sozinho não anuncia mais uma entrega.

No perfil do servidor, bônus reduzidos para um quarto das chances anteriores (60%/30%/10% → 15%/7,5%/2,5%); fixas preservadas. Para aplicar no servidor existente, faça backup e substitua somente `config/shirokai-daily/rewards.json` pelo [arquivo atualizado](reward-profiles/tabelas-usuario/config/shirokai-daily/rewards.json), além do JAR. Não apague o progresso. Retente a chave antiga pelo botão **Entregas pendentes**.

99 testes passaram e build/remapeamento Fabric concluídos. A entrega ainda precisa ser conferida no servidor real; veja as [instruções do perfil](reward-profiles/tabelas-usuario/README.md).

## Mensagem de resgate — 0.1.0-alpha.11

Os resgates de Daily, Playtime e Hunts mostram no chat as recompensas fixas e somente os bônus efetivamente sorteados. O resumo usa a transação persistida, sem novo sorteio ou lista de bônus possíveis. A confirmação de resgate preserva o fluxo de entregas pendentes; não significa que uma entrega pendente já foi concluída.

JAR: [shirokai_daily-0.1.0-alpha.11.jar](artifacts/v0.1.0-alpha.11/shirokai_daily-0.1.0-alpha.11.jar), com SHA-256 em `artifacts/v0.1.0-alpha.11/SHA256SUMS`. Validação: 95 testes passaram e build/remapeamento Fabric concluídos. Ainda sem teste funcional dentro do Minecraft.

Mod exclusivamente de servidor para Minecraft 1.21.1 / Fabric / Java 21, destinado ao Cobblemon **1.8.3**. Daily, Playtime e Hunts são independentes e possuem resgate manual. Versão **alpha compilada para Fabric; ainda sem validação funcional com cliente Minecraft**.

## Compatibilidade com Shirokai Raids — 0.1.0-alpha.7

Corrige IllegalAccessException do ator privado de boss e remove a pausa global das Hunts por falha em um único evento. Batalhas e capturas de Raid são filtradas antes de qualquer crédito, usando os predicados públicos do Raids 0.1.20-dev, marcadores de entidades/bolas e identidades persistentes. Erros são isolados e os logs limitados por categoria.

Troque somente o JAR Daily pelo alpha.7 e mantenha seu Raids 0.1.20-dev. Recompensas e comportamento do Raids não foram alterados. Batalhas/capturas normais, metas e resgates existentes são preservados. O arquivo novo `shirokai-daily-data/excluded-raid-sources.json` deve acompanhar o backup da pasta de dados. Progresso histórico não é apagado automaticamente.

**91 verificações do Daily passaram**, além dos 125 testes disponíveis do Raids recompilado sem mudanças. A compatibilidade foi validada por compilação/ABI e testes automatizados; ainda sem ensaio com clientes Minecraft. Consulte [a análise e o roteiro de conferência](docs/RAIDS_COMPATIBILITY.md).

JAR: `server/build/libs/shirokai_daily-0.1.0-alpha.7.jar`.

## Daily com bônus e Playtime completo — 0.1.0-alpha.6

O Daily agora oferece o prêmio fixo de cada dia **mais um único bônus aleatório**: 3 esmeraldas (60%), 2 diamantes (30%) ou 1 maçã dourada (10%). As opções e chances aparecem na lore com o mesmo visual de Playtime. O sorteio só acontece no resgate e permanece salvo para recuperação de entrega.

Playtime preenche as quatro linhas internas de sete slots, sem segunda página:

| Linha | Metas |
| --- | --- |
| 1 | 1h · 3h · 6h · 12h · 24h · 36h · 48h |
| 2 | 60h · 80h · 100h · 150h · 200h · 250h · 350h |
| 3 | 500h · 650h · 800h · 1000h · 1250h · 1500h · 1750h |
| 4 | 2000h · 2500h · 3000h · 3500h · 4000h · 4500h · 5000h |

As nove metas anteriores preservam seus IDs e limites; prêmios já resgatados não são liberados de novo. O tempo acumulado não é reduzido. As novas metas usam o progresso já existente e continuam com resgate manual e o mesmo bônus ponderado.

Atualização: pare o servidor, faça backup e substitua o JAR antigo pelo alpha.6. Padrões reconhecidos da alpha.5 migram com backup `*.pre-alpha6.bak`; configurações personalizadas permanecem intactas. Um Daily já resgatado hoje só terá bônus no próximo resgate elegível; entregas antigas pendentes não recebem um segundo sorteio retroativo. Se personalizou as recompensas, adicione os IDs de bônus no seu próprio `daily.json` usando os exemplos como referência.

O JAR Fabric atual é `server/build/libs/shirokai_daily-0.1.0-alpha.6.jar`. Foram executadas 70 verificações, sem falhas ou ignorados. A distribuição de 28 metas usa o mesmo contador periódico de tempo; aparência e interação no cliente Minecraft ainda precisam de conferência no jogo.

## Novos menus — 0.1.0-alpha.5

- Decoração padrão com `minecraft:gray_stained_glass_pane` nos três menus.
- Daily: calendário padrão de 30 dias em uma página.
- Playtime: uma página com metas permanentes de **1h, 6h, 12h, 24h, 60h, 100h, 250h, 500h e 1000h**. O limite configurável é 1000h; o total jogado pode ultrapassar esse valor. Progresso aparece em horas/minutos sem arredondar antecipadamente uma meta.
- Hunts: baú vanilla de **27 slots**, três alvos de captura diferentes, sorteados de espécies implementadas do registro Cobblemon (incluindo addons), respeitando whitelist/blacklist e exclusão de lendários/míticos. Ícones usam os modelos oficiais via `PokemonItem.from(Species, String...)`, API conferida no JAR oficial 1.8.3 com checksum verificado. Se a API do modelo não estiver disponível, o ícone usa papel e o console informa a falha.
- Rotação de Hunts nas horas cheias (60 minutos), persistida por UUID e período; reabrir, recarregar ou reiniciar não refaz os alvos da mesma hora. Se o pool permitir, a próxima hora evita os alvos anteriores. O menu aberto é atualizado na primeira amostragem periódica após a troca, em até aproximadamente 10 segundos.
- Prêmios padrão por dificuldade: **500 / 1000 / 1500 Impactor Dollars**, com uma, duas ou três capturas. Os bônus aleatórios da alpha.4 foram preservados em Playtime e Hunts. Sorteio de dificuldade ponderado e valores editáveis em `hunts.json`/`rewards.json`. É necessário resgatar manualmente antes da troca de hora; concluídas sem resgate expiram por padrão. Recompensas já resgatadas que aguardam entrega permanecem no ledger e no botão de pendências.

### Atualização sem apagar progresso

Pare o servidor e faça backup das configurações e de `shirokai-daily-data`. Substitua apenas o JAR antigo pelo alpha.3. Configurações reconhecidas como os padrões antigos são migradas automaticamente, com cópias `*.pre-alpha5.bak`. Configurações personalizadas são preservadas: use os exemplos atualizados para ajustar essas configurações manualmente. Não remova arquivos por UUID nem o histórico de transações. Metas antigas resgatadas permanecem registradas; os novos IDs de metas representam o novo calendário de prêmios. Missões diárias/semanais antigas permanecem nos dados, mas não são mostradas quando seus contadores estão desativados.

Novos campos: `hunts.json.hourlyCount`, `rotationSeconds`, `randomSpecies`; `menus/*.json.rows` controla o tamanho do inventário. `slots` e os controles precisam estar dentro de `rows * 9` e sem sobreposição. As metas de Playtime devem caber na página para não criar outro menu. Dados de jogador não são alterados pela migração de configuração.

Esta pré-release preserva as melhorias de cores, lore e bônus da alpha.4. O JAR foi compilado e testado localmente, sem validação visual dentro do Minecraft nesta alteração.

## Correção Windows — 0.1.0-alpha.2

Corrige `AccessDeniedException` em `Store.save` no Windows: a sincronização adicional do diretório agora ocorre somente fora do Windows. O JSON continua sendo gravado em arquivo temporário, sincronizado com `force(true)` e substituído atomicamente em todas as plataformas. Erros reais de gravação continuam sendo propagados. Não é necessário executar o servidor como administrador nem apagar os dados. O Windows não possui a mesma barreira adicional de fsync do diretório Unix.

Para atualizar, pare o servidor, faça backup de `shirokai-daily-data`, substitua o JAR alpha.1 pelo alpha.2 e inicie novamente. Mantenha apenas uma versão do mod na pasta `mods`.

## Estado real da entrega

- Núcleo Java compilado; **91 verificações passaram** (90 testes de comportamento + 1 de sintaxe Java), zero falhas/erros/ignorados.
- Implementados configuração, persistência por UUID, calendário, metas, missões, ledger de recompensas e testes.
- Entrypoint Fabric, três inventários vanilla bloqueados, comandos, permissões e ponte de eventos Cobblemon compilados com Fabric Loom 1.8.13. Build completa e remapeamento concluídos após a liberação da rede. Ainda sem execução funcional no Minecraft.
- O JAR Fabric compilado é `server/build/libs/shirokai_daily-0.1.0-alpha.7.jar`. O JAR em `build/libs` é apenas o núcleo, **não instale esse arquivo como mod**. A versão alpha possui limitações descritas abaixo.
- A integração real com Cobblemon 1.8.3, LuckPerms e cliente sem Shirokai Daily precisa de teste no servidor. Não foi possível obter/confirmar o artefato Cobblemon 1.8.3 na rede atual.
- Dollars e Tokens possuem adaptador da API real do Impactor 5.3.5 fornecido pelo usuário; métodos públicos foram conferidos no JAR. O adaptador de fragmentos requer a extensão pública preparada em `integrations/shirokai-crates-grant-fragments.patch`. Sem essa extensão, fragmentos ficam pendentes. As integrações ainda precisam de teste em servidor; nenhum fragmento é falsificado.

Consulte [limitações e APIs](docs/INTEGRATIONS.md) e [validação pendente](docs/VALIDATION.md) antes de instalar. A pré-release alpha não representa conclusão integral de `docs/SPECIFICATION.txt`.

## Desenvolvimento

```sh
# JDK 21 completo, não apenas JRE
./gradlew -PcoreOnly test
./gradlew -PcoreOnly generateExamples
./gradlew build
```

Gradle Wrapper 8.10.2 possui checksum de distribuição fixado. A build completa requer acesso aos repositórios Fabric e downloads Mojang. Na máquina gerenciada:

```sh
bash scripts/cloud-setup.sh --core-only
source scripts/cloud-env.sh
./gradlew build
```

O helper instala Temurin 21.0.13+9 com SHA-256 verificado, preserva TLS e usa os proxies herdados. Seu cache fica fora do checkout. Use o checkout existente; tarefas na nuvem já são isoladas e não precisam de worktree.

Após a build completa, o artefato é `server/build/libs/shirokai_daily-0.1.0-alpha.5.jar` (sem `-sources`/`-dev`). Instale somente no servidor junto com Fabric API e Cobblemon 1.8.3 e suas dependências oficiais. Clientes precisam dos mods exigidos pelo próprio Cobblemon, mas não do Shirokai Daily. Os menus usam exclusivamente o protocolo vanilla de baú; isso ainda precisa de teste funcional real.

## Configuração

Primeira inicialização gera `config/shirokai-daily/`:

| Arquivo | Conteúdo |
| --- | --- |
| `config.json` | Módulos, fuso, hora de reset, mapeamento de permissões e integrações |
| `daily.json` | 7/14/30 dias, RESET/PAUSE/GRACE, tolerância, recompensas únicas por dia do calendário e VIP |
| `playtime.json` | AFK e metas com IDs estáveis, segundos e períodos PERMANENT/DAILY/WEEKLY |
| `hunts.json` | Pools ponderados/fixos, quantidades, filtros e retenção de concluídas |
| `rewards.json` | IDs estáveis, tipos, valor, quantidade, chance, peso, permissão e condição |
| `messages.json` | Mensagens de resultado |
| `menus/{daily,playtime,hunts}.json` | Título, decoração, slots, controles, ícones, textos e lore |

Exemplos gerados pelo mesmo código de inicialização em [examples/config/shirokai-daily](examples/config/shirokai-daily). IDs de espécies precisam de namespace (`cobblemon:charmander`, `addon:species`); tipos usam nome simples (`fire`). Dificuldades: EASY, MEDIUM, HARD. Ações: CAPTURE, DEFEAT. Pool aleatório permite repetições quando há mais slots que modelos. No modo fixo, percorre o pool em ordem.

Defaults: reset às 00:00 em America/Sao_Paulo; calendário de 30 dias; 28 metas permanentes de 1h até 5000h em uma página; AFK de 300 segundos; 3 Hunts de captura por hora com Impactor Dollars. Daily e Playtime usam itens vanilla por padrão. Períodos diários/semanais continuam disponíveis para configurações personalizadas.

Recompensas com `weight=0` fazem sorteios independentes por `chance`. Entre recompensas com peso positivo no mesmo resgate, seleciona-se uma por peso e depois aplica-se sua chance. O resultado e o conteúdo da recompensa são persistidos antes da entrega; reload não refaz sorteios. `permission` vazia libera; `condition` aceita vazio ou VIP. Recompensas ineligíveis não entram no sorteio.

ITEM usa ID do registro Minecraft/Cobblemon. COMMAND é executado como servidor, aceita `{uuid}`, `{player}`, `{amount}` e `{transaction}` e deve conter somente comandos confiáveis configurados pelo administrador. Não configure comandos de entrega como substitutos de uma integração de fragmentos sem conferir a API oficial do seu plugin.

`reload` valida uma configuração completa antes da troca. Erro preserva a configuração ativa e o progresso. Não renomeie IDs de metas para representar o mesmo prêmio; um novo ID é uma nova meta. Não troque fuso/período para tentar redefinir resgates. Edições concorrentes de arquivos de configuração não são transacionais: termine de gravar todos os arquivos antes do reload.

Nos menus, `slots` define a sequência de posições de conteúdo; `profileSlot`, `pendingSlot`, `previousSlot`, `nextSlot` e `backSlot` posicionam controles, sem sobreposição. `icons`, `texts` e `lore` aceitam chaves `daily`, `playtime`, `hunts`, `profile`, `pending`, `previous` e `next`. Ícones podem especializar estados, por exemplo `daily.DISPONÍVEL`. Textos/lore aceitam `{day}`, `{status}`, `{streak}`, `{resets}`, `{goal}`, `{mission}`, `{action}`, `{species}`, `{type}`, `{difficulty}`, `{progress}`, `{target}`, `{period}`, `{epoch}`, `{rewards}`, `{total}`, `{eligible}` conforme a entrada; perfil aceita `{player}`, `{page}`, `{pages}`, `{module}`, `{reset}`. O botão voltar mantém o texto e o comportamento exigidos pela especificação.

`config.json.permissions` permite mapear um nó lógico para outro nó real, preservando os nomes padrão quando não definido. `integrations` permite desativar `luckperms`, `cobblemon` ou providers de `rewards`; itens/comandos permanecem no núcleo.

## Comandos

Jogadores: `/daily`, `/playtime`, `/hunts`. Permissões respectivas `shirokaidaily.daily`, `.playtime`, `.hunts`; VIP: `shirokaidaily.vip`; administrador: `shirokaidaily.admin`.

```
/shirokaidaily reload
/shirokaidaily status
/shirokaidaily status <jogador-ou-UUID>
/shirokaidaily reconcile <UUID> "<transação>" <índice> DONE|PENDING
/shirokaidaily daily reset <jogador-ou-UUID>
/shirokaidaily playtime add <jogador-ou-UUID> <minutos>
/shirokaidaily playtime set <jogador-ou-UUID> <minutos>
/shirokaidaily hunts reset <jogador-ou-UUID>
/shirokaidaily hunts reroll <jogador-ou-UUID>
```

Offline: forneça UUID completo, sem consulta bloqueante de perfil. Sem LuckPerms, os três comandos são públicos, VIP fica desabilitado, administração exige nível 2. Erros na API de permissões negam acesso. `daily reset` reinicia calendário/streak, preservando o bloqueio de resgate do dia. `playtime set` ajusta total/elegível permanente; contadores diário/semanal e resgates são preservados. `hunts reset/reroll` substituem missões, preservando o histórico de recompensas para impedir reaproveitar slots do mesmo período.

## Dados e recuperação

`shirokai-daily-data/<UUID>.json` separa Daily, Playtime, Hunts e transações em seções de um snapshot atômico, evitando commits parciais entre resgate e ledger. Escrita via arquivo temporário, fsync e rename atômico; falhas/corrupção não criam progresso vazio. Um executor próprio serializa operações fora da thread do jogo. Playtime é amostrado a cada 10 segundos, no disconnect e shutdown; uma queda abrupta pode perder a última amostra. Fronteiras diárias/semanais dividem o intervalo corretamente.

Estados de entrega:

- `PENDING`: sem efeito realizado; botão **Entregas pendentes** no menu de origem tenta novamente. Inventário cheio não recebe entrega parcial.
- `EXECUTING`: intenção gravada antes de acessar inventário/API/comando.
- `DONE`: retorno confirmado pela operação.
- `UNCERTAIN`: ocorreu exceção ou comando não confirmou sucesso.

Após queda, EXECUTING/UNCERTAIN **não são repetidos automaticamente**. Não existe transação atômica entre JSON, inventário vanilla e APIs externas. Confira inventário, extrato/API e logs; faça backup com servidor parado e reconcilie a linha do ledger para DONE se entregue ou PENDING somente após provar que não houve entrega. Não remova a transação inteira nem refaça o sorteio. Use `/shirokaidaily status <UUID>` para consultar as últimas 20 transações e `/shirokaidaily reconcile <UUID> "<chave>" <índice>` com DONE ou PENDING. Índices começam em zero; o comando só aceita EXECUTING/UNCERTAIN e registra o administrador nos logs. A conferência do efeito permanece manual.

Faça backup da pasta de dados e das configurações. Desabilitar módulos não remove seus dados. IDs de Pokémon vistos são duráveis para impedir reutilização após reset/reinício; esse histórico cresce e ainda precisa de índice/medição em servidores de longa duração. Não há benchmark de 100 jogadores.

## Perfil de recompensas do servidor

A [tabela de recompensas personalizada](reward-profiles/tabelas-usuario/README.md) contém os JSONs opcionais de 30 dias de Daily, 28 marcos até 5.000h e cinco dificuldades de Hunts. Requer alpha.8. O perfil não substitui automaticamente configurações existentes.
