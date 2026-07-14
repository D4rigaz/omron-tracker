# CLAUDE.md — Memória do projeto Omron Tracker

> Arquivo de contexto para retomar o desenvolvimento com o Claude (ou qualquer dev).
> Última atualização: **14/07/2026** — estado: **funcionando em produção** (app instalado + página no ar).

## O que é

Sistema pessoal/familiar de acompanhamento de composição corporal da balança
**Omron HBF-514C** (bioimpedância, sem Bluetooth — dados digitados manualmente).
Dono: Renan (github: **D4rigaz**). Uso previsto: até 4+ pessoas da família
(Renan, esposa, mãe, irmã) — suporte a **10 perfis** na página.

## Arquitetura

```
[App Android (1 por celular/pessoa)]
   ├─ Room (SQLite local, fonte da verdade)
   ├─ Health Connect (peso, %gordura, TMB, massa magra) → Samsung Health
   └─ GitHub Contents API ──PUT──> [repo PRIVADO D4rigaz/omron-data]
                                      └─ measurements.json          (Renan, legado)
                                      └─ measurements-<pessoa>.json (demais)
[Página web — GitHub Pages do repo público D4rigaz/omron-tracker, pasta /docs]
   └─ GET via API com fine-grained PAT somente leitura (salvo no localStorage)
   └─ Exclusão opcional via PAT de escrita (segundo campo, opcional)
```

- **Repo público `omron-tracker`**: só código (app Android + docs/index.html). Pages: main → /docs.
- **Repo privado `omron-data`**: só os JSONs de medições. Nunca colocar dados de saúde no repo público.
- **Tokens**: fine-grained PATs restritos ao `omron-data`. Um de escrita (app, aba Config),
  um de leitura (página). Página aceita um de escrita opcional só p/ excluir.

## Formato dos dados (measurements*.json)

Array ordenado por `timestamp` (epoch ms), sem duplicatas:

```json
[{ "timestamp": 1720900000000, "weightKg": 128.4, "bmi": 37.1,
   "bodyFatPercent": 34.5, "skeletalMusclePercent": 29.8,
   "visceralFatLevel": 17, "restingMetabolismKcal": 2210,
   "bodyAge": 54, "leanBodyMassKg": 84.1 }]
```

`leanBodyMassKg` é derivado: `peso × (1 − gordura/100)`. A página recalcula se ausente.

## App Android (Kotlin + Compose, minSdk conforme build.gradle)

Pacote `com.darigaz.omrontracker`. Abas: **Nova medição · Histórico · Tendência · Config**.

- `data/Measurement.kt` — entidade Room. 7 campos da balança + `leanBodyMassKg` derivado +
  flags `syncedToHealthConnect` e `syncedToGitHub`.
