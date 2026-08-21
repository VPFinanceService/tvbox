package org.jellyfin.androidtv.data.zip0

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

/**
 * Client for the zip0.com video site.
 *
 * The site is a React (TanStack Start) application without a public JSON REST API.
 * It exposes its data through "server functions" reachable at `/_serverFn/<id>`. Calls
 * use the GET method with a `payload` query parameter whose value is a Seroval-encoded
 * JSON document describing the arguments. The response is a Seroval-encoded JSON document
 * describing the result.
 *
 * See the `.zip0_analysis` directory for the reverse-engineered protocol used to build
 * this client.
 */
class Zip0ApiClient(
	private val baseUrl: String = "https://zip0.com",
) {
	companion object {
		private const val FN_SOURCES = "8fa43bc249007c84c4782b4237f5181449e18cd1723d5656f5f3c45c42e2daab"
		private const val FN_SEARCH = "924908a6328d92c97055b1d048defe7b4f8102dee6907ac36be30470204c7535"
		private const val FN_DETAIL = "75b7f04db7f68591c58cd4cbf1a827b99ed16ccbe2e203c1af2d34358cab97df"
		private const val FN_CATEGORY = "7e1a065dd0c4f105c2195db3af705adf30b75b211920223f5e3f9e62024916a0"

		private const val DEFAULT_SOURCE = "ffzy"
	}

	private val json = Json { ignoreUnknownKeys = true }

	private val headers = mapOf(
		"x-tsr-serverFn" to "true",
		"accept" to "application/x-tss-framed, application/x-ndjson, application/json",
		"User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
		"Referer" to "https://zip0.com/",
		"Origin" to "https://zip0.com",
		"Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
		"sec-ch-ua" to "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"",
		"sec-ch-ua-mobile" to "?0",
		"sec-ch-ua-platform" to "\"Windows\"",
		"sec-fetch-dest" to "empty",
		"sec-fetch-mode" to "cors",
		"sec-fetch-site" to "same-origin",
	)

	/** List all available video sources (线路). */
	suspend fun getSources(): List<Zip0Source> {
		val root = call(FN_SOURCES, emptyMap())
		val result = root.result
		return result?.asList()?.mapNotNull { element ->
			val map = element.asMap()
			val source = map.str("source") ?: return@mapNotNull null
			Zip0Source(
				source = source,
				sourceName = map.str("sourceName").orEmpty(),
			)
		}.orEmpty()
	}

	/** Search videos across sources. */
	suspend fun search(
		query: String,
		source: String = DEFAULT_SOURCE,
		type: String = "all",
		area: String = "all",
		year: String = "all",
	): Zip0VideoPage = withContext(Dispatchers.IO) {
		val root = call(
			FN_SEARCH,
			mapOf(
				"query" to query,
				"source" to source,
				"area" to area,
				"type" to type,
				"year" to year,
			)
		)
		root.toVideoPage()
	}

	/** Fetch video detail (including playable M3U8 episodes) or null if not found. */
	suspend fun getDetail(source: String, id: String): Zip0VideoDetail? = withContext(Dispatchers.IO) {
		val root = call(FN_DETAIL, mapOf("source" to source, "id" to id))
		val result = root.result?.asMap() ?: return@withContext null
		val detailId = result.str("id") ?: return@withContext null
		Zip0VideoDetail(
			id = detailId,
			source = result.str("source").orEmpty(),
			sourceName = result.str("sourceName").orEmpty(),
			title = result.str("title").orEmpty(),
			poster = result.str("poster"),
			year = result.str("year"),
			remarks = result.str("remarks"),
			category = result.str("category"),
			area = result.str("area"),
			language = result.str("language"),
			score = result.str("score"),
			episodeCount = result.int("episodeCount"),
			updatedAt = result.str("updatedAt"),
			description = result.str("description"),
			actors = result.str("actors"),
			director = result.str("director"),
			episodes = result["episodes"].asList().mapNotNull { episode ->
				val map = episode.asMap()
				val url = map.str("url") ?: return@mapNotNull null
				Zip0Episode(name = map.str("name"), url = url)
			},
		)
	}

	/** Browse a category (film, series, cartoon, etc.). */
	suspend fun category(
		category: String,
		page: Int = 1,
		source: String = DEFAULT_SOURCE,
		area: String = "all",
		type: String = "all",
		year: String = "all",
		sort: String = "updated",
	): Zip0VideoPage = withContext(Dispatchers.IO) {
		val root = call(
			FN_CATEGORY,
			mapOf(
				"category" to category,
				"page" to page,
				"source" to source,
				"area" to area,
				"type" to type,
				"year" to year,
				"sort" to sort,
			)
		)
		root.toVideoPage()
	}

	// ---- internal helpers ----

	private suspend fun call(functionId: String, args: Map<String, Any?>): Zip0Response = withContext(Dispatchers.IO) {
		val payload = buildPayload(args)
		val url = URL("$baseUrl/_serverFn/$functionId?payload=" + URLEncoder.encode(payload, "UTF-8"))

		val connection = url.openConnection() as HttpURLConnection
		try {
			connection.requestMethod = "GET"
			connection.connectTimeout = 30_000
			connection.readTimeout = 40_000
			headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

			val code = connection.responseCode
			val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
				?.bufferedReader(Charsets.UTF_8)
				?.use { it.readText() }
				.orEmpty()

			if (code !in 200..299) {
				throw IOException("zip0.com request failed (HTTP $code): ${body.take(200)}")
			}

			val node = json.parseToJsonElement(body)
			val decoded = deserialize(node).asMap()
			Zip0Response(
				result = decoded["result"],
				error = decoded["error"],
			)
		} catch (e: IOException) {
			Timber.e(e, "zip0.com request failed for function $functionId")
			throw e
		} finally {
			connection.disconnect()
		}
	}

	private data class Zip0Response(
		val result: JsonAny?,
		val error: JsonAny?,
	) {
		fun toVideoPage() = Zip0VideoPage(
			items = result?.asMap()?.let { map ->
				map["items"].asList().mapNotNull { element ->
					element.asMap().toVideo()
				}
			}.orEmpty(),
			health = result?.asMap()?.get("health")?.asMap()?.let { map ->
				Zip0Health(
					source = map.str("source"),
					sourceName = map.str("sourceName"),
					status = map.str("status"),
					resultCount = map.int("resultCount"),
					latencyMs = map.int("latencyMs"),
				)
			},
		)
	}
}

