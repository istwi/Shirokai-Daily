# Recompensas do servidor — Daily alpha.8

Perfil opcional com as tabelas fornecidas pelo administrador. Não é aplicado automaticamente a servidores existentes.

## Instalação

1. Pare o servidor e faça backup de `config/shirokai-daily` e `shirokai-daily-data`.
2. Substitua o JAR antigo pelo `shirokai_daily-0.1.0-alpha.8.jar`. Mantenha somente um JAR do Daily.
3. Copie os quatro JSONs desta pasta `config/shirokai-daily` para a pasta de mesmo nome do servidor. Preserve os demais arquivos, menus e dados dos jogadores.
4. Inicie o servidor. Se for uma instalação nova, o Daily gera os arquivos complementares ausentes.

Compatibilidade: Fabric Minecraft 1.21.1, Java 21, Cobblemon 1.8.3. Economia: Impactor 5.3.5, moedas `impactor:dollars` e `impactor:tokens`. VIP: LuckPerms com grupos existentes `magma`, `aqua`, `plasma`.

## Prêmios

São 30 dias de Daily e 28 metas permanentes de Playtime entre 1h e 5.000h. Todas as recompensas fixas são entregues, mais somente uma alternativa ponderada 60/30/10. Recompensas fixas compostas não são sorteadas entre si. Os IDs de metas existentes são preservados; metas já retiradas não podem ser retiradas novamente e transações antigas mantêm os prêmios originais.

Nas 5.000h: um lendário aleatório shiny com os seis IVs 31 e 500 Tokens fixos; bônus de 750 Tokens (60%), sete chaves Shiny (30%) ou nenhum bônus (10%). O VIP permanente foi retirado por solicitação do administrador. A opção vazia usa peso 10 e chance 0: não entrega papel e não redistribui probabilidades.

O lendário vem das espécies implementadas com label `legendary` registradas pelo Cobblemon, sem incluir míticos somente pelo label `mythical`. A API pública coloca no time ou no PC. O UUID do presente é derivado da transação e consultado no time/PC antes da entrega; não é captura e não gera progresso de Hunts. Cobblemon administra o salvamento dos seus próprios depósitos. Exceções após iniciar a entrega ficam incertas e não são repetidas automaticamente.

Os VIPs temporários usam `lp user {uuid} parent addtemp <grupo> <dias>d accumulate`. Uma duração temporária do mesmo grupo é acumulada. Não removemos outros grupos nem rebaixamos VIPs permanentes.

## Caixas

Entrega de **chaves**, usando a fábrica pública de itens assinados do Shirokai Crates 0.2.1 (`Tokens.create`/`matches`) e seu snapshot público. O Daily não executa `givekey` como jogador, não dá permissões administrativas, não fabrica assinaturas e não cria nem substitui o segredo do Crates. A fábrica original acessa o segredo já existente do próprio mundo. Inventário insuficiente, caixa inexistente ou API indisponível deixam a entrega pendente, sem substituir o prêmio.

IDs: Básica `inicial_rara`; Épica `caixa_epica`; Paradoxo `caixa_paradoxo`; Ultra Beast `caixa_ultra_beasts`; Mítica `caixa_mitica`; Lendária `caixa_lendaria`; Shiny `shiny`. Os IDs precisam existir exatamente assim no Crates do servidor. O ID da Básica foi confirmado por texto, mesmo que a imagem mostre outro prefixo.

A entrega de chaves usa o salvamento normal do inventário do Minecraft, assim como os itens comuns do Daily. Não equivale a uma transação atômica entre os arquivos de progresso e de inventário; falhas abruptas entre salvamentos podem exigir conferência administrativa. Entregas com resultado incerto não são repetidas automaticamente.

## Hunts

Três missões por rotação de uma hora, Pokémon aleatórios, sem lendários. Cinco dificuldades sorteadas com pesos 8/5/3/2/1, exigindo respectivamente 1/2/3/4/5 capturas. Esses requisitos são escolhas do perfil, editáveis no `hunts.json`; os prêmios seguem exatamente a tabela. O filtro de Raids da alpha.7 é preservado.

O arquivo `recompensas-planejadas.json` é a tabela de origem para revisão, não uma configuração executável. Somente os quatro JSONs na pasta `config/shirokai-daily` são instalados. `scripts/generate-user-rewards.py` reproduz o perfil.

## Validação

A build compila contra a API real do Cobblemon 1.8.3 e executa testes do perfil, sorteios, persistência de claims e compatibilidade de Raids. A fábrica pública do Crates foi conferida no código 0.2.1. Não houve teste em servidor com jogadores; valide as entregas no seu ambiente antes de disponibilizar as recompensas.
