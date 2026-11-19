package org.nekosukuriputo.nekuva.parsers.site.id

import okhttp3.Headers
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
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

@MangaSourceParser("IKIRU", "Ikiru", "id")
internal class Ikiru(context: MangaLoaderContext) :
	PagedMangaParser(context, MangaParserSource.IKIRU, 21) {

	override val configKeyDomain = ConfigKey.Domain(
		"11.ikiru.wtf",
		"10.ikiru.wtf",
		"09.ikiru.wtf",
		"07.ikiru.wtf",
	)

	private val defaultCdnUrl = "https://cdn.ikiru.id"
	private val genreSlugToId = ConcurrentHashMap<String, String>()

	override fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		super.onCreateConfig(keys)
		keys.add(userAgentKey)
	}

	override fun getRequestHeaders(): Headers = super.getRequestHeaders().newBuilder()
		.add(CommonHeaders.REFERER, "https://$domain/")
		.add(CommonHeaders.ORIGIN, "https://$domain")
		.build()

	override val availableSortOrders: Set<SortOrder> = EnumSet.of(
		SortOrder.UPDATED,
		SortOrder.UPDATED_ASC,
		SortOrder.POPULARITY,
		SortOrder.POPULARITY_ASC,
		SortOrder.RATING,
		SortOrder.RATING_ASC,
		SortOrder.ALPHABETICAL,
		SortOrder.ALPHABETICAL_DESC,
	)

	override val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = true,
			isSearchSupported = true,
			isSearchWithFiltersSupported = true,
			isYearRangeSupported = true,
			isAuthorSearchSupported = true,
		)

	override suspend fun getFilterOptions() = MangaListFilterOptions(
		availableTags = fetchAvailableTags(),
		availableStates = EnumSet.of(
			MangaState.ONGOING,
			MangaState.FINISHED,
			MangaState.PAUSED,
		),
		availableContentTypes = EnumSet.of(
			ContentType.MANGA,
			ContentType.MANHWA,
			ContentType.MANHUA,
			ContentType.NOVEL,
		),
	)

	override suspend fun getListPage(page: Int, order: SortOrder, filter: MangaListFilter): List<Manga> {
		if (filter.tags.any { !UUID_REGEX.matches(it.key) } && genreSlugToId.isEmpty()) {
			runCatchingCancellable { fetchAvailableTags() }
		}

		val url = urlBuilder().addPathSegments("api/public/library/search").apply {
			addQueryParameter("page", page.toString())
			addQueryParameter(
				"sortBy",
				when (order) {
					SortOrder.UPDATED, SortOrder.UPDATED_ASC -> "updated"
					SortOrder.POPULARITY, SortOrder.POPULARITY_ASC -> "popular"
					SortOrder.RATING, SortOrder.RATING_ASC -> "rating"
					SortOrder.ALPHABETICAL, SortOrder.ALPHABETICAL_DESC -> "title"
					else -> "popular"
				},
			)
			addQueryParameter(
				"sort",
				when (order) {
					SortOrder.UPDATED_ASC,
					SortOrder.POPULARITY_ASC,
					SortOrder.RATING_ASC,
					SortOrder.ALPHABETICAL,
					-> "asc"

					else -> "desc"
				},
			)

			filter.query?.trim()?.takeIf { it.isNotEmpty() }?.let { query ->
				addQueryParameter("query", query)
			}

			filter.author?.trim()?.takeIf { it.isNotEmpty() }?.let { author ->
				addQueryParameter("author", author)
			}

			for (tag in filter.tags) {
				val resolvedId = resolveGenreId(tag)
				if (resolvedId != null) {
					addQueryParameter("genre", resolvedId)
				}
			}

			for (type in filter.types) {
				val typeParam = when (type) {
					ContentType.MANGA -> "MANGA"
					ContentType.MANHWA -> "MANHWA"
					ContentType.MANHUA -> "MANHUA"
					ContentType.NOVEL -> "NOVEL"
					else -> null
				}
				if (typeParam != null) {
					addQueryParameter("type", typeParam)
				}
			}

			for (state in filter.states) {
				val statusParam = when (state) {
					MangaState.ONGOING -> "ONGOING"
					MangaState.FINISHED -> "COMPLETED"
					MangaState.PAUSED -> "HIATUS"
					else -> null
				}
				if (statusParam != null) {
					addQueryParameter("status", statusParam)
				}
			}

			if (filter.year != YEAR_UNKNOWN) {
				addQueryParameter("yearFrom", filter.year.toString())
				addQueryParameter("yearTo", filter.year.toString())
			} else {
				if (filter.yearFrom != YEAR_UNKNOWN) {
					addQueryParameter("yearFrom", filter.yearFrom.toString())
				}
				if (filter.yearTo != YEAR_UNKNOWN) {
					addQueryParameter("yearTo", filter.yearTo.toString())
				}
			}
		}.build()

		val json = webClient.httpGet(url).parseJson()
		val mangas = json.optJSONObject("data")?.optJSONArray("mangas") ?: return emptyList()

		return mangas.mapJSON { jo ->
			val slug = jo.getString("slug")
			val relativeUrl = "/manga/$slug/"
			val metadata = jo.optJSONObject("metadata")
			Manga(
				id = generateUid(relativeUrl),
				url = relativeUrl,
				publicUrl = "https://$domain$relativeUrl",
				title = jo.getString("title"),
				altTitles = emptySet(),
				coverUrl = resolveImageUrl(jo.getStringOrNull("featuredImage")),
				largeCoverUrl = null,
				description = jo.getStringOrNull("description")?.let(::cleanHtmlDescription),
				rating = parseScore(metadata),
				tags = parseGenres(metadata?.optJSONArray("genre")),
				authors = parseNames(metadata?.optJSONArray("author")),
				state = parseState(jo.getStringOrNull("status")),
				contentRating = if (jo.getBooleanOrDefault("isAdult", false)) ContentRating.ADULT else null,
				source = source,
			)
		}
	}

	override suspend fun getDetails(manga: Manga): Manga {
		val slug = extractMangaSlug(manga.url)
		val detailUrl = urlBuilder()
			.addPathSegments("api/public/manga")
			.addPathSegment(slug)
			.build()

		val json = webClient.httpGet(detailUrl).parseJson()
		val data = json.getJSONObject("data")
		val realSlug = data.getStringOrNull("slug") ?: slug
		val metadata = data.optJSONObject("metadata")

		val coverUrl = resolveImageUrl(data.getStringOrNull("featuredImage")) ?: manga.coverUrl
		val largeCoverUrl = resolveImageUrl(data.getStringOrNull("backgroundImage")) ?: coverUrl
		val parsedTags = parseGenres(metadata?.optJSONArray("genre"))
		val parsedAuthors = parseNames(metadata?.optJSONArray("author"))
		val parsedAltTitles = parseAltTitles(metadata?.opt("alternateTitles"))
		val parsedRating = parseScore(metadata)

		val chaptersObj = data.optJSONObject("chapters")
		val hasMore = chaptersObj?.getBooleanOrDefault("hasMore", false) ?: true
		val inlineChapters = chaptersObj?.optJSONArray("chapters")
		val chaptersArray = if (!hasMore && inlineChapters != null) {
			inlineChapters
		} else {
			val chaptersUrl = urlBuilder()
				.addPathSegments("api/public/manga")
				.addPathSegment(realSlug)
				.addPathSegment("chapter")
				.addQueryParameter("all", "true")
				.build()
			webClient.httpGet(chaptersUrl).parseJson()
				.optJSONObject("data")
				?.optJSONArray("chapters")
				?: JSONArray()
		}

		val chapters = chaptersArray.mapChapters(reversed = true) { _, ch ->
			val number = ch.getFloatOrDefault("number", -1f)
			val numberStr = formatChapterNumber(ch.opt("number"), number)
			if (numberStr.isEmpty()) return@mapChapters null
			val chapterUrl = "/manga/$realSlug/chapter-$numberStr/"
			val title = ch.getStringOrNull("title") ?: "Chapter $numberStr"
			val uploadDate = (ch.getStringOrNull("updatedAt") ?: ch.getStringOrNull("createdAt"))
				?.parseIsoDate() ?: 0L

			MangaChapter(
				id = generateUid(chapterUrl),
				title = title,
				number = number,
				volume = 0,
				url = chapterUrl,
				scanlator = null,
				uploadDate = uploadDate,
				branch = null,
				source = source,
			)
		}

		return manga.copy(
			publicUrl = "https://$domain/manga/$realSlug/",
			title = data.getStringOrNull("title") ?: manga.title,
			altTitles = parsedAltTitles.ifEmpty { manga.altTitles },
			description = data.getStringOrNull("description")?.let(::cleanHtmlDescription) ?: manga.description,
			coverUrl = coverUrl,
			largeCoverUrl = largeCoverUrl,
			rating = if (parsedRating != RATING_UNKNOWN) parsedRating else manga.rating,
			tags = parsedTags.ifEmpty { manga.tags },
			authors = parsedAuthors.ifEmpty { manga.authors },
			state = parseState(data.getStringOrNull("status")) ?: manga.state,
			contentRating = if (data.getBooleanOrDefault("isAdult", false)) ContentRating.ADULT else manga.contentRating,
			chapters = chapters,
		)
	}

	override suspend fun getPages(chapter: MangaChapter): List<MangaPage> {
		val afterManga = chapter.url.substringBefore('?').substringAfter("/manga/", "").trim('/')
		val mangaSlug = safeUrlDecode(afterManga.substringBefore('/'))
		val chapterSegment = afterManga.substringAfter('/', "")
		val rawNum = chapterSegment.removePrefix("chapter-").trim('/')
		val candidateNum = if (LEGACY_WP_POST_ID_REGEX.containsMatchIn(rawNum)) {
			rawNum.substringBeforeLast('.')
		} else {
			rawNum
		}

		var json = fetchChapterJson(mangaSlug, candidateNum)
		var data = json.optJSONObject("data")

		if ((json.optBoolean("success") != true || data == null) && chapter.number > 0f) {
			val fallbackNum = formatChapterNumber(null, chapter.number)
			if (fallbackNum.isNotEmpty() && fallbackNum != candidateNum) {
				json = fetchChapterJson(mangaSlug, fallbackNum)
				data = json.optJSONObject("data")
			}
		}

		val medias = data?.optJSONArray("medias") ?: return emptyList()
		val indexedUrls = ArrayList<Pair<Int, String>>(medias.length())
		for (i in 0 until medias.length()) {
			val item = medias.getJSONObject(i)
			val filePath = item.getStringOrNull("filePath") ?: continue
			val imageUrl = resolveImageUrl(filePath) ?: continue
			val pageNum = item.optInt("pageNumber", i)
			indexedUrls.add(pageNum to imageUrl)
		}

		return indexedUrls
			.sortedBy { it.first }
			.map { (_, imageUrl) ->
				MangaPage(
					id = generateUid(imageUrl),
					url = imageUrl,
					preview = null,
					source = source,
				)
			}
	}

	private suspend fun fetchChapterJson(mangaSlug: String, chapterNumber: String): JSONObject {
		val url = urlBuilder()
			.addPathSegments("api/public/chapter")
			.addPathSegment(chapterNumber)
			.addQueryParameter("mangaSlug", mangaSlug)
			.build()
		return webClient.httpGet(url).parseJson()
	}

	override suspend fun resolveLink(resolver: LinkResolver, link: HttpUrl): Manga? {
		val segments = link.pathSegments.filter { it.isNotEmpty() }
		val mangaIndex = segments.indexOf("manga")
		val slug = if (mangaIndex != -1) segments.getOrNull(mangaIndex + 1) else null
		if (slug.isNullOrEmpty()) return null
		val relativeUrl = "/manga/$slug/"
		return resolver.resolveManga(
			parser = this,
			url = relativeUrl,
			id = generateUid(relativeUrl),
		)
	}

	private suspend fun fetchAvailableTags(): Set<MangaTag> {
		val json = webClient.httpGet("https://$domain/api/user/genres").parseJson()
		val allGenres = json.optJSONObject("data")?.optJSONArray("allGenres") ?: return emptySet()
		val tags = LinkedHashSet<MangaTag>(allGenres.length())
		val seenTitles = HashSet<String>(allGenres.length())

		for (i in 0 until allGenres.length()) {
			val item = allGenres.getJSONObject(i)
			val id = item.getStringOrNull("id") ?: continue
			val rawName = item.getStringOrNull("name")?.trim() ?: continue
			val slug = item.getStringOrNull("slug")?.trim()
			if (!slug.isNullOrEmpty()) {
				genreSlugToId[slug.lowercase(Locale.ROOT)] = id
			}
			genreSlugToId[rawName.lowercase(Locale.ROOT)] = id

			val title = rawName.toTitleCase(sourceLocale)
			if (seenTitles.add(title.lowercase(Locale.ROOT))) {
				tags.add(
					MangaTag(
						key = id,
						title = title,
						source = source,
					),
				)
			}
		}

		return tags
	}

	private fun resolveGenreId(tag: MangaTag): String? {
		if (UUID_REGEX.matches(tag.key)) {
			return tag.key
		}
		return genreSlugToId[tag.key.lowercase(Locale.ROOT)]
			?: genreSlugToId[tag.title.lowercase(Locale.ROOT)]
	}

	private fun parseGenres(array: JSONArray?): Set<MangaTag> {
		if (array == null) return emptySet()
		val tags = LinkedHashSet<MangaTag>(array.length())
		for (i in 0 until array.length()) {
			val item = array.getJSONObject(i)
			val id = item.getStringOrNull("id") ?: continue
			val rawName = item.getStringOrNull("name")?.trim() ?: continue
			val slug = item.getStringOrNull("slug")?.trim()
			if (!slug.isNullOrEmpty()) {
				genreSlugToId[slug.lowercase(Locale.ROOT)] = id
			}
			genreSlugToId[rawName.lowercase(Locale.ROOT)] = id
			tags.add(
				MangaTag(
					key = id,
					title = rawName.toTitleCase(sourceLocale),
					source = source,
				),
			)
		}
		return tags
	}

	private fun parseNames(array: JSONArray?): Set<String> {
		if (array == null) return emptySet()
		val names = LinkedHashSet<String>(array.length())
		for (i in 0 until array.length()) {
			val item = array.opt(i)
			val name = when (item) {
				is JSONObject -> item.getStringOrNull("name")?.trim()
				is String -> item.trim()
				else -> null
			}
			if (!name.isNullOrEmpty()) {
				names.add(name)
			}
		}
		return names
	}

	private fun parseAltTitles(raw: Any?): Set<String> {
		if (raw == null || raw === JSONObject.NULL) return emptySet()
		return when (raw) {
			is JSONArray -> buildSet {
				for (i in 0 until raw.length()) {
					raw.optString(i).trim().takeIf(String::isNotEmpty)?.let(::add)
				}
			}

			is String -> {
				val trimmed = raw.trim()
				if (trimmed.isEmpty()) {
					emptySet()
				} else if (trimmed.startsWith('[')) {
					trimmed.toJSONArrayOrNull()?.let(::parseAltTitles) ?: emptySet()
				} else {
					trimmed.split(',')
						.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
						.toSet()
				}
			}

			else -> emptySet()
		}
	}

	private fun parseScore(metadata: JSONObject?): Float {
		val score = metadata?.getFloatOrDefault("score", -1f) ?: -1f
		return if (score > 0f) (score / 10f).coerceIn(0f, 1f) else RATING_UNKNOWN
	}

	private fun parseState(status: String?): MangaState? = when (status?.uppercase(Locale.ROOT)) {
		"ONGOING" -> MangaState.ONGOING
		"COMPLETED", "FINISHED" -> MangaState.FINISHED
		"HIATUS", "PAUSED" -> MangaState.PAUSED
		"DROPPED", "CANCELLED" -> MangaState.ABANDONED
		else -> null
	}

	private fun resolveImageUrl(raw: String?): String? {
		val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
		return if (trimmed.startsWith("http://", ignoreCase = true) ||
			trimmed.startsWith("https://", ignoreCase = true)
		) {
			trimmed
		} else {
			"$defaultCdnUrl/${trimmed.trimStart('/')}"
		}
	}

	private fun cleanHtmlDescription(raw: String): String? {
		return Jsoup.parseBodyFragment(raw).text().trim().nullIfEmpty()
	}

	private fun extractMangaSlug(rawUrl: String): String {
		val withoutQuery = rawUrl.substringBefore('?')
		val afterManga = if ("/manga/" in withoutQuery) {
			withoutQuery.substringAfter("/manga/")
		} else {
			withoutQuery
		}
		val segment = afterManga.trim('/').substringBefore('/')
		return safeUrlDecode(segment)
	}

	private fun safeUrlDecode(value: String): String {
		if (!REGEX_PERCENT_ENCODED.containsMatchIn(value)) return value
		return try {
			value.urlDecode()
		} catch (_: Exception) {
			value
		}
	}

	private fun formatChapterNumber(raw: Any?, floatVal: Float): String {
		return when (raw) {
			is Number -> {
				val d = raw.toDouble()
				if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
			}

			is String -> raw.trim()
			else -> if (floatVal >= 0f) {
				if (floatVal % 1f == 0f) floatVal.toInt().toString() else floatVal.toString()
			} else {
				""
			}
		}
	}

	private fun String.parseIsoDate(): Long {
		return try {
			val normalized = replace(REGEX_MILLIS_UTC, "Z")
			SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
				.apply {
					timeZone = TimeZone.getTimeZone("UTC")
				}
				.parse(normalized)?.time ?: 0L
		} catch (_: Exception) {
			0L
		}
	}

	private companion object {
		val UUID_REGEX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
		val LEGACY_WP_POST_ID_REGEX = Regex("\\.\\d{5,}$")
		val REGEX_MILLIS_UTC = Regex("\\.\\d+Z$")
		val REGEX_PERCENT_ENCODED = Regex("%[0-9a-fA-F]{2}")
	}
}