// ---- type aliases for the deserialized Seroval graph ----

private typealias JsonAny = Any

private fun JsonAny?.asMap(): Map<String, JsonAny?> = this as? Map<String, JsonAny?> ?: emptyMap()

private fun JsonAny?.asList(): List<JsonAny?> = this as? List<JsonAny?> ?: emptyList()

private fun Map<String, JsonAny?>.str(key: String): String? = this[key] as? String

private fun Map<String, JsonAny?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

private fun Map<String, JsonAny?>.toVideo(): Zip0Video? {
	val id = str("id") ?: return null
	val source = str("source") ?: return null
	return Zip0Video(
		id = id,
		source = source,
		sourceName = str("sourceName").orEmpty(),
		title = str("title").orEmpty(),
		poster = str("poster"),
		year = str("year"),
		remarks = str("remarks"),
		category = str("category"),
		area = str("area"),
		language = str("language"),
		score = str("score"),
		episodeCount = int("episodeCount"),
		updatedAt = str("updatedAt"),
	)
}

// ---- Seroval serialization (request payload) ----

private var serovalNextId = 0

private fun buildPayload(args: Map<String, Any?>): String {
	serovalNextId = 0
	val dataNode = serializeObject(mapOf("data" to args))
	return JsonObject(
		mapOf(
			"t" to dataNode,
			"f" to JsonPrimitive(127),
			"m" to JsonArray(emptyList()),
		)
	).toString()
}

private fun serializeValue(value: JsonAny?): JsonElement = when (value) {
	null -> JsonObject(mapOf("t" to JsonPrimitive(2), "s" to JsonPrimitive(0)))
	is Boolean -> JsonObject(mapOf("t" to JsonPrimitive(2), "s" to JsonPrimitive(if (value) 2 else 3)))
	is Int -> numberNode(value)
	is Long -> numberNode(value)
	is Double -> numberNode(value)
	is Float -> numberNode(value)
	is String -> JsonObject(mapOf("t" to JsonPrimitive(1), "s" to JsonPrimitive(value)))
	is List<*> -> serializeArray(value)
	is Map<*, *> -> serializeObject(value.mapKeys { it.key.toString() }.mapValues { it.value as JsonAny? })
	else -> throw IllegalArgumentException("Unsupported Seroval value type: ${value::class}")
}

private fun numberNode(value: Number) = JsonObject(mapOf("t" to JsonPrimitive(0), "s" to JsonPrimitive(value)))

private fun serializeArray(items: List<*>): JsonObject {
	val id = serovalNextId++
	return JsonObject(
		mapOf(
			"t" to JsonPrimitive(9),
			"i" to JsonPrimitive(id),
			"a" to JsonArray(items.map { serializeValue(it as JsonAny?) }),
			"o" to JsonPrimitive(0),
		)
	)
}

private fun serializeObject(map: Map<String, JsonAny?>): JsonObject {
	val id = serovalNextId++
	val keys = map.keys.toList()
	val values = keys.map { serializeValue(map[it]) }
	return JsonObject(
		mapOf(
			"t" to JsonPrimitive(10),
			"i" to JsonPrimitive(id),
			"p" to JsonObject(
				mapOf(
					"k" to JsonArray(keys.map { JsonPrimitive(it) }),
					"v" to JsonArray(values),
				)
			),
			"o" to JsonPrimitive(0),
		)
	)
}

// ---- Seroval deserialization (response payload) ----

private fun deserialize(node: JsonElement): JsonAny? {
	val obj = node as? JsonObject ?: return null
	return when (obj["t"]?.jsonPrimitive?.longOrNull) {
		0L -> obj["s"]?.jsonPrimitive?.let { it.longOrNull ?: it.doubleOrNull }

		1L -> obj["s"]?.jsonPrimitive?.contentOrNull

		2L -> when (obj["s"]?.jsonPrimitive?.longOrNull) {
			2L -> true
			3L -> false
			else -> null
		}

		9L -> obj["a"]?.jsonArray?.map { deserialize(it) }

		10L, 11L -> {
			val pair = obj["p"]?.jsonObject ?: return null
			val keys = pair["k"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
			val values = pair["v"]?.jsonArray?.map { deserialize(it) }.orEmpty()
			keys.mapIndexed { index, key -> key to values.getOrNull(index) }.toMap()
		}

		else -> null
	}
}