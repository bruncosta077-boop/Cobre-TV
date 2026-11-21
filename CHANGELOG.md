# Histórico de versões — Cobre TV

## 0.3.0 — biblioteca Desktop e reprodução direta (protótipo)

- Substitui álbuns de demonstração por catálogo real do Desktop, agrupado por artista/álbum e com capas autorizadas.
- A TV lista Desktops da conta Cobre ID, pede acesso por aparelho com escopos explícitos e aguarda aprovação no Desktop.
- Tokens curtos de acesso ao Desktop ficam cifrados no Android Keystore e são renovados durante a sessão.
- Reproduz a fila do álbum no Media3 ExoPlayer com controles anterior, pausar/tocar e próxima; as requisições carregam o token no cabeçalho e o áudio segue direto do Desktop à TV.
- O Cobre Server não retransmite áudio. HTTP continua restrito a endereço IPv4 privado; HTTPS usa validação normal de certificado do Android.
- `:app:assembleDebug` e `:app:lintDebug` passaram localmente. `:app:testDebugUnitTest` concluiu sem fontes de teste (`NO-SOURCE`); ainda falta teste em TV física e o APK é de depuração, não release assinada.

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
