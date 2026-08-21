package org.jellyfin.androidtv.data.zip0

import java.util.Collections
import java.util.UUID
import java.util.WeakHashMap

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

/**
 * Stable application-local [UUID] for a zip0.com video. This is the id used when a
 * [Zip0Video] is adapted into a [org.jellyfin.sdk.model.api.BaseItemDto], so it can be
 * looked up again in [Zip0ItemRegistry] when the item is clicked.
 */
fun Zip0Video.uid(): UUID = UUID.nameUUIDFromBytes("zip0:$source:$id".toByteArray())

/**
 * Registry mapping the stable [UUID] of a search-result [Zip0Video] back to the original
 * zip0.com source. Used to recognize a zip0 result when it is selected, so we can play it
 * directly instead of treating it as a Jellyfin library item.
 */
object Zip0ItemRegistry {
	private val entries: MutableMap<UUID, Zip0Video> = Collections.synchronizedMap(WeakHashMap())

	fun put(video: Zip0Video) {
		entries[video.uid()] = video
	}

	fun get(id: UUID): Zip0Video? = entries[id]

	fun clear() = entries.clear()
}

/** Adapt a full [Zip0VideoDetail] into a [Zip0Video] (used when building playback queue entries). */
fun Zip0VideoDetail.toVideo() = Zip0Video(
	id = id,
	source = source,
	sourceName = sourceName,
	title = title,
	poster = poster,
	year = year,
	remarks = remarks,
	category = category,
	area = area,
	language = language,
	score = score,
	episodeCount = episodeCount,
	updatedAt = updatedAt,
)