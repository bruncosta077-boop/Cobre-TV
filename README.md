# Cobre TV

![Banner do Cobre TV](app/src/main/res/drawable/banner.png)

Aplicativo em **protótipo — versão 0.1.0** para Android TV e Google TV. A base já compila; ainda não reproduz músicas nem se conecta ao Cobre Desktop ou ao Cobre ID.

## O que existe hoje

- Manifesto para Android TV, navegação por controle remoto e banner de início.
- Interface Compose com grade de álbuns de demonstração e barra “tocando agora”.
- Instância do Media3 ExoPlayer com liberação no ciclo de vida da tela, mas ainda sem faixa ou `MediaItem`.
- Ícone e banner Cobre incluídos nos recursos do Android.

## O que falta para ser utilizável

- Parear a TV pelo Cobre ID e revogar/autenticar o aparelho.
- Localizar um Desktop autorizado e consultar a biblioteca real.
- Carregar capas e metadados, tocar áudio vindo do Desktop e exibir estados de conexão.
- Implementar fila, controles, retomada, tratamento de erros e configurações de reprodução.
- Testar navegação e playback em dispositivo Android TV/Google TV físico.

## Compilar

Requer JDK 17 e Android SDK com plataforma 34. No Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK de depuração aparece em `app/build/outputs/apk/debug/app-debug.apk`. Esse APK não é uma release assinada para distribuição.

## Versões e créditos

Consulte [`CHANGELOG.md`](CHANGELOG.md) para as notas da versão e [`docs/REGISTRO-DE-TRABALHO.md`](docs/REGISTRO-DE-TRABALHO.md) para validações e pendências. Projeto de Bruno C S Moraes, com contribuições de [@dilucas00](https://github.com/dilucas00).
