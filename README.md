# Cobre TV

![Banner do Cobre TV](app/src/main/res/drawable/banner.png)

Aplicativo em **protótipo — versão 0.2.0** para Android TV e Google TV. Esta versão configura o endereço do Cobre Server e pareia a TV pelo Cobre ID usando um código aprovado no Mobile. A biblioteca ainda usa dados de exemplo e não reproduz músicas do Desktop.

## O que existe hoje

- Manifesto para Android TV, navegação por controle remoto e banner de início.
- Interface Compose com grade de álbuns de demonstração e barra “tocando agora”.
- Instância do Media3 ExoPlayer com liberação no ciclo de vida da tela, mas ainda sem faixa ou `MediaItem`.
- Ícone e banner Cobre incluídos nos recursos do Android.
- Configuração do endereço do Cobre Server, limitada a HTTPS ou IPv4 privado quando usar HTTP local.
- Pareamento Cobre ID: solicita código curto à API, apresenta-o na TV, aguarda aprovação autenticada em outro Cobre e resgata uma única sessão.
- Refresh token da TV guardado cifrado com chave vinculada ao Android Keystore; remover a TV pelo Cobre ID encerra sua sessão.

## O que falta para ser utilizável

- Localizar um Desktop autorizado e consultar a biblioteca real.
- Carregar capas e metadados, tocar áudio vindo do Desktop e exibir estados de conexão.
- Implementar fila, controles, retomada, tratamento de erros e configurações de reprodução.
- Testar navegação e playback em dispositivo Android TV/Google TV físico.
- Testar o pareamento com uma instalação real do Cobre Server e autorizar pelo Mobile; compilação sozinha não verifica tráfego entre aparelhos.

## Compilar

Requer JDK 17 e Android SDK com plataforma 34. No Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

O APK de depuração aparece em `app/build/outputs/apk/debug/app-debug.apk`. Esse APK não é uma release assinada para distribuição.

## Versões e créditos

Consulte [`CHANGELOG.md`](CHANGELOG.md) para as notas da versão e [`docs/REGISTRO-DE-TRABALHO.md`](docs/REGISTRO-DE-TRABALHO.md) para validações e pendências. Projeto de Bruno C S Moraes, com contribuições de [@dilucas00](https://github.com/dilucas00).
