# Histórico de versões — Cobre TV

## 0.7.0-rc.1 — conexões simples e acesso remoto por Tailscale

- Reúne Server, Cobre ID e Desktop em uma única tela “Conexões”, com instruções diretas, estado da conta, descoberta local e escolha do computador.
- Adiciona suporte a endereços Tailscale IPv4 (`100.64.0.0/10`) e MagicDNS (`*.ts.net`) nos campos de Server e Desktop. HTTP público continua bloqueado; HTTPS mantém a validação normal de certificado.
- Explica na própria TV que, fora da rede local, os dois aparelhos precisam estar no mesmo tailnet e que o áudio segue direto do Desktop para a TV.
- Adiciona testes locais para hosts permitidos, bloqueio de HTTP público e rejeição de URL com credenciais, caminho, query ou fragmento.
- CI agora executa testes unitários, lint e build antes de guardar o APK de depuração.
- `:app:testDebugUnitTest`, `:app:lintDebug` e `:app:assembleDebug` passaram localmente; são 3 testes unitários. O lint mantém avisos sobre target Android 34, versões mais novas de dependências e densidade do banner.
- Ainda requer validação em Android TV/Google TV real. O Tailscale precisa estar instalado e conectado nos dois aparelhos; não há relay de áudio nem APK de distribuição assinado.

## 0.6.0 — evitar conflito ao renovar sessão Cobre ID

- A renovação da sessão é serializada para impedir que a presença e a renovação programada da autorização Desktop reutilizem ao mesmo tempo o mesmo refresh token rotativo.
- Cada renovação lê a versão atual do token sob a trava e mantém intacta a detecção do Server a reuso real de tokens.
- Build debug e lint passaram localmente e no [GitHub Actions](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/45052591367). A TV continua protótipo; publicação estável depende de assinatura e validação física.

## 0.5.0 — presença e diagnóstico do áudio

- A TV envia presença Cobre ID a cada 20 segundos enquanto o app está aberto, para aparecer online no painel do Server.
- A TV mede latência inicial e taxa de bytes somente na rota de áudio autorizada do Desktop; envia métricas agregadas junto da presença, sem títulos, caminhos ou conteúdo.
- Renovação de sessão ocorre antes do vencimento do token usado para enviar presença. O envio não altera concessões ou aprovações existentes.
- As medições usam o mesmo contrato do Mobile e o Server pode marcar sua idade; dados antigos deixam de ser apresentados como velocidade atual.
- `:app:assembleDebug` e `:app:lintDebug` passaram localmente após habilitar a geração de `BuildConfig`. TV física continua necessária para confirmar heartbeat e áudio real.
- O workflow do GitHub compilou a versão e publicou um APK debug temporário no [run 44949012682](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44949012682).
- O pacote TV permanece APK de depuração até haver chave estável e validação em Android TV/Google TV.

## 0.4.0 — encontrar computadores na rede local

- A TV procura automaticamente o Cobre Desktop na rede local enquanto a tela de conexão está aberta; o endereço manual continua disponível na TV e QR/endereço manual seguem disponíveis no Mobile.
- A descoberta anuncia apenas identificador do Desktop e versões do protocolo, sem token ou credenciais. Encontrar um computador não concede acesso: o Cobre ID ainda solicita e exige aprovação no Desktop.
- Os bytes de áudio continuam indo diretamente do Desktop para a TV; o Cobre Server coordena conta, presença e autorização.
- `:app:assembleDebug` e `:app:lintDebug` passaram localmente. O lint reporta apenas avisos de APIs Android NSD antigas/depreciadas nesta implementação. Sem Android TV física, descoberta multicast e reprodução real entre aparelhos ainda não foram validadas.
- GitHub Actions também compilou o commit e guardou o APK debug como artefato temporário no [run 44946435962](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44946435962).
- Esta é uma compilação de desenvolvimento/debug, não uma release assinada para distribuição.

## 0.3.0 — biblioteca Desktop e reprodução direta (protótipo)

- Substitui álbuns de demonstração por catálogo real do Desktop, agrupado por artista/álbum e com capas autorizadas.
- A TV lista Desktops da conta Cobre ID, pede acesso por aparelho com escopos explícitos e aguarda aprovação no Desktop.
- Tokens curtos de acesso ao Desktop ficam cifrados no Android Keystore e são renovados durante a sessão.
- Reproduz a fila do álbum no Media3 ExoPlayer com controles anterior, pausar/tocar e próxima; as requisições carregam o token no cabeçalho e o áudio segue direto do Desktop à TV.
- O Cobre Server não retransmite áudio. HTTP continua restrito a endereço IPv4 privado; HTTPS usa validação normal de certificado do Android.
- `:app:assembleDebug` e `:app:lintDebug` passaram localmente. `:app:testDebugUnitTest` concluiu sem fontes de teste (`NO-SOURCE`); ainda falta teste em TV física e o APK é de depuração, não release assinada.
- O GitHub Actions compilou a versão após as correções no [run 44942938826](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44942938826) e guardou o APK debug como artefato temporário de CI.

## 0.2.0 — pareamento Cobre ID

- Adiciona configuração e teste do endereço base do Cobre Server.
- A TV solicita um código de uso único, mostra-o na tela e consulta o servidor até ser aprovada por outro cliente autenticado.
- Após aprovação, troca o código por uma sessão Cobre ID e guarda o refresh token cifrado com Android Keystore. A ação Sair encerra o dispositivo no servidor.
- HTTP é aceito somente para IPv4 privado; HTTPS pode usar host DNS com certificado válido. Senhas de conta não são pedidas nem armazenadas na TV.
- A biblioteca permanece com álbuns demonstrativos; não há acesso à biblioteca Desktop nem reprodução real nesta versão.
- Compilação APK debug passou no GitHub Actions, execução `44743655894` (commit `bfceba4`). Isso comprova o build, não a comunicação com um Server real.
- O pareamento ainda precisa ser testado em TV física com o Server e o Mobile reais; não há release assinada para distribuição.

## 0.1.0 — protótipo inicial

- Base Android TV/Google TV em Kotlin, Jetpack Compose for TV e Media3.
- Manifesto Leanback, navegação D-pad, grade visual de álbuns de exemplo e barra inferior de reprodução.
- Logo, ícone e banner do Cobre integrados ao pacote Android.
- Corrigida a configuração de memória do Gradle, que estava escrita com o prefixo inválido `-e` e fazia o processo encerrar por thrashing do garbage collector.
- Incluído Gradle Wrapper 8.9 para builds reproduzíveis.
- Removida a permissão de Internet não utilizada nesta base sem recursos de rede.
- `:app:assembleDebug` compilado localmente com sucesso; APK de debug não testado em aparelho e não representa uma release funcional.
- Contribuições: [@dilucas00](https://github.com/dilucas00).

### Build do repositório (2026-11-20)

- Corrigida a permissão executável do `gradlew` para o GitHub Actions em Linux. A compilação automatizada do APK debug passou no workflow [44684235826](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44684235826). Isso valida a compilação, não adiciona recursos ao app.

### Limites conhecidos

Esta versão ainda usa `FakeMusicSource`, não adiciona mídia ao ExoPlayer e não implementa Cobre ID, streaming, biblioteca real, fila nem reprodução funcional.
