# Validação

## Compatibilidade Daily + Raids — 0.1.0-alpha.7

Build/remapJar passaram; 91 verificações do Daily e 125 testes do Raids 0.1.20-dev auditado passaram, sem falhas, erros ou ignorados. O Raids foi recompilado sem alterações no commit `1a5e9ab1f0f0372750ff8d7ae4bfdde03da0a92e`; os dois predicados públicos foram conferidos no JAR resultante. As regressões incluem os cenários obrigatórios de exclusão, continuidade de Hunts normais, falha isolada, múltiplos participantes, deduplicação e reinício. Detalhes, escopo de fixtures e limitações de validação em [RAIDS_COMPATIBILITY.md](RAIDS_COMPATIBILITY.md). Não foram executados ensaios com clientes reais ou sessões de jogo nesta alteração.

## Daily com bônus e metas até 5000h — 0.1.0-alpha.6

70 verificações passaram. Os testes adicionados comprovam um único bônus por resgate Daily, chances padrão 60/30/10, preservação do sorteio após reinício, migração da alpha.5 sem modificar o snapshot por UUID, ausência de bônus retroativo em entregas antigas, ocupação dos 28 slots da página, ordenação das metas, manutenção dos nove IDs antigos, bloqueio de novo resgate de meta antiga e preservação de recompensas personalizadas e backups em recargas repetidas. O limite máximo de configuração passou para 18.000.000 segundos (5000h).

O bônus é adicional ao prêmio fixo, não um prêmio para cada opção da lista. O tempo acumulado e as transações existentes são preservados. Não houve teste visual no cliente Minecraft nesta alteração.

## Menus e rotação horária — 0.1.0-alpha.5

Adicionados testes para: defaults cinza/30 dias/metas até 1000h/baú de 27 slots; ciclo completo de 30 dias; sorteio horário estável após reinício/reload; troca na fronteira da hora sem repetir alvos quando o pool permite; captura somente da espécie correta; resgate manual em Dollars; preservação de entregas pendentes após expiração; ausência temporária de registro sem sortear missões vazias; filtros e pool pequeno sem alvos duplicados; migração com backup sem tocar dados por UUID; preservação de configuração personalizada; migração de Hunts antigos preservando o conteúdo de entregas já sorteadas; limite máximo de meta.

O JAR oficial Cobblemon 1.8.3 foi obtido via HTTPS, conferido contra seu SHA-256 publicado e inspecionado com javap. Confirmadas as assinaturas de `PokemonItem.from(Species, String...)`, `Species.getImplemented()`, `Species.getLabels()` e `PokemonSpecies.getSpecies()`. Não houve teste visual de cliente Minecraft nem teste nativo Windows nesta sessão. As versões publicadas anteriormente são preservadas; esta alteração usa a numeração alpha.5.

## Correção Windows — 0.1.0-alpha.2

