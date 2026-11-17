# Redução adicional das recompensas

Base: ZIP `shirokai-daily-nerf-50.zip` fornecido pelo administrador.

- Dinheiro do Playtime: mais 50% de redução sobre os valores do ZIP original.
- Dinheiro do Daily e Hunts: mais 40% de redução.
- Chaves fixas e bônus removidas dos dias 1–10 e dos primeiros dez marcos do Playtime: 1, 2, 3, 5, 8, 12, 18, 24, 36 e 48 horas.
- Tokens, demais itens e configurações dos outros arquivos preservados.

Pare o servidor e faça backup. Substitua apenas `daily.json`, `playtime.json` e
`rewards.json` em `config/shirokai-daily/` pelos arquivos desta pasta e reinicie.
Preserve os dados dos jogadores. Prêmios já sorteados e pendentes não são recalculados.

O ZIP contém a configuração completa enviada, com somente esses três JSONs
alterados. Não é necessário substituir o JAR para aplicar este ajuste.
JSONs, referências de recompensas e ausência de chaves nas etapas iniciais foram validados.
