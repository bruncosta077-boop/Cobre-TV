# Registro de trabalho — Cobre TV

## 2026-11-21 — base de biblioteca e streaming direto 0.3.0

- Troquei o catálogo fictício por uma conexão real: a TV lista Desktops autenticados na conta, solicita escopos `library.read`, `playback.read`, `playback.control` e `stream`, aguarda a aprovação no Desktop e consulta a biblioteca autorizada.
- A grade agrupa faixas por artista/álbum e busca as capas do Desktop. Ao escolher um álbum, o Media3 ExoPlayer monta a fila e toca os streams diretamente do computador, com comandos de anterior, pausar/tocar e próxima.
- O token curto da concessão é guardado cifrado no Android Keystore, enviado no cabeçalho `Authorization` e renovado junto com a sessão Cobre ID. O Server continua fora do caminho dos bytes de áudio.
- HTTP local aceita somente IPv4 privado; redirecionamentos são recusados. Respostas do catálogo e capas têm limite de tamanho. Nenhuma URL contém token.
- Build local `:app:assembleDebug` e análise Android `:app:lintDebug` passaram. `:app:testDebugUnitTest` concluiu sem fontes (`NO-SOURCE`); ainda não há testes unitários escritos. O primeiro lint apontou um acesso de API indisponível no minSdk e chamadas Media3 sem marcação; os pontos foram corrigidos e o lint repetido passou.
- O workflow Windows/Linux do repositório passou para o commit inicial da mudança no [run 44942603614](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44942603614) e para as correções de compatibilidade no [run 44942938826](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44942938826), que publicou o APK debug como artefato temporário de CI.
- Continua sem validação em Android TV/Google TV físico. Também falta testar pareamento, autorização, imagem e stream entre instalações reais; não publicar como release estável com base apenas no build.

## 2026-11-20 — pareamento Cobre ID 0.2.0

- Adicionados endereço do Cobre Server, solicitação/exibição do código de pareamento, espera por aprovação no cliente autenticado e resgate único da sessão.
- Refresh token da TV é guardado cifrado com chave do Android Keystore; HTTP só é aceito para IPv4 privado e HTTPS usa a confiança TLS do Android.
- Build de APK debug passou no GitHub Actions [44743655894](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44743655894) após adicionar a dependência/imports de Material 3 para os diálogos.
- Ainda não houve teste em TV física nem troca real com Server/Mobile. A biblioteca é demonstrativa, não reproduz áudio, e não há assinatura de distribuição nem release publicada.

## 2026-11-19 — protótipo 0.1.0

- Fonte importada do pacote local `cobre-tv-starter.zip`.
- A primeira compilação na pasta correta encerrou por GC thrashing: `gradle.properties` tinha o prefixo inválido `-e`, então o Gradle ignorava o heap de 2 GB. Corrigida para heap de 2 GB e metaspace de 1 GB.
- Adicionado Gradle Wrapper 8.9 com SHA-256 oficial da distribuição fixado em `gradle-wrapper.properties`.
- Removida a permissão `INTERNET` do manifesto enquanto o protótipo não implementa acesso de rede.
- Build `:app:assembleDebug` passou tanto pelo Gradle local quanto pelo Wrapper 8.9: 35 tarefas. APK debug com 9.220.558 bytes; SHA-256 `c12c10a9bbf4a8fc36a82ba1a112d282540e1542f84bfe3122cb0bacd3efa5fc` após remover a permissão de rede não utilizada.
- Uma tentativa inicial foi executada na pasta externa ao projeto Gradle e descartada; todas as validações válidas ocorreram na pasta que contém `settings.gradle.kts`.
- Sem teste em emulador ou TV física. Código ainda usa dados de demonstração e não reproduz áudio.
- Logo e banner já estão dentro de `app/src/main/res`; @dilucas00 citado no crédito e nas notas.

## Pendências para tornar a TV utilizável

- Descobrir Desktop autorizado e carregar sua biblioteca e capas reais.
- Reproduzir áudio do Desktop com fila, controles, recuperação e estados de erro.
- Testar pareamento e reprodução em Android TV/Google TV físico.
- Criar fluxo de assinatura estável e publicação Android TV depois que atualização e reinstalação estiverem validadas.
