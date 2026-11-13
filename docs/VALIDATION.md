# Validação

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