A abertura de diretório por FileChannel era rejeitada no Windows após o rename do JSON. A correção evita somente essa operação não suportada no Windows, mantendo fsync do arquivo, rename atômico e propagação de falhas reais. Foram adicionados testes de substituição/leitura pelo provider nativo, resgate/playtime/reinício sem directory fsync e falha real de escrita com preservação do snapshot anterior. A suíte passou a ter 42 verificações e a build Fabric alpha.2 passou. O workflow [Core tests](https://github.com/istwi/Shirokai-Daily/actions/runs/43402393985), commit `9d9b4bc60296d651df72f539b246f0795aa45940`, concluiu com sucesso os testes em Windows e Linux. A verificação remota do JAR também confirmou o SHA-256 `9e7fbb3b0772524b309a53ba9594021f6c6a4cd02611e276814e324d58f31382`.

## Build da pré-release alpha em 13/11/2026

No ambiente publicado, a build completa `./gradlew build` passou, incluindo compilação Fabric e `:server:remapJar`. Foi corrigida a configuração `withSourcesJar()` necessária à tarefa de fontes. Os 39 testes/verificações foram executados novamente por `./gradlew :test --rerun-tasks`, com zero falhas/erros/ignorados. O JAR foi inspecionado: ID `shirokai_daily`, environment `server`, versão 0.1.0-alpha.1, entrypoint presente e núcleo incluído como dependência aninhada. O sources JAR não contém descritor carregável. O SHA-256 acompanha o binário.

**Não foram executados testes com cliente/servidor Minecraft nesta validação.** A publicação é uma pré-release alpha, sem alegação de estabilidade ou validação das integrações em jogo. A extensão de API Crates continua pendente de aplicação/teste.

## Histórico da sessão inicial

Executado nesta máquina, Java 21, Gradle 8.10.2:

- `./gradlew -PcoreOnly test`: 39 verificações (38 de comportamento, 1 de sintaxe), 0 falhas, 0 erros, 0 ignorados.
- `./gradlew -PcoreOnly generateExamples`: gerou os nove arquivos JSON pelo caminho real de configuração.
- `bash scripts/cloud-setup.sh --core-only`: instalação/ativação repetível e testes verificados.
- Build do núcleo Java: sucesso. Não é artefato Fabric.
- Build completa: falhou ao resolver Fabric Loom 1.8.13. Curl confirmou HTTP 403 no CONNECT para maven.fabricmc.net. Nenhum bypass de TLS/checksum/política foi usado.

Testes cobrem calendário e limite de dia São Paulo; RESET/PAUSE/GRACE; VIP; RESTART/STOP; resgate único; reinício do Engine/Store; permissões; elegibilidade AFK; sessões/reconexão/dimensão; períodos/metas independentes; divisão de amostra no reset; geração/progresso/claim de Hunts; eventos não confirmados, próprios, lendários e duplicados; dedup após reset; ledger em EXECUTING sem reenvio; PENDING/DONE; JSON corrompido, reload e módulos desabilitados. São testes do núcleo, não testes Fabric/Cobblemon/GUI.

## Ainda obrigatório antes de release

1. Build completa e remapeamento concluídos na sessão da pré-release; preservar essa verificação em futuras alterações.
2. Confirmar e obter Cobblemon Fabric 1.8.3 oficial, conferir checksum, dependências e eventos nessa versão; testar ponte/reflexão, registro de addons, reload de espécies e falhas controladas.
3. Iniciar servidor com `./gradlew :server:runServer` depois de instalar Cobblemon e dependências oficiais em `server/run/mods`. Aceitar o EULA somente após leitura pelo responsável. Nenhum servidor foi iniciado e nenhum EULA foi aceito nesta sessão.
4. Cliente sem Shirokai Daily: conectar, abrir os três menus e testar todas as ações de inventário incluindo drag, número/hotbar, shift, drop, double-click, inventário cheio, fechar durante entrega e mudança de tela.
5. Validar comandos online/offline UUID e LuckPerms com permissões concedidas/revogadas e VIP durante resgate.
6. Validar o adaptador Impactor em servidor; aplicar/revisar a extensão de API no Crates e testar moeda, chave, falhas, timeout e idempotência. Testar o comando administrativo de reconciliação em servidor.
7. Ensaiar reset diário/semanal, clock adjustment, reload, reinício e queda entre PREPARE/EXECUTING/efeito/DONE, incluindo persistência de inventário vanilla.
8. Testes de blacklist/whitelist, filtros de espécies/tipos, pool ponderado e retenção passaram no núcleo; testar exclusões e exploração em batalhas reais.
9. Validar configuração de menus no cliente e, quando necessário, integrar as fontes opcionais de perfil. Medir carga e crescimento de dedup/transações para 100 jogadores antes de alegar essa capacidade.
10. Gerar JAR remapeado, validar instalação limpa e só então publicar release no GitHub se houver acesso de publicação. Nenhuma release foi tentada com artefato não validado.

Resultados JUnit da execução local: `build/reports/tests/test/index.html` e `build/test-results/test/`. Esses diretórios são gerados e ignorados pelo Git.

## Evidências adicionais das integrações

- JAR recebido: Impactor-Fabric-5.3.5+1.21.1.jar, SHA-256 `04e118e262892e516409dd631739048df09b197150a4ef5d7d3d245ac6ff7f74`.
- `scripts/verify-impactor-api.py <jar>` passou: versão e assinaturas públicas de EconomyService, CurrencyProvider, Account e EconomyTransaction; não executa o mod.
- Seis testes do fluxo Impactor usam um provider controlado: moeda ausente, persistência ainda pendente, falha no depósito, falha de gravação, despacho para a thread de jogo e moeda não permitida. Não provam depósito no provider real.
- `git apply --check integrations/shirokai-crates-grant-fragments.patch` passou contra Shirokai-Crates `05b3995c4d4466ef17c6171ad07d4f0eab512db9`. O patch não foi aplicado ao repositório remoto nem compilado.
- O teste de sintaxe percorre os fontes Fabric em Java 21; não verifica tipos, mapeamentos, carregamento ou comportamento Minecraft.
