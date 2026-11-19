# Histórico de versões — Cobre TV

## 0.1.0 — protótipo inicial

- Base Android TV/Google TV em Kotlin, Jetpack Compose for TV e Media3.
- Manifesto Leanback, navegação D-pad, grade visual de álbuns de exemplo e barra inferior de reprodução.
- Logo, ícone e banner do Cobre integrados ao pacote Android.
- Corrigida a configuração de memória do Gradle, que estava escrita com o prefixo inválido `-e` e fazia o processo encerrar por thrashing do garbage collector.
- Incluído Gradle Wrapper 8.9 para builds reproduzíveis.
- `:app:assembleDebug` compilado localmente com sucesso; APK de debug não testado em aparelho e não representa uma release funcional.
- Contribuições: [@dilucas00](https://github.com/dilucas00).

### Limites conhecidos

Esta versão ainda usa `FakeMusicSource`, não adiciona mídia ao ExoPlayer e não implementa Cobre ID, streaming, biblioteca real, fila nem reprodução funcional.
