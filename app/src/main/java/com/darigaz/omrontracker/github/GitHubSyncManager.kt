package com.darigaz.omrontracker.github

import android.content.Context
import android.util.Base64
import com.darigaz.omrontracker.data.Measurement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Sincroniza as medições com um repositório PRIVADO no GitHub, mantendo o
 * arquivo `measurements.json` (um array ordenado por timestamp) via
 * Contents API:
 *
 *   GET  /repos/{owner}/{repo}/contents/measurements.json  → sha + conteúdo atual
 *   PUT  /repos/{owner}/{repo}/contents/measurements.json  → novo conteúdo (base64)
 *
 * O token deve ser um fine-grained PAT com permissão "Contents: Read and write"
 * APENAS no repositório de dados. A página web usa um segundo token, somente
 * leitura. Sem token configurado, o sync fica desativado silenciosamente
 * (mesmo padrão do Health Connect indisponível).
 *
 * Estratégia de merge: baixa o array atual, aplica exclusões pendentes,
 * faz upsert das medições pendentes por timestamp (edições substituem a
 * versão remota) e regrava. Last-write-wins é aceitável aqui porque cada
 * arquivo tem um único escritor principal (o app daquela pessoa).
 */
class GitHubSyncManager(context: Context) {

    private val prefs = context.getSharedPreferences("github_sync", Context.MODE_PRIVATE)

    var owner: String
        get() = prefs.getString("owner", "") ?: ""
        set(v) = prefs.edit().putString("owner", v.trim()).apply()

    var repo: String
        get() = prefs.getString("repo", "") ?: ""
        set(v) = prefs.edit().putString("repo", v.trim()).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v.trim()).apply()

    /** Nome do arquivo desta pessoa no repo (um arquivo por pessoa). */
    var fileName: String
        get() = prefs.getString("fileName", "measurements.json") ?: "measurements.json"
        set(v) {
            val clean = v.trim().ifBlank { "measurements.json" }
            prefs.edit().putString("fileName", clean).apply()
        }

    fun isConfigured(): Boolean =
        owner.isNotBlank() && repo.isNotBlank() && token.isNotBlank()

    /* ---- exclusões pendentes (sobrevivem a ficar offline) ---- */

    fun markDeleted(timestamp: Long) {
        val set = (prefs.getStringSet("pendingDeletions", emptySet()) ?: emptySet()).toMutableSet()
        set.add(timestamp.toString())
        prefs.edit().putStringSet("pendingDeletions", set).apply()
    }

    fun hasPendingWork(pendingUpserts: Boolean): Boolean =
        pendingUpserts || pendingDeletions().isNotEmpty()

    private fun pendingDeletions(): Set<Long> =
        (prefs.getStringSet("pendingDeletions", emptySet()) ?: emptySet())
            .mapNotNull { it.toLongOrNull() }.toSet()

    private fun clearDeletions() =
        prefs.edit().remove("pendingDeletions").apply()

    private val fileUrl: String
        get() = "https://api.github.com/repos/$owner/$repo/contents/$fileName"

    /**
     * Envia as medições pendentes. Retorna a lista das que foram gravadas
     * com sucesso (para o chamador marcar `syncedToGitHub = true`).
     * Lança exceção em falha de rede/API — o chamador decide a mensagem.
     */
    suspend fun push(pending: List<Measurement>): List<Measurement> =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext emptyList()
            val deletions = pendingDeletions()
            if (pending.isEmpty() && deletions.isEmpty()) return@withContext emptyList()

            // 1) Estado atual do arquivo (404 = primeira gravação)
            val current = getFile()
            val existing = current?.second ?: JSONArray()

            // 2) Merge: exclusões primeiro, depois upsert por timestamp
            //    (medição editada substitui a versão remota)
            val map = LinkedHashMap<Long, JSONObject>()
            for (i in 0 until existing.length()) {
                val obj = existing.getJSONObject(i)
                map[obj.getLong("timestamp")] = obj
            }
            deletions.forEach { map.remove(it) }
            pending.forEach { map[it.timestamp] = it.toJson() }

            val merged = map.values.sortedBy { it.getLong("timestamp") }
            val newArray = JSONArray()
            merged.forEach { newArray.put(it) }

            // 3) Regrava (sha obrigatório quando o arquivo já existe)
            putFile(newArray.toString(2), current?.first)
            clearDeletions()
            pending
        }

    /** GET do arquivo: retorna Pair(sha, conteúdo como JSONArray) ou null se 404. */
    private fun getFile(): Pair<String, JSONArray>? {
        val conn = open("GET", fileUrl)
        return try {
            when (conn.responseCode) {
                200 -> {
                    val body = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(body)
                    val sha = json.getString("sha")
                    val contentB64 = json.getString("content").replace("\n", "")
                    val content = String(
                        Base64.decode(contentB64, Base64.DEFAULT),
                        StandardCharsets.UTF_8,
                    )
                    sha to JSONArray(content)
                }
                404 -> null
                else -> throw GitHubApiException(conn.responseCode, readError(conn))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun putFile(content: String, sha: String?) {
        val body = JSONObject().apply {
            put("message", "Sync: ${java.time.Instant.now()}")
            put(
                "content",
                Base64.encodeToString(
                    content.toByteArray(StandardCharsets.UTF_8),
                    Base64.NO_WRAP,
                ),
            )
            if (sha != null) put("sha", sha)
        }

        val conn = open("PUT", fileUrl)
        try {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..201) {
                throw GitHubApiException(code, readError(conn))
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun open(method: String, url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("Content-Type", "application/json")
        }

    private fun readError(conn: HttpURLConnection): String =
        conn.errorStream?.bufferedReader()?.readText()?.take(200) ?: "(sem corpo)"

    private fun Measurement.toJson(): JSONObject = JSONObject().apply {
        put("timestamp", timestamp)
        put("weightKg", weightKg)
        put("bmi", bmi)
        put("bodyFatPercent", bodyFatPercent)
        put("skeletalMusclePercent", skeletalMusclePercent)
        put("visceralFatLevel", visceralFatLevel)
        put("restingMetabolismKcal", restingMetabolismKcal)
        put("bodyAge", bodyAge)
        put("leanBodyMassKg", leanBodyMassKg)
    }
}

class GitHubApiException(code: Int, detail: String) :
    Exception("GitHub API HTTP $code: $detail")
