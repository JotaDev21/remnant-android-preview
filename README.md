# REMNANT para Android · Preview

Código da edição de demonstração do REMNANT, um app Android para registrar treinos, consultar o histórico de sessões e acompanhar a progressão por patentes. O app foi reconstruído em Kotlin e Jetpack Compose a partir da versão instalada.

## Escopo desta edição

A build usa um identificador Android próprio e começa com armazenamento local. A Arena, o login do serviço oficial e a sincronização com o banco de produção ficam desativados nesta edição. O mentor pode ser configurado separadamente com uma chave do próprio usuário. O atualizador da distribuição oficial fica desativado na preview. A distribuição oficial do aplicativo está no repositório [REMNANT Updates](https://github.com/JotaDev21/remnant-updates).

O código utiliza Room para persistência local. O app inclui integração com Health Connect, contador de repetições por câmera e registro de cardio. A disponibilidade dessas funções depende do aparelho e das permissões concedidas.

## Compilar e testar

Use JDK 17, Android SDK 36 e Gradle 9.3.1. Na raiz do projeto, execute:

    gradle :app:testPreviewDebugUnitTest :app:assemblePreviewDebug

O APK de demonstração é gerado em app/build/outputs/apk/preview/debug/.

Consulte [recursos de terceiros](THIRD_PARTY_ASSETS.md) para os créditos dos recursos incluídos. A animação de ascensão usa um vídeo criado com Seedance.
