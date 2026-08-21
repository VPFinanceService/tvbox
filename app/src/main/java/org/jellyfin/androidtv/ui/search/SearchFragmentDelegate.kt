package org.jellyfin.androidtv.ui.search

import android.content.Context
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.OnItemViewClickedListener
import androidx.leanback.widget.OnItemViewSelectedListener
import androidx.leanback.widget.Row
import org.jellyfin.androidtv.constant.QueryType
import org.jellyfin.androidtv.data.service.BackgroundService
import org.jellyfin.androidtv.data.zip0.Zip0ItemRegistry
import org.jellyfin.androidtv.ui.itemhandling.BaseItemDtoBaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.BaseRowItem
import org.jellyfin.androidtv.ui.itemhandling.ItemLauncher
import org.jellyfin.androidtv.ui.itemhandling.ItemRowAdapter
import org.jellyfin.androidtv.ui.playback.zip0.Zip0PlaybackLauncher
import org.jellyfin.androidtv.ui.presentation.CardPresenter
import org.jellyfin.androidtv.ui.presentation.CustomListRowPresenter
import org.jellyfin.androidtv.ui.presentation.MutableObjectAdapter

class SearchFragmentDelegate(
	private val context: Context,
	private val backgroundService: BackgroundService,
	private val itemLauncher: ItemLauncher,
	private val zip0PlaybackLauncher: Zip0PlaybackLauncher,
) {
	val rowsAdapter = MutableObjectAdapter<Row>(CustomListRowPresenter())

	fun showResults(searchResultGroups: Collection<SearchResultGroup>) {
		rowsAdapter.clear()
		val adapters = mutableListOf<ItemRowAdapter>()
		for ((labelRes, baseItems) in searchResultGroups) {
			val adapter = ItemRowAdapter(
				context,
				baseItems.toList(),
				CardPresenter(),
				rowsAdapter,
				QueryType.Search
			).apply {
				setRow(ListRow(HeaderItem(context.getString(labelRes)), this))
			}
			adapters.add(adapter)
		}
		for (adapter in adapters) adapter.Retrieve()
	}

	val onItemViewClickedListener = OnItemViewClickedListener { _, item, _, row ->
		if (item !is BaseRowItem) return@OnItemViewClickedListener

		// A zip0.com search result plays directly instead of opening Jellyfin item details.
		val baseItem = (item as? BaseItemDtoBaseRowItem)?.baseItem
		val zip0Video = baseItem?.let { Zip0ItemRegistry.get(it.id) }
		if (zip0Video != null) {
			zip0PlaybackLauncher.launch(zip0Video)
			return@OnItemViewClickedListener
		}

		row as ListRow
		val adapter = row.adapter as ItemRowAdapter
		itemLauncher.launch(item as BaseRowItem?, adapter, context)
	}

	val onItemViewSelectedListener = OnItemViewSelectedListener { _, item, _, _ ->
		val baseItem = item?.let { (item as BaseRowItem).baseItem }
		if (baseItem != null) {
			backgroundService.setBackground(baseItem)
		} else {
			backgroundService.clearBackgrounds()
		}
	}
}
