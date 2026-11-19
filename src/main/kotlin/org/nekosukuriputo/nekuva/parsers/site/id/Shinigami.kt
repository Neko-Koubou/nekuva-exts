package org.nekosukuriputo.nekuva.parsers.site.id

import okhttp3.Headers
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.nekosukuriputo.nekuva.parsers.MangaLoaderContext
import org.nekosukuriputo.nekuva.parsers.MangaSourceParser
import org.nekosukuriputo.nekuva.parsers.config.ConfigKey
import org.nekosukuriputo.nekuva.parsers.core.PagedMangaParser
import org.nekosukuriputo.nekuva.parsers.model.*
import org.nekosukuriputo.nekuva.parsers.network.CommonHeaders
import org.nekosukuriputo.nekuva.parsers.util.*
import org.nekosukuriputo.nekuva.parsers.util.json.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

@MangaSourceParser("SHINIGAMI", "Shinigami", "id")
internal class Shinigami(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.SHINIGAMI, 24) {

	override val configKeyDomain = ConfigKey.Domain("s1.shinigami.mx", "id.shinigami.asia")
	private val apiHost = "api.shinigami.io"
	private val apiSuffix = "$apiHost/v1"
	private val defaultCdnUrl = "https://cdn.shinigami.gg"
	private val authorSlugCache = ConcurrentHashMap<String, String>()

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = super.getRequestHeaders().newBuilder()
		.add(CommonHeaders.REFERER, "https://$domain/")
		.add(CommonHeaders.ORIGIN, "https://$domain")
		.add(CommonHeaders.SEC_FETCH_DEST, "empty")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.NEWEST,
		SortOrder.NEWEST_ASC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override suspend fun getFilterOptions(): MangaListFilterOptions {
		return MangaListFilterOptions(
			availableTags = fetchTags(),
			availableStates = EnumSet.of(
				MangaState.ONGOING,
				MangaState.FINISHED,
				MangaState.PAUSED,
			),
			availableContentTypes = EnumSet.of(
				ContentType.MANGA,
				ContentType.MANHWA,
				ContentType.MANHUA,
			),
		)
	}

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isTagsExclusionSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		val rawAuthor = filter.author?.trim()?.takeIf { it.isNotEmpty() }
		var usedGuessedAuthorSlug = false
		val authorParam = rawAuthor?.let { author ->
			val cached = authorSlugCache[author.lowercase(Locale.ROOT)]
			when {
				cached != null -> cached
				UUID_REGEX.matches(author) || author.endsWith("-author", ignoreCase = true) -> author
				else -> {
					usedGuessedAuthorSlug = true
					"${author.lowercase(Locale.ROOT).replace(REGEX_SPACES, "-")}-author"
				}
			}
		}

		val url = buildListUrl(
			page = page,
			order = order,
			filter = filter,
			authorParam = authorParam,
			queryOverride = null,
		)

		var json = webClient.httpGet(url).parseJson()
		var data = json.optJSONArray("data")

		if ((data == null || data.length() == 0) &&
			usedGuessedAuthorSlug &&
			rawAuthor != null &&
			filter.query.isNullOrBlank()
		) {
			val fallbackUrl = buildListUrl(
				page = page,
				order = order,
				filter = filter,
				authorParam = null,
				queryOverride = rawAuthor,
			)
			json = webClient.httpGet(fallbackUrl).parseJson()
			data = json.optJSONArray("data")
		}

		if (data == null || data.length() == 0) {
			return emptyList()
		}

