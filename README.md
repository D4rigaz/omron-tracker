# Omron Tracker

App Android para registrar manualmente as medições da balança **Omron HBF-514C**
e sincronizá-las com o **Samsung Health** via **Health Connect**.

## Como funciona a sincronização

```
[Você digita os dados] → [Room: histórico local completo]
                       → [Health Connect: peso, %gordura, TMB, massa magra]
                       → [Samsung Health sincroniza do Health Connect]
```

Mapeamento dos 7 campos da balança:

| Campo Omron              | Health Connect              | Room (local) |
|--------------------------|-----------------------------|--------------|
| Peso (kg)                | `WeightRecord`              | ✓            |
| IMC                      | — (sem tipo nativo)         | ✓            |
| Gordura corporal (%)     | `BodyFatRecord`             | ✓            |
| Músculo esquelético (%)  | — (derivado em massa magra) | ✓            |
| Gordura visceral (1–30)  | — (sem tipo nativo)         | ✓            |
| Metabolismo repouso      | `BasalMetabolicRateRecord`  | ✓            |
| Idade corporal           | — (sem tipo nativo)         | ✓            |

A massa magra (`LeanBodyMassRecord`) é derivada: `peso × (1 − %gordura/100)`.

## Setup

### Opção A — Sem Android Studio (build na nuvem via GitHub Actions)

1. Crie um repositório no GitHub e envie o conteúdo desta pasta.
2. O workflow `.github/workflows/build-apk.yml` roda automaticamente no push
   (ou manualmente em **Actions → Build APK → Run workflow**).
3. Ao terminar (~3–5 min), baixe o artefato **omron-tracker-debug** na página
   da execução — dentro do zip está o `app-debug.apk`.
4. Transfira o APK para o celular (Quick Share, cabo ou Google Drive) e toque
   nele para instalar. O One UI pedirá para permitir instalação de fontes
   desconhecidas para o app usado na abertura.

### Opção B — Com Android Studio

1. Abra o projeto no Android Studio (Ladybug ou mais recente).
2. Sincronize o Gradle e rode no aparelho físico (Health Connect não funciona em emulador de forma confiável; no Android 14+ ele é parte do sistema).
3. No primeiro uso, toque em **"Conceder permissões do Health Connect"**.
4. No **Samsung Health**: Configurações → Health Connect → ative a sincronização e marque os tipos de dados (peso, gordura corporal etc.).

## Estrutura

```
app/src/main/java/com/darigaz/omrontracker/
├── MainActivity.kt              # Permissões HC + navegação por abas
├── data/
│   ├── Measurement.kt           # Entidade Room (7 campos + flag de sync)
│   ├── MeasurementDao.kt        # Insert, histórico, pendentes de sync
│   └── AppDatabase.kt
├── health/
│   └── HealthConnectManager.kt  # Disponibilidade, permissões, insertRecords
└── ui/
    ├── MeasurementViewModel.kt  # Estado do form, validação, save + sync
    ├── MeasurementFormScreen.kt # Formulário com validação de faixas
    ├── HistoryScreen.kt         # Lista do histórico com status de sync
    └── TrendChartScreen.kt      # Gráfico de tendência (Canvas + regressão linear)
```

## Comportamento offline / falha de sync

Toda medição é salva primeiro no Room. Se o Health Connect estiver
indisponível ou sem permissão, a medição fica marcada como pendente
(`syncedToHealthConnect = false`) e o botão **"Sincronizar pendentes"**
reenvia depois.

## Testes (sugestões)

- **Unitários**: a validação de faixas em `MeasurementViewModel.validate()`
  é função pura — fácil de cobrir com JUnit parametrizado.
- **Instrumentados**: Compose UI Test para o fluxo do formulário
  (`createAndroidComposeRule<MainActivity>()`).
- **E2E**: Appium/Robot Framework com AppiumLibrary; localize os campos
  por `testTag` (adicione `Modifier.testTag("weight")` nos campos se for
  automatizar por acessibilidade).

## Gráfico de tendência

A aba **Tendência** plota qualquer uma das 8 métricas (incluindo massa magra
derivada) em Canvas puro do Compose, sem dependência externa. Sobre a série é
traçada uma **regressão linear** (mínimos quadrados) em linha tracejada, e o
resumo mostra o valor atual, a variação no período (Δ) e a inclinação da
tendência em unidade/semana. A função `linearRegression()` é pura e coberta
facilmente por teste unitário.

## Dashboard web (GitHub Pages)

A pasta `docs/` contém uma página única (`index.html`) que consulta as medições
de qualquer navegador, com explicação de cada métrica, faixas de referência
Omron por sexo/idade, gráfico de evolução e silhueta com os índices de gordura
visceral e subcutânea.

### Arquitetura de dados (privacidade)

```
[App Android] --PUT--> [repo PRIVADO omron-data/measurements.json]
[Página web (Pages, repo público)] --GET (token somente leitura)--^
```

Dados de saúde nunca ficam neste repositório público — apenas o código.

### Setup

1. **Repo de dados**: crie um repositório **privado** chamado `omron-data`.
2. **Token do app** (escrita): GitHub → Settings → Developer settings →
   Fine-grained tokens → repositório `omron-data` apenas, permissão
   **Contents: Read and write**. Cole na aba **Config** do app Android.
3. **Token da página** (leitura): outro fine-grained token, mesmo repositório,
   permissão **Contents: Read-only**. A página pede na primeira visita e
   guarda no navegador (localStorage).
4. **GitHub Pages**: em Settings → Pages deste repositório, escolha
   *Deploy from a branch* → `main` → `/docs`. A página fica em
   `https://<usuario>.github.io/omron-tracker/`.

### Múltiplas pessoas (até 10)

Cada pessoa tem seu próprio arquivo no repo de dados (ex:
`measurements-renan.json`, `measurements-maria.json`) — configurado na aba
**Config** do app Android do celular dela. Na página web, cadastre as pessoas
em ⚙ Configurar → Pessoas (nome, sexo, ano de nascimento e altura definem as
faixas de referência Omron de cada perfil) e troque entre elas pelo seletor
no cabeçalho. O arquivo legado `measurements.json` continua funcionando.

O app grava o arquivo configurado (array ordenado por timestamp, com dedupe)
via Contents API; medições offline ficam pendentes (`syncedToGitHub = false`)
e são reenviadas no próximo salvamento ou pelo botão de sincronizar.

## Próximos passos possíveis

- Exportação CSV do histórico
- Backup/rotação do measurements.json no repo de dados
- Lembretes de pesagem semanal (WorkManager)
- Backup do Room (Auto Backup já cobre o básico via `allowBackup`)
