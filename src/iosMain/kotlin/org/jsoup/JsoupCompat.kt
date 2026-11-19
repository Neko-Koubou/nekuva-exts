package org.jsoup

import com.fleeksoft.ksoup.Ksoup
import java.io.InputStream
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import org.nekosukuriputo.nekuva.ioscompat.Charset
import org.nekosukuriputo.nekuva.ioscompat.Charsets

public object Jsoup {
	public fun parse(html: String, baseUri: String): Document =
		Ksoup.parse(html = html, baseUri = baseUri)

	public fun parse(html: String): Document =
		Ksoup.parse(html = html)

	public fun parse(html: String, baseUri: String, parser: Parser): Document =
		Ksoup.parse(html = html, baseUri = baseUri, parser = parser)

	public fun parse(html: String, parser: Parser): Document =
		Ksoup.parse(html = html, parser = parser)

	public fun parse(inStream: InputStream, charsetName: String?, baseUri: String): Document {
		val cs = charsetName?.let { Charset.forName(it) } ?: Charsets.UTF_8
		val html = org.nekosukuriputo.nekuva.ioscompat.String(inStream.readBytes(), cs)
		return Ksoup.parse(html = html, baseUri = baseUri)
	}

	public fun parse(inStream: InputStream, charsetName: String?, baseUri: String, parser: Parser): Document {
		val cs = charsetName?.let { Charset.forName(it) } ?: Charsets.UTF_8
		val html = org.nekosukuriputo.nekuva.ioscompat.String(inStream.readBytes(), cs)
		return Ksoup.parse(html = html, baseUri = baseUri, parser = parser)
	}

	public fun parseBodyFragment(bodyHtml: String, baseUri: String): Document =
		Ksoup.parseBodyFragment(bodyHtml = bodyHtml, baseUri = baseUri)

	public fun parseBodyFragment(bodyHtml: String): Document =
		Ksoup.parseBodyFragment(bodyHtml = bodyHtml)
}

public open class HttpStatusException(
	message: String,
	public val statusCode: Int,
	public val url: String,
) : java.io.IOException("$message. Status=$statusCode, URL=[$url]")
