# ✦ Shirokai Daily

Mod exclusivamente de servidor para Minecraft 1.21.1 / Fabric / Java 21, destinado ao Cobblemon **1.8.3**. Daily, Playtime e Hunts são independentes e possuem resgate manual. Versão **alpha compilada para Fabric; ainda sem validação funcional com cliente Minecraft**.

## Correção Windows — 0.1.0-alpha.2

Corrige `AccessDeniedException` em `Store.save` no Windows: a sincronização adicional do diretório agora ocorre somente fora do Windows. O JSON continua sendo gravado em arquivo temporário, sincronizado com `force(true)` e substituído atomicamente em todas as plataformas. Erros reais de gravação continuam sendo propagados. Não é necessário executar o servidor como administrador nem apagar os dados. O Windows não possui a mesma barreira adicional de fsync do diretório Unix.

Para atualizar, pare o servidor, faça backup de `shirokai-daily-data`, substitua o JAR alpha.1 pelo alpha.2 e inicie novamente. Mantenha apenas uma versão do mod na pasta `mods`.

## Estado real da entrega

- Núcleo Java compilado; **42 verificações passaram** (41 testes de comportamento + 1 de sintaxe Java), zero falhas/erros/ignorados.
- Implementados configuração, persistência por UUID, calendário, metas, missões, ledger de recompensas e testes.
- Entrypoint Fabric, três inventários vanilla bloqueados, comandos, permissões e ponte de eventos Cobblemon compilados com Fabric Loom 1.8.13. Build completa e remapeamento concluídos após a liberação da rede. Ainda sem execução funcional no Minecraft.
- O JAR Fabric compilado é `server/build/libs/shirokai_daily-0.1.0-alpha.2.jar`. O JAR em `build/libs` é apenas o núcleo, **não instale esse arquivo como mod**. A versão alpha possui limitações descritas abaixo.
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

Após a build completa, o artefato é `server/build/libs/shirokai_daily-0.1.0-alpha.2.jar` (sem `-sources`/`-dev`). Instale somente no servidor junto com Fabric API e Cobblemon 1.8.3 e suas dependências oficiais. Clientes precisam dos mods exigidos pelo próprio Cobblemon, mas não do Shirokai Daily. Os menus usam exclusivamente o protocolo vanilla de baú; isso ainda precisa de teste funcional real.

## Configuração

Primeira inicialização gera `config/shirokai-daily/`:

| Arquivo | Conteúdo |
| --- | --- |
| `config.json` | Módulos, fuso, hora de reset, mapeamento de permissões e integrações |
| `daily.json` | 7/14/30 dias, RESET/PAUSE/GRACE, tolerância, RESTART/STOP, recompensas por dia e VIP |
| `playtime.json` | AFK e metas com IDs estáveis, segundos e períodos PERMANENT/DAILY/WEEKLY |
| `hunts.json` | Pools ponderados/fixos, quantidades, filtros e retenção de concluídas |
| `rewards.json` | IDs estáveis, tipos, valor, quantidade, chance, peso, permissão e condição |
| `messages.json` | Mensagens de resultado |
| `menus/{daily,playtime,hunts}.json` | Título, decoração, slots, controles, ícones, textos e lore |

Exemplos gerados pelo mesmo código de inicialização em [examples/config/shirokai-daily](examples/config/shirokai-daily). IDs de espécies precisam de namespace (`cobblemon:charmander`, `addon:species`); tipos usam nome simples (`fire`). Dificuldades: EASY, MEDIUM, HARD. Ações: CAPTURE, DEFEAT. Pool aleatório permite repetições quando há mais slots que modelos. No modo fixo, percorre o pool em ordem.

Defaults: reset às 00:00 em America/Sao_Paulo; semana inicia segunda-feira no mesmo horário; calendário de 7 dias; metas permanente de 30 min, diária de 1h e semanal de 6h; AFK de 300 segundos; 3 Hunts diárias e 2 semanais; itens vanilla como recompensas funcionais do núcleo.

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
