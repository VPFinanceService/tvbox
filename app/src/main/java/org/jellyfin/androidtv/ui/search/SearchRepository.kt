package org.jellyfin.androidtv.ui.search

import java.util.UUID
import org.jellyfin.androidtv.data.zip0.Zip0ApiClient
import org.jellyfin.androidtv.data.zip0.Zip0Video
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber

interface SearchRepository {
	suspend fun search(
		searchTerm: String,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>>
}

class SearchRepositoryImpl(
	private val api: Zip0ApiClient,
) : SearchRepository {
	override suspend fun search(
		searchTerm: String,
		itemTypes: Collection<BaseItemKind>,
	): Result<List<BaseItemDto>> = try {
		val page = api.search(query = searchTerm)
		Result.success(page.items.map { it.toBaseItemDto() })
	} catch (e: Exception) {
		Timber.e(e, "Failed to search zip0.com")
		Result.failure(e)
	}
}

private fun Zip0Video.toBaseItemDto() = BaseItemDto(
	id = UUID.nameUUIDFromBytes("zip0:$source:$id".toByteArray()),
	name = title,
	type = BaseItemKind.MOVIE,
	mediaType = MediaType.VIDEO,
	overview = remarks,
	productionYear = year?.toIntOrNull(),
	officialRating = score,
)