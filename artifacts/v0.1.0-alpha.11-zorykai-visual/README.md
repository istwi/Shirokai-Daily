# Zorykai — variante visual do alpha.11

Base: commit ba30640bff8deafaaa5d1e405a8d243ea10b3d1d, versão mais recente da única branch remota main na verificação.

Somente o JAR foi adaptado. Código-fonte do mod permanece intacto. A versão técnica continua 0.1.0-alpha.11, o ID continua shirokai_daily; instale somente um dos dois JARs.

Menus: substituição visual Shirokai → Zorykai e SHIROKAI → ZORYKAI na renderização, preservando os códigos de formatação e configurações existentes. Nome visual do mod e texto de status também atualizados.

Chat de resgate de Daily, Playtime e Hunts: prefixo ✦ ZORYKAI branco e negrito, seguido de texto cinza sem negrito. Mantido o resultado persistido: bônus aparece apenas quando sorteado. Entregas pendentes continuam pendentes.

Verificação: somente Menus.class, ShirokaiDaily.class e o campo name do fabric.mod.json diferem do original. Bibliotecas aninhadas e núcleo permanecem idênticos. ASM BasicVerifier aprovado; testes com os objetos Text reais do Minecraft verificaram prefixo, cores, negrito e preservação da formatação de menus. Sem teste com servidor/cliente em jogo.

Não substitui IDs, comandos, permissões, namespaces, pacotes, classes, placeholders ou pastas de dados/configuração. Checksum em SHA256SUMS.
