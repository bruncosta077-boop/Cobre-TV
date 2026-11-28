# Registro de trabalho — Cobre TV

## 2026-11-28 — conexões centralizadas e Tailscale 0.7.0-rc.1

- Unifiquei a configuração do Cobre Server, sessão Cobre ID, Desktop encontrado na rede, endereço manual e autorização em uma tela “Conexões”.
- HTTP agora aceita endereços IPv4 privados, endereços Tailscale `100.64.0.0/10` e MagicDNS `*.ts.net`; HTTP público continua recusado e HTTPS mantém a validação de certificado do Android.
- A tela explica a instalação/conexão do Tailscale nos dois aparelhos e que a mídia segue diretamente do Desktop para a TV, sem relay.
- Adicionei testes unitários para a validação de endereços e atualizei o CI para executar testes, lint e build.
- `:app:testDebugUnitTest`, `:app:lintDebug` e `:app:assembleDebug` passaram localmente; 3 testes unitários passaram. Permanecem avisos informativos de target/dependências/banner no lint.
- APK debug gerado: 10.951.794 bytes, SHA-256 `B7A9B30D9D962CA4E36F647B677A7D0431B6C849CCE756BBD03EF4C8B46FB429`.
- Pendente: validação física da rede/stream em Android TV ou Google TV. A versão RC não é release assinada.

## 2026-11-22 — serialização da renovação Cobre ID 0.6.0

- Presença e renovação periódica do grant podem acordar perto da expiração do token Cobre ID; ambas passam a usar trava compartilhada do processo ao rotacionar a sessão.
- A trava envolve leitura do refresh token atual, chamada autenticada e salvamento do próximo token, para impedir replay acidental no Server.
- Build debug e lint local passaram em 22/11/2026; GitHub Actions passou no [workflow 45052591367](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/45052591367). Validação física e uma chave estável para distribuição continuam pendentes.

## 2026-11-21 — heartbeat e telemetria do stream 0.5.0

- A TV agora envia presença Cobre ID a cada 20 segundos enquanto o app está aberto; refresh da sessão ocorre antes de o token de acesso expirar.
- `TvStreamMetrics` acompanha somente `/v1/tracks/{id}/stream`, mede latência ao primeiro byte e vazão agregada, descarta medidas após dois minutos e não registra metadados pessoais.
- Métricas seguem no corpo autenticado de `/v1/presence`; o áudio permanece direto do Desktop para a TV.
- Build debug e lint passaram localmente após habilitar `BuildConfig`; teste físico em TV/Google TV ainda é necessário para confirmar presença, stream e estados de rede reais.
- O GitHub Actions compilou e guardou um APK debug temporário no [run 44949012682](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44949012682).

## 2026-11-21 — descoberta local 0.4.0

- Adicionada busca mDNS pelo serviço `_cobre._tcp` enquanto a tela de conexão está aberta. A TV filtra protocolo/API, UUID do Desktop e IPv4 local antes de oferecer um computador.
- Um Desktop encontrado usa o fluxo já existente de pedido Cobre ID e aprovação local; nenhum segredo passa pelo anúncio. A seleção e o endereço manual continuam disponíveis como alternativa.
- Compilação `:app:assembleDebug` e análise `:app:lintDebug` passaram localmente; o lint aponta somente depreciações da API Android `NsdManager` usada para resolver o serviço.
- O workflow GitHub compilou o commit e guardou APK debug temporário no [run 44946435962](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44946435962).
- Continua necessário validar descoberta, conexão, aprovação e áudio em uma TV física e na rede real. CI gera somente APK de depuração e não existe assinatura de distribuição para TV.

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

- Validar pareamento, descoberta local, autorização, capas, reprodução e reconexão Tailscale em Android TV/Google TV físico.
- Tratar mudança/perda de rede durante reprodução e permitir retomada do stream.
- Definir assinatura estável e distribuição para Android TV depois de confirmar atualização e reinstalação em dispositivo físico.
