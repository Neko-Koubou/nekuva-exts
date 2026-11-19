@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package okhttp3.dnsoverhttps

import java.net.InetAddress
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CFunction
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.cstr
import kotlinx.cinterop.invoke
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.reinterpret
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.RTLD_DEFAULT
import platform.posix.dlsym

private fun callIosConfigureDoh(url: String?) {
	try {
		val sym = dlsym(RTLD_DEFAULT, "ios_configure_doh") ?: return
		val fn = sym.reinterpret<CFunction<(CPointer<ByteVar>?) -> Unit>>()
		if (url != null) {
			memScoped {
				fn(url.cstr.ptr)
			}
		} else {
			fn(null)
		}
	} catch (_: Throwable) {
	}
}

public class DnsOverHttps private constructor(
	private val client: OkHttpClient?,
	private val url: HttpUrl?,
	private val bootstrapHosts: List<InetAddress>,
) : Dns {

	init {
		applyAppleDoH()
	}

	private fun applyAppleDoH() {
		val dohUrl = url?.toString() ?: return
		callIosConfigureDoh(dohUrl)
	}

	override fun lookup(hostname: String): List<InetAddress> {
		// 1. Try querying DoH JSON endpoint
		val dohUrl = url?.toString()
		if (dohUrl != null) {
			val jsonResult = queryDohJson(dohUrl, hostname)
			if (jsonResult.isNotEmpty()) {
				return jsonResult
			}
		}
		// 2. Fallback to system resolver
		return Dns.SYSTEM.lookup(hostname)
	}

	private fun queryDohJson(dohBaseUrl: String, hostname: String): List<InetAddress> {
		return try {
			val queryUrlStr = if (dohBaseUrl.contains("dns.google")) {
				"https://dns.google/resolve?name=$hostname&type=A"
			} else {
				val sep = if (dohBaseUrl.contains("?")) "&" else "?"
				"${dohBaseUrl}${sep}name=$hostname&type=A"
			}
			val nsUrl = NSURL.URLWithString(queryUrlStr) ?: return emptyList()
			val request = NSMutableURLRequest(
				uRL = nsUrl,
				cachePolicy = NSURLRequestReloadIgnoringLocalCacheData,
				timeoutInterval = 5.0,
			)
			request.setValue("application/dns-json", forHTTPHeaderField = "Accept")

			val sem = dispatch_semaphore_create(0)
			var respData: NSData? = null
			var httpCode = 0

			val config = NSURLSessionConfiguration.ephemeralSessionConfiguration()
			val session = NSURLSession.sessionWithConfiguration(config)
			val task = session.dataTaskWithRequest(request) { data: NSData?, response: NSURLResponse?, _: NSError? ->
				respData = data
				httpCode = (response as? NSHTTPURLResponse)?.statusCode?.toInt() ?: 0
				dispatch_semaphore_signal(sem)
			}
			task.resume()
			dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER)

			val data = respData
			if (httpCode in 200..299 && data != null && data.length > 0u) {
				val json = NSJSONSerialization.JSONObjectWithData(data, 0u, null) as? Map<*, *>
				val answers = json?.get("Answer") as? List<*>
				if (answers != null) {
					val ips = mutableListOf<InetAddress>()
					for (ans in answers) {
						val ansMap = ans as? Map<*, *> ?: continue
						val type = (ansMap["type"] as? Number)?.toInt()
						if (type == 1) { // A record
							val ip = ansMap["data"]?.toString()?.trim() ?: continue
							if (ip.split('.').size == 4) {
								ips.add(InetAddress.getByName(ip))
							}
						}
					}
					if (ips.isNotEmpty()) return ips
				}
			}
			emptyList()
		} catch (_: Throwable) {
			emptyList()
		}
	}

	public class Builder {
		private var client: OkHttpClient? = null
		private var url: HttpUrl? = null
		private val bootstrapHosts = mutableListOf<InetAddress>()

		public fun client(client: OkHttpClient): Builder {
			this.client = client
			return this
		}

		public fun url(url: HttpUrl): Builder {
			this.url = url
			return this
		}

		public fun bootstrapDnsHosts(hosts: List<InetAddress>): Builder {
			this.bootstrapHosts.addAll(hosts)
			return this
		}

		public fun bootstrapDnsHosts(vararg hosts: InetAddress): Builder {
			this.bootstrapHosts.addAll(hosts.toList())
			return this
		}

		public fun includeIPv6(includeIPv6: Boolean): Builder = this
		public fun post(post: Boolean): Builder = this
		public fun resolvePrivateAddresses(resolvePrivateAddresses: Boolean): Builder = this
		public fun resolvePublicAddresses(resolvePublicAddresses: Boolean): Builder = this

		public fun build(): DnsOverHttps = DnsOverHttps(client, url, bootstrapHosts)
	}

	public companion object {
		public fun disableDoH() {
			callIosConfigureDoh(null)
		}
	}
}
