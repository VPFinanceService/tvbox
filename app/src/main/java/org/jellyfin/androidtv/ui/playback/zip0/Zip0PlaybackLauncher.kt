package org.jellyfin.androidtv.ui.playback.zip0

import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.data.zip0.Zip0ApiClient
import org.jellyfin.androidtv.data.zip0.Zip0Video
import org.jellyfin.androidtv.data.zip0.Zip0VideoDetail
import org.jellyfin.androidtv.data.zip0.toVideo
import org.jellyfin.androidtv.playback.createZip0QueueEntry
import org.jellyfin.androidtv.ui.navigation.Destinations
import org.jellyfin.androidtv.ui.navigation.NavigationRepository
import org.jellyfin.playback.core.queue.QueueEntry
import org.jellyfin.playback.core.queue.supplier.QueueSupplier
import timber.log.Timber

/**
 * Holds the zip0.com video detail that is currently queued for the built-in video player.
 * Shared between [Zip0PlaybackLauncher] and the video player fragment.
 */
class Zip0QueueManager {
	private var _detail: Zip0VideoDetail? = null

	val detail: Zip0VideoDetail?
		get() = _detail

	fun setQueue(detail: Zip0VideoDetail) {
		_detail = detail
	}

	fun clear() {
		_detail = null
	}
}

/**
 * A [QueueSupplier] that exposes every playable episode of a zip0.com video as a
 * [QueueEntry]. Each entry is resolved to its M3U8 stream by the zip0 media stream
 * resolver when playback starts.
 */
class Zip0QueueSupplier(
	private val detail: Zip0VideoDetail,
) : QueueSupplier {
	override val size: Int get() = detail.episodes.size

	override suspend fun getItem(index: Int): QueueEntry? {
		if (index !in detail.episodes.indices) return null
		return createZip0QueueEntry(detail.toVideo(), index)
	}
}

/**
 * Launches playback of a zip0.com video in the built-in video player. Fetches the video
 * detail, queues all episodes and navigates to the player.
 */
class Zip0PlaybackLauncher(
	private val api: Zip0ApiClient,
	private val queueManager: Zip0QueueManager,
	private val navigationRepository: NavigationRepository,
) {
	fun launch(video: Zip0Video) {
		ProcessLifecycleOwner.get().lifecycleScope.launch {
			val detail = api.getDetail(video.source, video.id)
			if (detail == null || detail.episodes.isEmpty()) {
				Timber.w("Zip0: no playable episode for %s %s", video.source, video.id)
				return@launch
			}

			queueManager.setQueue(detail)
			navigationRepository.navigate(Destinations.videoPlayerNew(0))
		}
	}
}