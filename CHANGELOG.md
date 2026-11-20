# Histórico de versões — Cobre TV

## 0.1.0 — protótipo inicial

- Base Android TV/Google TV em Kotlin, Jetpack Compose for TV e Media3.
- Manifesto Leanback, navegação D-pad, grade visual de álbuns de exemplo e barra inferior de reprodução.
- Logo, ícone e banner do Cobre integrados ao pacote Android.
- Corrigida a configuração de memória do Gradle, que estava escrita com o prefixo inválido `-e` e fazia o processo encerrar por thrashing do garbage collector.
- Incluído Gradle Wrapper 8.9 para builds reproduzíveis.
- Removida a permissão de Internet não utilizada nesta base sem recursos de rede.
- `:app:assembleDebug` compilado localmente com sucesso; APK de debug não testado em aparelho e não representa uma release funcional.
- Contribuições: [@dilucas00](https://github.com/dilucas00).

### Build do repositório (2026-11-20)`r`n`r`n- Corrigida a permissão executável do `gradlew` para o GitHub Actions em Linux. A compilação automatizada do APK debug passou no workflow [44684235826](https://github.com/bruncosta077-boop/Cobre-TV/actions/runs/44684235826). Isso valida a compilação, não adiciona recursos ao app.`r`n`r`n### Limites conhecidos

Esta versão ainda usa `FakeMusicSource`, não adiciona mídia ao ExoPlayer e não implementa Cobre ID, streaming, biblioteca real, fila nem reprodução funcional.
