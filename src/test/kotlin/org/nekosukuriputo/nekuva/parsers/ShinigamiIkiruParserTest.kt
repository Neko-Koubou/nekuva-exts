package org.nekosukuriputo.nekuva.parsers

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.nekosukuriputo.nekuva.parsers.model.*
import org.nekosukuriputo.nekuva.parsers.util.generateUid
import org.nekosukuriputo.nekuva.parsers.util.mimeType
import java.util.EnumSet
import kotlin.time.Duration.Companion.minutes

internal class ShinigamiIkiruParserTest {

	private val context = MangaLoaderContextMock
	private val timeout = 2.minutes

	@Test
	fun shinigamiFiltersAndAuthorSearch() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(MangaParserSource.SHINIGAMI)
		val options = parser.getFilterOptions()
		assertTrue(options.availableTags.isNotEmpty())
		assertTrue(parser.filterCapabilities.isAuthorSearchSupported)
		assertTrue(parser.filterCapabilities.isTagsExclusionSupported)

		// Author search after Details populates authorSlugCache
		val sample = parser.getList(0, SortOrder.POPULARITY, MangaListFilter.EMPTY)
			.first { it.authors.isNotEmpty() }
		val details = parser.getDetails(sample)
		val cachedAuthor = details.authors.first()
		val byCachedAuthor = parser.getList(0, SortOrder.POPULARITY, MangaListFilter(author = cachedAuthor))
		assertTrue(byCachedAuthor.any { it.id == details.id }, "Expected ${details.title} when filtering by $cachedAuthor")

		// Manual author search on a fresh parser instance (including UUID-slug fallback for SIU and slug guess for Cheon Yong YJ)
		val freshParser = context.newParserInstance(MangaParserSource.SHINIGAMI)
		val siuResults = freshParser.getList(0, SortOrder.POPULARITY, MangaListFilter(author = "SIU"))
		assertTrue(siuResults.any { it.title.contains("Tower Of God", ignoreCase = true) })

		val cheonResults = freshParser.getList(0, SortOrder.POPULARITY, MangaListFilter(author = "Cheon Yong YJ"))
		assertTrue(cheonResults.any { it.title.contains("Unlimited Bacteria Magician", ignoreCase = true) })

		// Tag exclusion
		val actionTag = options.availableTags.first { it.key == "action" }
		val excluded = parser.getList(0, SortOrder.POPULARITY, MangaListFilter(tagsExclude = setOf(actionTag)))
		assertTrue(excluded.isNotEmpty())
		assertTrue(excluded.none { m -> m.tags.any { it.key == "action" } })

		// State & ContentType filters
		val ongoingManhwa = parser.getList(
			0,
			SortOrder.POPULARITY,
			MangaListFilter(
				states = EnumSet.of(MangaState.ONGOING),
				types = EnumSet.of(ContentType.MANHWA),
			),
		)
		assertTrue(ongoingManhwa.isNotEmpty())
		assertTrue(ongoingManhwa.all { it.state == MangaState.ONGOING })