- `data/AppDatabase.kt` — **versão 2** (MIGRATION_1_2 adicionou `syncedToGitHub`).
- `data/MeasurementDao.kt` — insert/update/**delete**, observeAll, pendingSync (HC),
  pendingGitHubSync, latest.
- `github/GitHubSyncManager.kt` — SharedPreferences (owner/repo/token/**fileName**).
  `push()`: GET arquivo (sha) → aplica **exclusões pendentes** (fila persistente em prefs,
  sobrevive offline) → **upsert por timestamp** (edição substitui a versão remota) → PUT.
  Sem dependências novas (HttpURLConnection + org.json). Token via header Bearer.
- `health/HealthConnectManager.kt` — grava/apaga 4 registros (Weight, BodyFat, BasalMetabolicRate,
  LeanBodyMass). `deleteMeasurement()` usa TimeRangeFilter ±1s do timestamp.
  IMC, visceral, %músculo e idade corporal não têm tipo no HC (ficam só no Room/GitHub).
- `ui/MeasurementViewModel.kt` — save() cria OU edita (FormState.editing != null →
  dao.update mantendo timestamp, HC delete+reinsert, marca syncedToGitHub=false p/ upsert).
  `delete()`: Room + HC best-effort + `gitHub.markDeleted(ts)` + push.
  `syncPending()` reenvia pendências de HC e GitHub.
- `ui/MeasurementFormScreen.kt` — modo edição (título com a data, "Salvar edição"/"Cancelar edição").
  ⚠️ `val editing = form.editing` local é OBRIGATÓRIO (smart cast falha em propriedade delegada —
  já quebrou o build uma vez).
- `ui/HistoryScreen.kt` — cards com status ✓/⏳ HC e GH, botões **Editar** (→ startEdit + volta
  p/ aba 0 via callback do MainActivity) e **Excluir** (AlertDialog de confirmação).
- `ui/SettingsScreen.kt` — owner, repo, token, **fileName** (arquivo da pessoa) + sync pendentes.
- Manifest tem `INTERNET` + permissões Health Connect.

### Build / CI

- `.github/workflows/build-apk.yml`: gradle assembleDebug em todo push, APK como artefato.
- **Keystore de debug fixo**: secret `DEBUG_KEYSTORE_B64` restaurado p/ `~/.android/debug.keystore`
  antes do build (sem isso cada build teria assinatura diferente e não instalaria por cima —
  já causou dor: exigiu desinstalar/reinstalar uma vez).
- Instalação: `adb install -r app-debug.apk` (Play Protect bloqueia instalação manual;
  contornos: "instalar mesmo assim" ou desativar verificação temporariamente).
- Instalar por cima PRESERVA o Room (migração roda sozinha). Desinstalar APAGA.

## Página web (docs/index.html — arquivo único, ~50 KB)

HTML+CSS+JS vanilla, Chart.js 4.4.1 via cdnjs, fontes Archivo + IBM Plex Sans (Google Fonts).
Idioma: PT-BR. Config no `localStorage` (chave `omron-tracker-cfg`).

Estrutura da cfg: `{ owner, repo, token, writeToken?, profiles: [{id, name, file, sex,
birthYear, heightCm}], activeId }`. Migração automática do formato antigo (pessoa única).

Funcionalidades (em ordem de implementação):
1. Cards das 8 métricas com valor, delta vs medição anterior, chip de classificação e
   **faixa de referência Omron** (barra segmentada com marcador) por sexo/idade do perfil.
   Explicação expansível "O que significa" em cada card.
2. Gráfico de evolução (chips de métrica).
3. Silhueta SVG: mancha visceral (escala nível 1–30) + subcutânea (escala %gordura;
   perfil feminino desloca p/ quadril — padrão ginoide). Disclaimer honesto: bioimpedância
   não localiza gordura; só o índice visceral é regional.
4. Perfil com nome/altura + conferência IMC calculado vs balança (diverge >0,5 → alerta).
5. **Multi-pessoa (até 10)**: seletor no header (aparece com 2+), gestão em ⚙ Configurar
   (adicionar/editar/remover/ver). Arquivo auto-gerado do nome (slug) se vazio.
6. **Histórico de pesagens**: tabela completa clicável — selecionar linha faz cards/silhueta
   mostrarem AQUELA medição (nota azul + "Voltar à mais recente"). Gráfico sempre mostra tudo.
7. **Exclusão pela página** (opcional): coluna Excluir aparece só com writeToken configurado.
   GET (sha) → filter timestamp → PUT. `cache: 'no-store'` em TODAS as chamadas API
   (bug corrigido: navegador reusava resposta raw p/ chamada de metadados → content undefined).

Faixas de referência implementadas: gordura corporal e músculo esquelético (tabelas Omron
por sexo × 3 faixas etárias), visceral (1–9/10–14/15–30), IMC (OMS), idade corporal (vs real).

## Decisões e assimetrias conscientes (não são bugs)

- **Excluir pela página NÃO remove do Room do celular.** A medição local fica synced=true e não
  ressuscita — EXCETO se a pessoa editá-la depois no app (edição marca pendente de novo → volta).
  Exclusão definitiva: preferir o app.
- **Não existe edição pela página** — evitaria conflito de versões com o Room (o app é o dono do dado).
- Last-write-wins no PUT é aceitável: cada arquivo tem um único escritor principal.
- Silhueta é representação proporcional dos índices, não mapa medido (deixado explícito na UI).
- Health Connect: só 4 métricas têm tipo nativo; o resto vive no Room/GitHub.

## Histórico de problemas resolvidos

| Problema | Causa | Solução |
|---|---|---|
| push rejeitado (fetch first) | zip sem .git → históricos não relacionados | `git push --force` (histórico antigo descartado conscientemente) |
| APK não instalava por cima | keystore de debug novo a cada build no CI | keystore fixo via secret `DEBUG_KEYSTORE_B64` |
| Play Protect bloqueou instalação | APK debug fora da Play Store | instalar mesmo assim / adb install -r |
| Build: "Smart cast impossible... delegated property" | `form.editing` usado direto (form é `by collectAsState`) | captura local `val editing = form.editing` |
| Página: "Cannot read properties of undefined (reading 'replace')" | cache do navegador entregou resposta raw na chamada de metadados | `cache:'no-store'` + fallback raw + validação de sha/array |
| 404 na primeira configuração da página | campo usuário com nome de exibição ("Renan Felix") | usar o login `D4rigaz` |

## Ideias futuras (backlog)

- Filtro por período / comparação lado a lado de duas datas no histórico (útil quando a tabela crescer).
- Exportação CSV pelo app.
- Teste instrumentado da migração Room (MigrationTestHelper).
- Backup/rotação do JSON no repo de dados.
- Metas por pessoa (ex: peso alvo) com linha no gráfico.
- PWA (manifest + service worker) p/ "instalar" a página no celular.

## Como retomar com o Claude

1. Compartilhar este arquivo (ou pedir p/ clonar `https://github.com/D4rigaz/omron-tracker`).
2. Informar o que mudou desde a última atualização deste arquivo.
3. **Manter este arquivo atualizado a cada sessão de evolução** (pedir ao Claude que o regenere).

Convenções da colaboração: respostas em PT-BR; página = arquivo único em docs/;
zero dependências novas no app sem necessidade; validar sintaxe do JS antes de entregar;
nunca colocar dados de saúde ou tokens no repo público.