		return data.mapJSON { jo ->
			val id = jo.getString("manga_id")
			val taxonomy = jo.optJSONObject("taxonomy")
			val cover = jo.getStringOrNull("cover_image_url") ?: jo.getStringOrNull("cover_portrait_url")
			val largeCover = jo.getStringOrNull("cover_portrait_url") ?: jo.getStringOrNull("cover_image_url")
			Manga(
				id = generateUid(id),
				url = "/manga/detail/$id",
				publicUrl = "https://$domain/series/$id",
				title = jo.getString("title"),
				altTitles = parseAltTitles(jo.getStringOrNull("alternative_title")),
				coverUrl = cover,
				largeCoverUrl = largeCover,
				authors = parseAuthors(taxonomy),
				tags = parseTaxonomyTags(taxonomy, titleCase = true),
				state = parseState(jo.getIntOrDefault("status", 0)),
				description = jo.getStringOrNull("description"),
				contentRating = null,
				source = source,
				rating = parseRating(jo),
			)
		}
	}

	private fun buildListUrl(
		page: Int,
		order: SortOrder,
		filter: MangaListFilter,
		authorParam: String?,
		queryOverride: String?,
	): HttpUrl = urlBuilder().host(apiHost).addPathSegments("v1/manga/list").apply {
		val offset = (page - 1) * pageSize
		addQueryParameter("offset", offset.toString())
		addQueryParameter("limit", pageSize.toString())
		addQueryParameter(
			"sort",
			when (order) {
				SortOrder.POPULARITY, SortOrder.POPULARITY_ASC -> "popularity"
				SortOrder.NEWEST, SortOrder.NEWEST_ASC -> "new_manga"
				SortOrder.RATING, SortOrder.RATING_ASC -> "rating"
				SortOrder.ALPHABETICAL, SortOrder.ALPHABETICAL_DESC -> "title"
				SortOrder.UPDATED, SortOrder.UPDATED_ASC -> "latest"
				else -> "latest"
			},
		)
		addQueryParameter(
			"sort_order",
			when (order) {
				SortOrder.UPDATED_ASC,
				SortOrder.NEWEST_ASC,
				SortOrder.POPULARITY_ASC,
				SortOrder.RATING_ASC,
				SortOrder.ALPHABETICAL,
				-> "asc"

				else -> "desc"
			},
		)

		filter.states.oneOrThrowIfMany()?.let {
			val statusValue = when (it) {
				MangaState.FINISHED -> "completed"
				MangaState.ONGOING -> "ongoing"
				MangaState.PAUSED -> "hiatus"
				else -> null
			}
			if (statusValue != null) {
				addQueryParameter("status", statusValue)
			}
		}

		filter.types.oneOrThrowIfMany()?.let {
			val formatValue = when (it) {
				ContentType.MANGA -> "manga"
				ContentType.MANHUA -> "manhua"
				ContentType.MANHWA -> "manhwa"
				else -> null
			}
			if (formatValue != null) {
				addQueryParameter("format", formatValue)
			}
		}

		addTaxonomyFilters(filter.tags, isExclude = false)
		addTaxonomyFilters(filter.tagsExclude, isExclude = true)

		if (!authorParam.isNullOrEmpty()) {
			addQueryParameter("author", authorParam)
		}

		val effectiveQuery = queryOverride ?: filter.query?.trim()?.takeIf { it.isNotEmpty() }
		if (!effectiveQuery.isNullOrEmpty()) {
			addQueryParameter("q", effectiveQuery)
		}
	}.build()

	private fun HttpUrl.Builder.addTaxonomyFilters(tags: Set<MangaTag>, isExclude: Boolean) {
		if (tags.isEmpty()) return
		val genres = ArrayList<String>()
		val themes = ArrayList<String>()
		val subformats = ArrayList<String>()
		for (tag in tags) {
			when {
				tag.key.startsWith(THEME_PREFIX) -> themes.add(tag.key.removePrefix(THEME_PREFIX))
				tag.key.startsWith(SUBFORMAT_PREFIX) -> subformats.add(tag.key.removePrefix(SUBFORMAT_PREFIX))
				else -> genres.add(tag.key)
			}
		}
		val suffix = if (isExclude) "_exclude" else ""
		if (genres.isNotEmpty()) {
			addQueryParameter("genre$suffix", genres.joinToString(","))
		}
		if (themes.isNotEmpty()) {
			addQueryParameter("theme$suffix", themes.joinToString(","))
		}
		if (subformats.isNotEmpty()) {
			addQueryParameter("subformat$suffix", subformats.joinToString(","))
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val mangaId = manga.url.substringBefore('?').trim('/').substringAfterLast('/')
		val json = webClient.httpGet("https://$apiSuffix/manga/detail/$mangaId").parseJson()
		val jo = json.getJSONObject("data")
		val id = jo.getString("manga_id")
		val taxonomy = jo.optJSONObject("taxonomy")
		val parsedTags = parseTaxonomyTags(taxonomy, titleCase = false)
		val parsedAuthors = parseAuthors(taxonomy)
		val cover = jo.getStringOrNull("cover_image_url") ?: jo.getStringOrNull("cover_portrait_url")
		val largeCover = jo.getStringOrNull("cover_portrait_url") ?: jo.getStringOrNull("cover_image_url")

		return manga.copy(
			title = jo.getStringOrNull("title") ?: manga.title,
			altTitles = parseAltTitles(jo.getStringOrNull("alternative_title")).ifEmpty { manga.altTitles },
			publicUrl = "https://$domain/series/$id",
			coverUrl = cover ?: manga.coverUrl,
			largeCoverUrl = largeCover ?: manga.largeCoverUrl,
			rating = parseRating(jo).let { if (it != RATING_UNKNOWN) it else manga.rating },
			tags = parsedTags.ifEmpty { manga.tags },
			authors = parsedAuthors.ifEmpty { manga.authors },
			state = parseState(jo.getIntOrDefault("status", 0)) ?: manga.state,
			description = jo.getStringOrNull("description") ?: manga.description,
			chapters = getChapters(id),
		)
	}

	private suspend fun getChapters(mangaId: String): List<MangaChapter> {
		val chapters = ArrayList<MangaChapter>()
		val seenCursors = HashSet<String>()
		var cursor: String? = null

		for (page in 0 until 50) {
			val url = urlBuilder().host(apiHost).addPathSegments("v1/chapter/$mangaId/list").apply {
				addQueryParameter("limit", "2000")
				addQueryParameter("sort_by", "chapter_number")
				addQueryParameter("sort_order", "asc")
				if (cursor != null) {
					addQueryParameter("cursor", cursor)
				}
			}.build()

			val json = webClient.httpGet(url).parseJson()
			val data = json.optJSONArray("data") ?: break

			for (i in 0 until data.length()) {
				val jo = data.getJSONObject(i)
				val chapterId = jo.getString("chapter_id")
				val title = jo.getStringOrNull("chapter_title")

				chapters.add(
					MangaChapter(
						id = generateUid(chapterId),
						title = title,
						number = jo.getFloatOrDefault("chapter_number", 0f),
						url = "chapter/detail/$chapterId",
						scanlator = null,
						uploadDate = jo.getStringOrNull("release_date")?.parseDate() ?: 0L,
						branch = null,
						source = source,
						volume = 0,
					),
				)
			}

			val meta = json.optJSONObject("meta")
			val hasNext = meta?.getBooleanOrDefault("has_next", false) == true
			val nextCursor = meta?.getStringOrNull("next_cursor")
			if (!hasNext || nextCursor == null || !seenCursors.add(nextCursor)) {
				break
			}
			cursor = nextCursor
		}

		return chapters
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val chapterPath = chapter.url.removePrefix("/")
		val json = webClient.httpGet("https://$apiSuffix/$chapterPath").parseJson()
		val data = json.getJSONObject("data")
		val baseUrl = (data.getStringOrNull("base_url") ?: defaultCdnUrl).trimEnd('/')
		val chapterData = data.getJSONObject("chapter")
		val rawPath = chapterData.getString("path")
		val basePath = buildString {
			if (!rawPath.startsWith('/')) append('/')
			append(rawPath)
			if (!rawPath.endsWith('/')) append('/')
		}
		val datas = chapterData.getJSONArray("data")

		return List(datas.length()) { i ->
			val imgName = datas.getString(i).trimStart('/')
			val imageUrl = "$baseUrl$basePath$imgName"

			MangaPage(
				id = generateUid(imageUrl),
				url = imageUrl,
				preview = null,
				source = source,
			)
		}
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val segments = link.pathSegments.filter { it.isNotEmpty() }
		val seriesIndex = segments.indexOf("series")
		val mangaId = if (seriesIndex != -1) segments.getOrNull(seriesIndex + 1) else null
		if (mangaId.isNullOrEmpty()) return null
		return resolver.resolveManga(
			parser = this,
			url = "/manga/detail/$mangaId",
			id = generateUid(mangaId),
		)
	}

	private suspend fun fetchTags(): Set<MangaTag> {
		val tags = LinkedHashSet<MangaTag>()
		val seenTitles = HashSet<String>()

		val genreJson = webClient.httpGet("https://$apiSuffix/taxonomy/list?type=genre&limit=100").parseJson()
		appendTaxonomyTags(genreJson.optJSONArray("data"), prefix = "", seenTitles = seenTitles, out = tags)

		runCatchingCancellable {
			webClient.httpGet("https://$apiSuffix/taxonomy/list?type=theme&limit=100").parseJson()
		}.getOrNull()?.let { themeJson ->
			appendTaxonomyTags(themeJson.optJSONArray("data"), prefix = THEME_PREFIX, seenTitles = seenTitles, out = tags)
		}

		runCatchingCancellable {
			webClient.httpGet("https://$apiSuffix/taxonomy/list?type=subformat&limit=100").parseJson()
		}.getOrNull()?.let { subformatJson ->
			appendTaxonomyTags(
				subformatJson.optJSONArray("data"),
				prefix = SUBFORMAT_PREFIX,
				seenTitles = seenTitles,
				out = tags,
			)
		}

		return tags
	}

	private fun appendTaxonomyTags(
		array: JSONArray?,
		prefix: String,
		seenTitles: MutableSet<String>,
		out: MutableSet<MangaTag>,
	) {
		if (array == null) return
		for (i in 0 until array.length()) {
			val item = array.getJSONObject(i)
			val slug = item.getStringOrNull("slug") ?: continue
			val rawName = item.getStringOrNull("name")?.trim() ?: continue
			val title = rawName.toTitleCase(sourceLocale)
			if (seenTitles.add(title.lowercase(Locale.ROOT))) {
				out.add(
					MangaTag(
						key = "$prefix$slug",
						title = title,
						source = source,
					),
				)
			}
		}
	}

	private fun parseAuthors(taxonomy: JSONObject?): Set<String> {
		val array = taxonomy?.optJSONArray("Author") ?: return emptySet()
		val authors = LinkedHashSet<String>(array.length())
		for (i in 0 until array.length()) {
			val item = array.getJSONObject(i)
			val name = item.getStringOrNull("name")?.trim() ?: continue
			authors.add(name)
			val slug = item.getStringOrNull("slug")?.trim()
			if (!slug.isNullOrEmpty()) {
				authorSlugCache[name.lowercase(Locale.ROOT)] = slug
			}
		}
		return authors
	}

	private fun parseTaxonomyTags(taxonomy: JSONObject?, titleCase: Boolean): Set<MangaTag> {
		if (taxonomy == null) return emptySet()
		val tags = LinkedHashSet<MangaTag>()
		val seenTitles = HashSet<String>()

		fun collect(keyName: String, prefix: String) {
			val array = taxonomy.optJSONArray(keyName) ?: return
			for (i in 0 until array.length()) {
				val item = array.getJSONObject(i)
				val slug = item.getStringOrNull("slug") ?: continue
				val rawName = item.getStringOrNull("name")?.trim() ?: continue
				val title = if (titleCase) rawName.toTitleCase(sourceLocale) else rawName
				if (seenTitles.add(title.lowercase(Locale.ROOT))) {
					tags.add(
						MangaTag(
							key = "$prefix$slug",
							title = title,
							source = source,
						),
					)
				}
			}
		}

		collect("Genre", "")
		collect("Theme", THEME_PREFIX)
		collect("Subformat", SUBFORMAT_PREFIX)
		return tags
	}

	private fun parseAltTitles(raw: String?): Set<String> {
		if (raw.isNullOrBlank()) return emptySet()
		return raw.split('|')
			.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
			.toSet()
	}

	private fun parseState(status: Int): MangaState? = when (status) {
		1 -> MangaState.ONGOING
		2 -> MangaState.FINISHED
		3 -> MangaState.PAUSED
		else -> null
	}

	private fun parseRating(jo: JSONObject): Float {
		val rate = jo.getFloatOrDefault("user_rate", -1f)
		return if (rate > 0f) (rate / 10f).coerceIn(0f, 1f) else RATING_UNKNOWN
	}

	private fun String.parseDate(): Long {
		return try {
			val normalized = replace(REGEX_MILLIS_UTC, "Z")
			SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
				.apply {
					timeZone = TimeZone.getTimeZone("UTC")
				}
				.parse(normalized)?.time ?: 0L
		} catch (e: Exception) {
			0L
		}
	}

	private companion object {
		const val THEME_PREFIX = "theme:"
		const val SUBFORMAT_PREFIX = "subformat:"
		val REGEX_SPACES = Regex("\\s+")
		val REGEX_MILLIS_UTC = Regex("\\.\\d+Z$")
		val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
	}
}
