package org.jellyfin.androidtv.data.zip0

/**
 * Data models mirroring the zip0.com server function responses.
 *
 * All values are strings unless a field can clearly be numeric. The server returns
 * numbers as JSON numbers inside the Seroval payload, so numeric fields are parsed
 * as [Int] where applicable and `null` is used for missing/invalid values.
 */

/** A single video source line (线路). */
data class Zip0Source(
	val source: String,
	val sourceName: String,
)

/** A summarized video entry returned by search / category browse. */
data class Zip0Video(
	val id: String,
	val source: String,
	val sourceName: String,
	val title: String,
	val poster: String? = null,
	val year: String? = null,
	val remarks: String? = null,
	val category: String? = null,
	val area: String? = null,
	val language: String? = null,
	val score: String? = null,
	val episodeCount: Int? = null,
	val updatedAt: String? = null,
)

/** A single playable episode / source URL (M3U8). */
data class Zip0Episode(
	val name: String? = null,
	val url: String,
)

/** Full detail for a video, including playable episodes. */
data class Zip0VideoDetail(
	val id: String,
	val source: String,
	val sourceName: String,
	val title: String,
	val poster: String? = null,
	val year: String? = null,
	val remarks: String? = null,
	val category: String? = null,
	val area: String? = null,
	val language: String? = null,
	val score: String? = null,
	val episodeCount: Int? = null,
	val updatedAt: String? = null,
	val description: String? = null,
	val actors: String? = null,
	val director: String? = null,
	val episodes: List<Zip0Episode> = emptyList(),
)

/** Health / status of a source after a search request. */
data class Zip0Health(
	val source: String? = null,
	val sourceName: String? = null,
	val status: String? = null,
	val resultCount: Int? = null,
	val latencyMs: Int? = null,
)

/** A page of video results returned by search or category browse. */
data class Zip0VideoPage(
	val items: List<Zip0Video> = emptyList(),
	val health: Zip0Health? = null,
)