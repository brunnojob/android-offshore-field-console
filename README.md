# Offshore Field Console

Aplicativo Android para inspeções com checklist obrigatório, referência de evidência, banco Room e sincronização autenticada de eventos.

## Executar

Requisitos: Android SDK, Kotlin e Jetpack Compose.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Funcionamento

A interface registra os dados informados pelo operador e conserva os eventos offline. Configure a sessão pelo token do arquivo de operações. Envios exigem HTTPS e confirmação de persistência. `ArchiveWorkerFactory` permite integrar WorkManager com um provedor de sessão. A execução em dispositivo ainda precisa de validação.

## Persistência de resultados

O arquivo de operações está em [vercel-home-telemetry-api.vercel.app](https://vercel-home-telemetry-api.vercel.app/laboratory.html?project=android-offshore-field-console). As migrações Supabase estão no [repositório da API](https://github.com/brunnojob/vercel-home-telemetry-api/tree/main/supabase/migrations).

```sh
python cloud/sync.py enqueue resultado.json --project android-offshore-field-console
python cloud/sync.py sync
```

Defina `BRUNNODEV_ACCESS_TOKEN` com sua sessão. A fila SQLite conserva os relatórios até confirmação do servidor; o mesmo conteúdo não gera registros duplicados. Tokens não são gravados no código.
