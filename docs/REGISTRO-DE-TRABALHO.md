# Registro de trabalho — Cobre TV

## 2026-11-19 — protótipo 0.1.0

- Fonte importada do pacote local `cobre-tv-starter.zip`.
- A primeira compilação na pasta correta encerrou por GC thrashing: `gradle.properties` tinha o prefixo inválido `-e`, então o Gradle ignorava o heap de 2 GB. Corrigida para heap de 2 GB e metaspace de 1 GB.
- Adicionado Gradle Wrapper 8.9 com SHA-256 oficial da distribuição fixado em `gradle-wrapper.properties`.
- Removida a permissão `INTERNET` do manifesto enquanto o protótipo não implementa acesso de rede.
- Build `:app:assembleDebug` passou tanto pelo Gradle local quanto pelo Wrapper 8.9: 35 tarefas. APK debug com 9.214.200 bytes; SHA-256 `a5a2e5074d2eef32aaf07429f35c42b6551ef655d04b4a75e4ba2f408997b3fb`.
- Uma tentativa inicial foi executada na pasta externa ao projeto Gradle e descartada; todas as validações válidas ocorreram na pasta que contém `settings.gradle.kts`.
- Sem teste em emulador ou TV física. Código ainda usa dados de demonstração e não reproduz áudio.
- Logo e banner já estão dentro de `app/src/main/res`; @dilucas00 citado no crédito e nas notas.

## Pendências antes de uma release funcional

- Criar o repositório GitHub Cobre-TV e publicar esta base com build automatizado.
- Implementar pareamento Cobre ID e descoberta/autorização de um Desktop.
- Implementar catálogo, capas, playback Media3, fila e estados de erro/recuperação.
- Criar release assinada e testar em Android TV/Google TV físico com controle remoto.
