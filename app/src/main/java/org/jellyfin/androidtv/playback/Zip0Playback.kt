package org.jellyfin.androidtv.playback

import java.time.LocalDate
import org.jellyfin.androidtv.data.zip0.Zip0ApiClient
import org.jellyfin.androidtv.data.zip0.Zip0Video
import org.jellyfin.playback.core.element.ElementKey
import org.jellyfin.playback.core.element.element
import org.jellyfin.playback.core.element.elementFlow
import org.jellyfin.playback.core.mediastream.MediaConversionMethod
import org.jellyfin.playback.core.mediastream.MediaStreamContainer
import org.jellyfin.playback.core.mediastream.MediaStreamResolver
import org.jellyfin.playback.core.mediastream.PlayableMediaStream
import org.jellyfin.playback.core.mediastream.mediatype.MediaType
import org.jellyfin.playback.core.mediastream.mediatype.mediaType
import org.jellyfin.playback.core.plugin.playbackPlugin
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.queue.QueueEntryMetadata
import org.jellyfin.playback.core.queue.metadata

/**
 * Reference to a zip0.com video that a [QueueEntry] is resolved from.
 */
data class Zip0Reference(
	val source: String,
	val id: String,
	val episodeIndex: Int = 0,
)

private val zip0ReferenceKey = ElementKey<Zip0Reference>("Zip0Reference")

/** Get or set the zip0.com reference for this [QueueEntry]. */
var QueueEntry.zip0Reference by element(zip0ReferenceKey)

/** Get the flow of [zip0Reference]. */
val QueueEntry.zip0ReferenceFlow by elementFlow(zip0ReferenceKey)

/**
 * Create a [QueueEntry] backed by a zip0.com video. Playback is resolved lazily by
 * [Zip0MediaStreamResolver] using the detail API.
 */
fun createZip0QueueEntry(
	video: Zip0Video,
	episodeIndex: Int = 0,
): QueueEntry = QueueEntry().apply {
	metadata = QueueEntryMetadata(
		mediaId = "zip0:${video.source}:${video.id}",
		title = video.title,
		displayTitle = video.title,
		description = video.remarks,
		artworkUri = video.poster,
		genre = video.category,
		releaseDate = video.year
			?.takeIf { it.length >= 4 }
			?.substring(0, 4)
			?.toIntOrNull()
			?.let { LocalDate.of(it, 1, 1) },
	)
	mediaType = MediaType.Video
	zip0Reference = Zip0Reference(
		source = video.source,
		id = video.id,
		episodeIndex = episodeIndex,
	)
}

/**
 * Resolves a zip0.com video's playable M3U8 stream from its detail API.
 */
class Zip0MediaStreamResolver(
	private val api: Zip0ApiClient,
) : MediaStreamResolver {
	override suspend fun getStream(queueEntry: QueueEntry): PlayableMediaStream? {
		val reference = queueEntry.zip0Reference ?: return null

		val detail = api.getDetail(reference.source, reference.id) ?: return null
		val episode = detail.episodes.getOrNull(reference.episodeIndex)
			?: detail.episodes.firstOrNull()
			?: return null

		return PlayableMediaStream(
			identifier = "zip0:${detail.source}:${detail.id}:${reference.episodeIndex}",
			conversionMethod = MediaConversionMethod.None,
			container = MediaStreamContainer("m3u8"),
			tracks = emptyList(),
			queueEntry = queueEntry,
			url = episode.url,
		)
	}
}

/** Playback plugin providing the zip0.com media stream resolver. */
fun zip0Plugin(api: Zip0ApiClient) = playbackPlugin {
	provide(Zip0MediaStreamResolver(api))
}