		// All declared sort orders return results
		for (order in parser.availableSortOrders) {
			val sorted = parser.getList(0, order, MangaListFilter.EMPTY)
			assertTrue(sorted.isNotEmpty(), "Empty list for Shinigami sort order $order")
		}
	}

	@Test
	fun shinigamiBackwardsCompatibilityAndLinks() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(MangaParserSource.SHINIGAMI)
		val mangaUuid = "7259a06d-fe9d-4be5-8936-9ebb3cbe24d8"
		val legacySaved = Manga(
			id = parser.generateUid(mangaUuid),
			title = "Unlimited Bacteria Magician",
			altTitles = emptySet(),
			url = "/manga/detail/$mangaUuid",
			publicUrl = "https://id.shinigami.asia/series/$mangaUuid",
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = "https://storage.shngm.id/old-cover.jpg",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			source = MangaParserSource.SHINIGAMI,
		)

		val refreshed = parser.getDetails(legacySaved)
		assertEquals(legacySaved.id, refreshed.id)
		assertEquals(legacySaved.url, refreshed.url)
		assertEquals("https://s1.shinigami.mx/series/$mangaUuid", refreshed.publicUrl)
		assertTrue(refreshed.coverUrl?.contains("cdn.shinigami.gg") == true)
		val chapters = checkNotNull(refreshed.chapters)
		assertTrue(chapters.isNotEmpty())

		val pages = parser.getPages(chapters.first())
		assertTrue(pages.isNotEmpty())
		context.doRequest(parser.getPageUrl(pages.first()), MangaParserSource.SHINIGAMI).use { res ->
			assertTrue(res.isSuccessful, "Image fetch failed: ${res.code}")
			assertTrue(res.mimeType?.startsWith("image/") == true)
		}

		val resolvedNew = context.newLinkResolver("https://s1.shinigami.mx/series/$mangaUuid").getManga()
		val resolvedOld = context.newLinkResolver("https://id.shinigami.asia/series/$mangaUuid").getManga()
		assertEquals(legacySaved.id, resolvedNew?.id)
		assertEquals(legacySaved.id, resolvedOld?.id)
	}

	@Test
	fun ikiruFiltersAuthorAndYearRange() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(MangaParserSource.IKIRU)
		assertTrue(parser.filterCapabilities.isAuthorSearchSupported)
		assertTrue(parser.filterCapabilities.isYearRangeSupported)

		// Author search
		val byAuthor = parser.getList(0, SortOrder.POPULARITY, MangaListFilter(author = "Mun Baeg Kyeong"))
		assertTrue(byAuthor.any { it.title.contains("Greatest Estate Developer", ignoreCase = true) })

		// Year range & single year
		val byRange = parser.getList(0, SortOrder.POPULARITY, MangaListFilter(yearFrom = 2023, yearTo = 2024))
		assertTrue(byRange.isNotEmpty())
		val bySingleYear = parser.getList(0, SortOrder.POPULARITY, MangaListFilter(year = 2023))
		assertTrue(bySingleYear.isNotEmpty())

		// Multiple types & multiple states
		val multiFilter = parser.getList(
			0,
			SortOrder.UPDATED,
			MangaListFilter(
				types = EnumSet.of(ContentType.MANGA, ContentType.MANHWA),
				states = EnumSet.of(MangaState.ONGOING, MangaState.FINISHED),
			),
		)
		assertTrue(multiFilter.isNotEmpty())

		// All declared sort orders return results
		for (order in parser.availableSortOrders) {
			val sorted = parser.getList(0, order, MangaListFilter.EMPTY)
			assertTrue(sorted.isNotEmpty(), "Empty list for Ikiru sort order $order")
		}
	}

	@Test
	fun ikiruBackwardsCompatibilityAndLinks() = runTest(timeout = timeout) {
		val parser = context.newParserInstance(MangaParserSource.IKIRU)
		val legacySlug = "the-greatest-estate-developer"
		val legacyUrl = "/manga/$legacySlug/"
		val legacySaved = Manga(
			id = parser.generateUid(legacyUrl),
			title = "The Greatest Estate Developer",
			altTitles = emptySet(),
			url = legacyUrl,
			publicUrl = "https://07.ikiru.wtf$legacyUrl",
			rating = RATING_UNKNOWN,
			contentRating = null,
			coverUrl = "https://07.ikiru.wtf/wp-content/uploads/old.jpg",
			tags = emptySet(),
			state = null,
			authors = emptySet(),
			source = MangaParserSource.IKIRU,
		)

		val refreshed = parser.getDetails(legacySaved)
		assertEquals(legacySaved.id, refreshed.id)
		assertEquals(legacyUrl, refreshed.url)
		assertEquals("https://11.ikiru.wtf$legacyUrl", refreshed.publicUrl)
		assertTrue(refreshed.coverUrl?.contains("cdn.ikiru.id") == true)
		assertTrue(refreshed.authors.isNotEmpty())
		assertTrue(refreshed.tags.isNotEmpty())
		assertFalse(refreshed.chapters.isNullOrEmpty())

		// Legacy WordPress NatsuId chapter URL format (chapter-1.844765)
		val legacyChapter = MangaChapter(
			id = parser.generateUid("/manga/0-magic-a-high-spirit-and-a-demonic-sword/chapter-1.844765/"),
			title = "Chapter 1",
			number = 1f,
			volume = 0,
			url = "/manga/0-magic-a-high-spirit-and-a-demonic-sword/chapter-1.844765/",
			scanlator = null,
			uploadDate = 0L,
			branch = null,
			source = MangaParserSource.IKIRU,
		)
		val legacyPages = parser.getPages(legacyChapter)
		assertTrue(legacyPages.isNotEmpty())
		context.doRequest(parser.getPageUrl(legacyPages.first()), MangaParserSource.IKIRU).use { res ->
			assertTrue(res.isSuccessful, "Legacy Ikiru image fetch failed: ${res.code}")
			assertTrue(res.mimeType?.startsWith("image/") == true)
		}

		// Decimal chapter number + spaces/apostrophe in slug
		val decimalChapter = MangaChapter(
			id = parser.generateUid("/manga/Omniscient Reader's Viewpoint/chapter-237.2/"),
			title = "Chapter 237.2",
			number = 237.2f,
			volume = 0,
			url = "/manga/Omniscient Reader's Viewpoint/chapter-237.2/",
			scanlator = null,
			uploadDate = 0L,
			branch = null,
			source = MangaParserSource.IKIRU,
		)
		val decimalPages = parser.getPages(decimalChapter)
		assertTrue(decimalPages.isNotEmpty())

		// Legacy slug-keyed tag compatibility
		val legacyTag = MangaTag(key = "action", title = "Action", source = MangaParserSource.IKIRU)
		val byLegacyTag = parser.getList(0, SortOrder.POPULARITY, MangaListFilter(tags = setOf(legacyTag)))
		assertTrue(byLegacyTag.isNotEmpty())

		// Old and new links resolve to identical UID
		val resolvedNew = context.newLinkResolver("https://11.ikiru.wtf/manga/$legacySlug/").getManga()
		val resolvedOld = context.newLinkResolver("https://07.ikiru.wtf/manga/$legacySlug/").getManga()
		assertEquals(legacySaved.id, resolvedNew?.id)
		assertEquals(legacySaved.id, resolvedOld?.id)
	}

	@Test
	fun voratoonMarkedBrokenWithPermanentDomain() = runTest(timeout = timeout) {
		assertTrue(MangaParserSource.VORATOON.isBroken)
		val parser = context.newParserInstance(MangaParserSource.VORATOON)
		assertEquals("voratoon.co", parser.domain)
		assertEquals(listOf("voratoon.co", "13.voratoon.com", "v1.voratoon.com"), parser.configKeyDomain.presetValues.toList())
		assertEquals(MangaParserSource.VORATOON, context.newLinkResolver("https://voratoon.co/series/bocchi-the-rock").getSource())
		assertEquals(MangaParserSource.VORATOON, context.newLinkResolver("https://13.voratoon.com/series/bocchi-the-rock").getSource())
		assertEquals(MangaParserSource.VORATOON, context.newLinkResolver("https://v1.voratoon.com/series/bocchi-the-rock").getSource())
	}
}
