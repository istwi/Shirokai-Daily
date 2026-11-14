# Entrega de versões

Por solicitação do usuário, cada nova versão compilada do JAR deve ser enviada ao GitHub de `istwi/Shirokai-Daily`.

Antes de enviar, consulte a versão remota para preservar alterações feitas em outras sessões. Integre essas alterações, use um número novo, execute a build/testes e confira o descritor Fabric, a dependência aninhada e o SHA-256. Não substitua tags ou artefatos existentes.

Publique como pré-release enquanto a validação funcional em Minecraft estiver pendente, descrevendo as limitações reais. Se o upload de anexos não estiver autorizado, versione o JAR em `artifacts/v<versão>/` com seu checksum e inclua na release um link imutável para o commit. Confira o conteúdo baixado do GitHub contra o checksum local.

Essa autorização diz respeito ao código/JAR no GitHub. Não publica nem altera a configuração do ambiente da nuvem.
