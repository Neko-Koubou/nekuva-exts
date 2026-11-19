@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package okhttp3

import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.reflect.KClass
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.nekosukuriputo.nekuva.ioscompat.Charset
import org.nekosukuriputo.nekuva.ioscompat.Charsets
import org.nekosukuriputo.nekuva.ioscompat.Class
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLRequestUseProtocolCachePolicy
import platform.Foundation.NSURLResponse
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.NSURLSessionTask
import platform.Foundation.NSURLSessionTaskDelegateProtocol
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataWithBytes
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.darwin.NSObject
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.memcpy

public enum class Protocol(private val protocol: String) {
	HTTP_1_0("http/1.0"),
	HTTP_1_1("http/1.1"),
	SPDY_3("spdy/3.1"),
	HTTP_2("h2"),
	H2_PRIOR_KNOWLEDGE("h2_prior_knowledge"),
	QUIC("hq"),
	HTTP_3("h3"),
	;

	override fun toString(): String = protocol
}

public class Request internal constructor(
	public val url: HttpUrl,
	public val method: String,
	public val headers: Headers,
	public val body: RequestBody?,
	internal val tags: Map<KClass<*>, Any>,
	public val cacheControl: CacheControl = CacheControl.Builder().build(),
) {
	public val isHttps: Boolean
		get() = url.isHttps

	public fun header(name: String): String? = headers[name]

	public fun headers(name: String): List<String> = headers.values(name)

	public fun tag(): Any? = tag(Any::class)

	@Suppress("UNCHECKED_CAST")
	public fun <T : Any> tag(type: KClass<T>): T? = tags[type] as? T

	@Suppress("UNCHECKED_CAST")
	public fun <T : Any> tag(type: Class<out T>): T? = tags[type.kClass] as? T

	public fun newBuilder(): Builder = Builder(this)

	override fun toString(): String = "Request{method=$method, url=$url, headers=$headers}"

	public open class Builder() {
		internal var url: HttpUrl? = null
		internal var method: String = "GET"
		internal var headers: Headers.Builder = Headers.Builder()
		internal var body: RequestBody? = null
		internal val tags: MutableMap<KClass<*>, Any> = LinkedHashMap()
		internal var cacheControl: CacheControl? = null

		internal constructor(request: Request) : this() {
			this.url = request.url
			this.method = request.method
			this.headers = request.headers.newBuilder()
			this.body = request.body
			this.tags.putAll(request.tags)
			this.cacheControl = request.cacheControl
		}

		public open fun url(url: HttpUrl): Builder {
			this.url = url
			return this
		}

		public open fun url(url: String): Builder {
			val finalUrl = when {
				url.startsWith("ws:", ignoreCase = true) -> "http:${url.substring(3)}"
				url.startsWith("wss:", ignoreCase = true) -> "https:${url.substring(4)}"
				else -> url
			}
			return url(finalUrl.toHttpUrl())
		}

		public open fun url(url: URL): Builder = url(url.toString().toHttpUrl())

		public open fun header(name: String, value: String): Builder {
			headers[name] = value
			return this
		}

		public open fun addHeader(name: String, value: String): Builder {
			headers.add(name, value)
			return this
		}

		public open fun removeHeader(name: String): Builder {
			headers.removeAll(name)
			return this
		}

		public open fun headers(headers: Headers): Builder {
			this.headers = headers.newBuilder()
			return this
		}

		public open fun cacheControl(cacheControl: CacheControl): Builder {
			this.cacheControl = cacheControl
			val ccValue = cacheControl.toString()
			return if (ccValue.isEmpty()) removeHeader("Cache-Control") else header("Cache-Control", ccValue)
		}

		public open fun get(): Builder = method("GET", null)

		public open fun head(): Builder = method("HEAD", null)

		public open fun post(body: RequestBody): Builder = method("POST", body)

		public open fun delete(body: RequestBody? = RequestBody.EMPTY): Builder = method("DELETE", body)

		public open fun put(body: RequestBody): Builder = method("PUT", body)

		public open fun patch(body: RequestBody): Builder = method("PATCH", body)

		public open fun method(method: String, body: RequestBody?): Builder {
			this.method = method.uppercase()
			this.body = body
			return this
		}

		public open fun tag(tag: Any?): Builder = tag(Any::class, tag)

		public open fun <T : Any> tag(type: KClass<T>, tag: T?): Builder {
			if (tag == null) {
				tags.remove(type)
			} else {
				tags[type] = tag
			}
			return this
		}

		public open fun <T : Any> tag(type: Class<in T>, tag: T?): Builder {
			if (tag == null) {
				tags.remove(type.kClass)
			} else {
				tags[type.kClass] = tag
			}
			return this
		}

		public open fun build(): Request = Request(
			url = requireNotNull(url) { "url == null" },
			method = method,
			headers = headers.build(),
			body = body,
			tags = tags.toMap(),
			cacheControl = cacheControl ?: CacheControl.Builder().build(),
		)
	}
}

public class Response internal constructor(
	public val request: Request,
	public val protocol: Protocol,
	public val message: String,
	public val code: Int,
	public val headers: Headers,
	public val body: ResponseBody,
	public val networkResponse: Response?,
	public val cacheResponse: Response?,
	public val priorResponse: Response?,
	public val sentRequestAtMillis: Long,
	public val receivedResponseAtMillis: Long,
) : Closeable {

	public val isSuccessful: Boolean
		get() = code in 200..299

	public val isRedirect: Boolean
		get() = code in listOf(300, 301, 302, 303, 307, 308)

	public fun header(name: String, defaultValue: String? = null): String? =
		headers[name] ?: defaultValue

	public fun headers(name: String): List<String> = headers.values(name)

	public fun peekBody(byteCount: Long): ResponseBody {
		val peeked = body.source().peek()
		val buffer = Buffer()
		peeked.request(byteCount)
		val toCopy = minOf(byteCount, peeked.buffer.size)
		buffer.write(peeked, toCopy)
		return ResponseBody.create(body.contentType(), buffer.size, buffer)
	}

	public fun newBuilder(): Builder = Builder(this)

	override fun close() {
		body.close()
	}

	override fun toString(): String =
		"Response{protocol=$protocol, code=$code, message=$message, url=${request.url}}"

	public open class Builder() {
		internal var request: Request? = null
		internal var protocol: Protocol = Protocol.HTTP_1_1
		internal var code: Int = -1
		internal var message: String = ""
		internal var headers: Headers.Builder = Headers.Builder()
		internal var body: ResponseBody = ResponseBody.EMPTY
		internal var networkResponse: Response? = null
		internal var cacheResponse: Response? = null
		internal var priorResponse: Response? = null
		internal var sentRequestAtMillis: Long = 0L
		internal var receivedResponseAtMillis: Long = 0L

		internal constructor(response: Response) : this() {
			this.request = response.request
			this.protocol = response.protocol
			this.code = response.code
			this.message = response.message
			this.headers = response.headers.newBuilder()
			this.body = response.body
			this.networkResponse = response.networkResponse
			this.cacheResponse = response.cacheResponse
			this.priorResponse = response.priorResponse
			this.sentRequestAtMillis = response.sentRequestAtMillis
			this.receivedResponseAtMillis = response.receivedResponseAtMillis
		}

		public open fun request(request: Request): Builder {
			this.request = request
			return this
		}

		public open fun protocol(protocol: Protocol): Builder {
			this.protocol = protocol
			return this
		}

		public open fun code(code: Int): Builder {
			this.code = code
			return this
		}

		public open fun message(message: String): Builder {
			this.message = message
			return this
		}

		public open fun header(name: String, value: String): Builder {
			headers[name] = value
			return this
		}

		public open fun addHeader(name: String, value: String): Builder {
			headers.add(name, value)
			return this
		}

		public open fun removeHeader(name: String): Builder {
			headers.removeAll(name)
			return this
		}

		public open fun headers(headers: Headers): Builder {
			this.headers = headers.newBuilder()
			return this
		}

		public open fun body(body: ResponseBody?): Builder {
			this.body = body ?: ResponseBody.EMPTY
			return this
		}

		public open fun networkResponse(networkResponse: Response?): Builder {
			this.networkResponse = networkResponse
			return this
		}

		public open fun cacheResponse(cacheResponse: Response?): Builder {
			this.cacheResponse = cacheResponse
			return this
		}

		public open fun priorResponse(priorResponse: Response?): Builder {
			this.priorResponse = priorResponse
			return this
		}

		public open fun sentRequestAtMillis(sentRequestAtMillis: Long): Builder {
			this.sentRequestAtMillis = sentRequestAtMillis
			return this
		}

		public open fun receivedResponseAtMillis(receivedResponseAtMillis: Long): Builder {
			this.receivedResponseAtMillis = receivedResponseAtMillis
			return this
		}

		public open fun build(): Response = Response(
			request = requireNotNull(request) { "request == null" },
			protocol = protocol,
			message = message,
			code = code,
			headers = headers.build(),
			body = body,
			networkResponse = networkResponse,
			cacheResponse = cacheResponse,
			priorResponse = priorResponse,
			sentRequestAtMillis = sentRequestAtMillis,
			receivedResponseAtMillis = receivedResponseAtMillis,
		)
	}
}

public interface Call {
	public fun request(): Request
	public fun execute(): Response
	public fun enqueue(responseCallback: Callback)
	public fun cancel()
	public fun isExecuted(): Boolean
	public fun isCanceled(): Boolean
	public fun clone(): Call

	public fun interface Factory {
		public fun newCall(request: Request): Call
	}
}

public interface Callback {
	public fun onFailure(call: Call, e: IOException)
	public fun onResponse(call: Call, response: Response)
}

public fun interface Interceptor {
	public fun intercept(chain: Chain): Response

	public interface Chain {
		public fun request(): Request
		public fun proceed(request: Request): Response
		public fun call(): Call
		public fun connectTimeoutMillis(): Int
		public fun withConnectTimeout(timeout: Int, unit: TimeUnit): Chain
		public fun readTimeoutMillis(): Int
		public fun withReadTimeout(timeout: Int, unit: TimeUnit): Chain
		public fun writeTimeoutMillis(): Int
		public fun withWriteTimeout(timeout: Int, unit: TimeUnit): Chain
	}

	public companion object {
		public inline operator fun invoke(crossinline block: (chain: Chain) -> Response): Interceptor =
			Interceptor { block(it) }
	}
}

public class Route(
	public val address: Any = Any(),
	public val proxy: Proxy = Proxy.NO_PROXY,
	public val socketAddress: InetSocketAddress = InetSocketAddress("127.0.0.1", 443),
)

public fun interface Authenticator {
	public fun authenticate(route: Route?, response: Response): Request?

	public companion object {
		public val NONE: Authenticator = Authenticator { _, _ -> null }
	}
}

public fun interface Dns {
	public fun lookup(hostname: String): List<InetAddress>

	public companion object {
		public val SYSTEM: Dns = Dns { hostname -> listOf(InetAddress.getByName(hostname)) }
	}
}

public class Dispatcher {
	public var maxRequests: Int = 64
	public var maxRequestsPerHost: Int = 5
	public fun cancelAll() {}
}

public class ConnectionPool(
	maxIdleConnections: Int = 5,
	keepAliveDuration: Long = 5,
	timeUnit: TimeUnit = TimeUnit.MINUTES,
) {
	public fun evictAll() {}
}

public object Credentials {
	@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
	public fun basic(username: String, password: String, charset: Charset = Charsets.ISO_8859_1): String {
		val usernameAndPassword = "$username:$password"
		val encoded = kotlin.io.encoding.Base64.Default.encode(usernameAndPassword.encodeToByteArray())
		return "Basic $encoded"
	}
}

public open class OkHttpClient internal constructor(
	builder: Builder,
) : Call.Factory {
	public constructor() : this(Builder())

	public val dispatcher: Dispatcher = builder.dispatcher
	public val interceptors: List<Interceptor> = builder.interceptors.toList()
	public val networkInterceptors: List<Interceptor> = builder.networkInterceptors.toList()
	public val cookieJar: CookieJar = builder.cookieJar
	public val cache: Cache? = builder.cache
	public val followRedirects: Boolean = builder.followRedirects
	public val followSslRedirects: Boolean = builder.followSslRedirects
	public val retryOnConnectionFailure: Boolean = builder.retryOnConnectionFailure
	public val authenticator: Authenticator = builder.authenticator
	public val proxyAuthenticator: Authenticator = builder.proxyAuthenticator
	public val dns: Dns = builder.dns
	public val proxy: Proxy? = builder.proxy
	public val connectTimeoutMillis: Int = builder.connectTimeoutMillis
	public val readTimeoutMillis: Int = builder.readTimeoutMillis
	public val writeTimeoutMillis: Int = builder.writeTimeoutMillis
	public val callTimeoutMillis: Int = builder.callTimeoutMillis

	override fun newCall(request: Request): Call = RealIosCall(this, request)

	public fun newBuilder(): Builder = Builder(this)

	public class Builder() {
		internal var dispatcher: Dispatcher = Dispatcher()
		internal val interceptors: MutableList<Interceptor> = ArrayList()
		internal val networkInterceptors: MutableList<Interceptor> = ArrayList()
		internal var cookieJar: CookieJar = CookieJar.NO_COOKIES
		internal var cache: Cache? = null
		internal var followRedirects: Boolean = true
		internal var followSslRedirects: Boolean = true
		internal var retryOnConnectionFailure: Boolean = true
		internal var authenticator: Authenticator = Authenticator.NONE
		internal var proxyAuthenticator: Authenticator = Authenticator.NONE
		internal var dns: Dns = Dns.SYSTEM
		internal var proxy: Proxy? = null
		internal var connectTimeoutMillis: Int = 15_000
		internal var readTimeoutMillis: Int = 20_000
		internal var writeTimeoutMillis: Int = 20_000
		internal var callTimeoutMillis: Int = 0

		internal constructor(client: OkHttpClient) : this() {
			this.dispatcher = client.dispatcher
			this.interceptors.addAll(client.interceptors)
			this.networkInterceptors.addAll(client.networkInterceptors)
			this.cookieJar = client.cookieJar
			this.cache = client.cache
			this.followRedirects = client.followRedirects
			this.followSslRedirects = client.followSslRedirects
			this.retryOnConnectionFailure = client.retryOnConnectionFailure
			this.authenticator = client.authenticator
			this.proxyAuthenticator = client.proxyAuthenticator
			this.dns = client.dns
			this.proxy = client.proxy
			this.connectTimeoutMillis = client.connectTimeoutMillis
			this.readTimeoutMillis = client.readTimeoutMillis
			this.writeTimeoutMillis = client.writeTimeoutMillis
			this.callTimeoutMillis = client.callTimeoutMillis
		}

		public fun dispatcher(dispatcher: Dispatcher): Builder {
			this.dispatcher = dispatcher
			return this
		}

		public fun addInterceptor(interceptor: Interceptor): Builder {
			interceptors.add(interceptor)
			return this
		}

		public inline fun addInterceptor(crossinline block: (chain: Interceptor.Chain) -> Response): Builder =
			addInterceptor(Interceptor { block(it) })

		public fun addNetworkInterceptor(interceptor: Interceptor): Builder {
			networkInterceptors.add(interceptor)
			return this
		}

		public inline fun addNetworkInterceptor(crossinline block: (chain: Interceptor.Chain) -> Response): Builder =
			addNetworkInterceptor(Interceptor { block(it) })

		public fun interceptors(): MutableList<Interceptor> = interceptors

		public fun networkInterceptors(): MutableList<Interceptor> = networkInterceptors

		public fun cookieJar(cookieJar: CookieJar): Builder {
			this.cookieJar = cookieJar
			return this
		}

		public fun cache(cache: Cache?): Builder {
			this.cache = cache
			return this
		}

		public fun dns(dns: Dns): Builder {
			this.dns = dns
			return this
		}

		public fun proxy(proxy: Proxy?): Builder {
			this.proxy = proxy
			return this
		}

		public fun proxySelector(proxySelector: java.net.ProxySelector): Builder = this

		public fun authenticator(authenticator: Authenticator): Builder {
			this.authenticator = authenticator
			return this
		}

		public fun proxyAuthenticator(proxyAuthenticator: Authenticator): Builder {
			this.proxyAuthenticator = proxyAuthenticator
			return this
		}

		public fun followRedirects(followRedirects: Boolean): Builder {
			this.followRedirects = followRedirects
			return this
		}

		public fun followSslRedirects(followProtocolRedirects: Boolean): Builder {
			this.followSslRedirects = followProtocolRedirects
			return this
		}

		public fun retryOnConnectionFailure(retryOnConnectionFailure: Boolean): Builder {
			this.retryOnConnectionFailure = retryOnConnectionFailure
			return this
		}

		public fun connectTimeout(timeout: Long, unit: TimeUnit): Builder {
			this.connectTimeoutMillis = unit.toMillis(timeout).toInt()
			return this
		}

		public fun readTimeout(timeout: Long, unit: TimeUnit): Builder {
			this.readTimeoutMillis = unit.toMillis(timeout).toInt()
			return this
		}

		public fun writeTimeout(timeout: Long, unit: TimeUnit): Builder {
			this.writeTimeoutMillis = unit.toMillis(timeout).toInt()
			return this
		}

		public fun callTimeout(timeout: Long, unit: TimeUnit): Builder {
			this.callTimeoutMillis = unit.toMillis(timeout).toInt()
			return this
		}

		public fun sslSocketFactory(
			sslSocketFactory: SSLSocketFactory,
			trustManager: X509TrustManager,
		): Builder = this

		public fun hostnameVerifier(hostnameVerifier: HostnameVerifier): Builder = this

		public fun connectionPool(connectionPool: ConnectionPool): Builder = this

		public fun protocols(protocols: List<Protocol>): Builder = this

		public fun build(): OkHttpClient = OkHttpClient(this)
	}
}

private val iosHttpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private class NoRedirectSessionDelegate : NSObject(), NSURLSessionTaskDelegateProtocol {
	override fun URLSession(
		session: NSURLSession,
		task: NSURLSessionTask,
		willPerformHTTPRedirection: NSHTTPURLResponse,
		newRequest: platform.Foundation.NSURLRequest,
		completionHandler: (platform.Foundation.NSURLRequest?) -> Unit,
	) {
		completionHandler(null)
	}
}

private val sharedUrlSession: NSURLSession by lazy {
	val config = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
		HTTPCookieAcceptPolicy = platform.Foundation.NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
		HTTPShouldSetCookies = false
	}
	NSURLSession.sessionWithConfiguration(config)
}

private val noRedirectUrlSession: NSURLSession by lazy {
	val config = NSURLSessionConfiguration.defaultSessionConfiguration().apply {
		HTTPCookieAcceptPolicy = platform.Foundation.NSHTTPCookieAcceptPolicy.NSHTTPCookieAcceptPolicyNever
		HTTPShouldSetCookies = false
	}
	NSURLSession.sessionWithConfiguration(
		config,
		delegate = NoRedirectSessionDelegate(),
		delegateQueue = NSOperationQueue(),
	)
}

internal class RealIosCall(
	private val client: OkHttpClient,
	private val originalRequest: Request,
) : Call {
	private var executed = false
	private var canceled = false
	internal var activeTask: NSURLSessionTask? = null

	override fun request(): Request = originalRequest

	override fun execute(): Response {
		executed = true
		if (canceled) throw IOException("Canceled")
		val allInterceptors = ArrayList<Interceptor>(client.interceptors.size + client.networkInterceptors.size + 2)
		allInterceptors.addAll(client.interceptors)
		allInterceptors.add(IosBridgeInterceptor(client.cookieJar))
		allInterceptors.addAll(client.networkInterceptors)
		allInterceptors.add(IosNsUrlSessionInterceptor(client, this))

		val chain = RealIosInterceptorChain(
			call = this,
			interceptors = allInterceptors,
			index = 0,
			request = originalRequest,
			connectTimeoutMillis = client.connectTimeoutMillis,
			readTimeoutMillis = client.readTimeoutMillis,
			writeTimeoutMillis = client.writeTimeoutMillis,
		)
		return chain.proceed(originalRequest)
	}

	override fun enqueue(responseCallback: Callback) {
		executed = true
		iosHttpScope.launch {
			try {
				val response = execute()
				responseCallback.onResponse(this@RealIosCall, response)
			} catch (e: IOException) {
				responseCallback.onFailure(this@RealIosCall, e)
			} catch (t: Throwable) {
				responseCallback.onFailure(this@RealIosCall, IOException(t.message ?: "HTTP failure", t))
			}
		}
	}

	override fun cancel() {
		canceled = true
		activeTask?.cancel()
	}

	override fun isExecuted(): Boolean = executed

	override fun isCanceled(): Boolean = canceled

	override fun clone(): Call = RealIosCall(client, originalRequest)
}

private class RealIosInterceptorChain(
	private val call: RealIosCall,
	private val interceptors: List<Interceptor>,
	private val index: Int,
	private val request: Request,
	private val connectTimeoutMillis: Int,
	private val readTimeoutMillis: Int,
	private val writeTimeoutMillis: Int,
) : Interceptor.Chain {

	override fun request(): Request = request

	override fun proceed(request: Request): Response {
		if (call.isCanceled()) throw IOException("Canceled")
		val next = RealIosInterceptorChain(
			call = call,
			interceptors = interceptors,
			index = index + 1,
			request = request,
			connectTimeoutMillis = connectTimeoutMillis,
			readTimeoutMillis = readTimeoutMillis,
			writeTimeoutMillis = writeTimeoutMillis,
		)
		val interceptor = interceptors[index]
		return interceptor.intercept(next)
	}

	override fun call(): Call = call

	override fun connectTimeoutMillis(): Int = connectTimeoutMillis

	override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain =
		RealIosInterceptorChain(
			call = call,
			interceptors = interceptors,
			index = index,
			request = request,
			connectTimeoutMillis = unit.toMillis(timeout.toLong()).toInt(),
			readTimeoutMillis = readTimeoutMillis,
			writeTimeoutMillis = writeTimeoutMillis,
		)

	override fun readTimeoutMillis(): Int = readTimeoutMillis

	override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain =
		RealIosInterceptorChain(
			call = call,
			interceptors = interceptors,
			index = index,
			request = request,
			connectTimeoutMillis = connectTimeoutMillis,
			readTimeoutMillis = unit.toMillis(timeout.toLong()).toInt(),
			writeTimeoutMillis = writeTimeoutMillis,
		)

	override fun writeTimeoutMillis(): Int = writeTimeoutMillis

	override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain =
		RealIosInterceptorChain(
			call = call,
			interceptors = interceptors,
			index = index,
			request = request,
			connectTimeoutMillis = connectTimeoutMillis,
			readTimeoutMillis = readTimeoutMillis,
			writeTimeoutMillis = unit.toMillis(timeout.toLong()).toInt(),
		)
}

private class IosBridgeInterceptor(private val cookieJar: CookieJar) : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val userRequest = chain.request()
		val requestBuilder = userRequest.newBuilder()

		val body = userRequest.body
		if (body != null) {
			val contentType = body.contentType()
			if (contentType != null) {
				requestBuilder.header("Content-Type", contentType.toString())
			}
			val contentLength = body.contentLength()
			if (contentLength != -1L) {
				requestBuilder.header("Content-Length", contentLength.toString())
			}
		}


		val cookies = cookieJar.loadForRequest(userRequest.url)
		if (cookies.isNotEmpty()) {
			val existingCookie = userRequest.header("Cookie")
			val jarCookieHeader = cookies.joinToString("; ") { "${it.name}=${it.value}" }
			val merged = if (existingCookie.isNullOrBlank()) {
				jarCookieHeader
			} else {
				"$existingCookie; $jarCookieHeader"
			}
			requestBuilder.header("Cookie", merged)
		}

		if (userRequest.header("User-Agent") == null) {
			requestBuilder.header(
				"User-Agent",
				"Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
			)
		}

		val networkResponse = chain.proceed(requestBuilder.build())
		val responseCookies = Cookie.parseAll(networkResponse.request.url, networkResponse.headers)
		if (responseCookies.isNotEmpty()) {
			cookieJar.saveFromResponse(networkResponse.request.url, responseCookies)
		}
		return networkResponse
	}
}

private class IosNsUrlSessionInterceptor(
	private val client: OkHttpClient,
	private val call: RealIosCall,
) : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		// Consult client.dns (DoHManager) to ensure encrypted DNS / privacy context is activated
		runCatching { client.dns.lookup(request.url.host) }
		val sentAt = org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis()
		val nsUrl = NSURL.URLWithString(request.url.toString())
			?: throw IOException("Invalid NSURL: ${request.url}")

		val timeoutSeconds = maxOf(chain.connectTimeoutMillis(), chain.readTimeoutMillis()).toDouble() / 1000.0
		val cachePolicy = if (request.cacheControl.noCache) {
			NSURLRequestReloadIgnoringLocalCacheData
		} else {
			NSURLRequestUseProtocolCachePolicy
		}

		val nsRequest = NSMutableURLRequest.requestWithURL(
			nsUrl,
			cachePolicy = cachePolicy,
			timeoutInterval = if (timeoutSeconds > 0) timeoutSeconds else 20.0,
		)
		nsRequest.setHTTPMethod(request.method)

		for ((name, value) in request.headers) {
			nsRequest.setValue(value, forHTTPHeaderField = name)
		}

		val reqBody = request.body
		if (reqBody != null && request.method != "GET" && request.method != "HEAD") {
			val buffer = Buffer()
			reqBody.writeTo(buffer)
			val bodyBytes = buffer.readByteArray()
			val nsData = if (bodyBytes.isEmpty()) {
				NSData()
			} else {
				bodyBytes.usePinned { pinned ->
					NSData.dataWithBytes(pinned.addressOf(0), bodyBytes.size.toULong())
				}
			}
			nsRequest.setHTTPBody(nsData)
		}

		val semaphore = dispatch_semaphore_create(0)
		var respData: NSData? = null
		var urlResp: NSURLResponse? = null
		var respErr: NSError? = null

		val session = if (client.followRedirects) sharedUrlSession else noRedirectUrlSession
		val task = session.dataTaskWithRequest(nsRequest) { data, response, error ->
			respData = data
			urlResp = response
			respErr = error
			dispatch_semaphore_signal(semaphore)
		}
		call.activeTask = task
		task.resume()
		dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER)

		if (call.isCanceled()) {
			throw IOException("Canceled")
		}
		val err = respErr
		if (err != null) {
			throw IOException(err.localizedDescription)
		}

		val httpResponse = urlResp as? NSHTTPURLResponse
			?: throw IOException("Non-HTTP response for ${request.url}")

		val receivedAt = org.nekosukuriputo.nekuva.ioscompat.System.currentTimeMillis()
		val statusCode = httpResponse.statusCode.toInt()
		val headersBuilder = Headers.Builder()
		val headerFields = httpResponse.allHeaderFields
		for ((k, v) in headerFields) {
			val keyStr = k?.toString() ?: continue
			val valStr = v?.toString() ?: continue
			if (keyStr.equals("Set-Cookie", ignoreCase = true)) {
				// NSHTTPURLResponse may join multiple Set-Cookie headers with ", "
				for (cookiePart in splitSetCookieHeader(valStr)) {
					headersBuilder.add("Set-Cookie", cookiePart)
				}
			} else {
				headersBuilder.add(keyStr, valStr)
			}
		}
		val responseHeaders = headersBuilder.build()

		val rawBytes = respData?.let { data ->
			val len = data.length.toInt()
			if (len == 0) {
				ByteArray(0)
			} else {
				val arr = ByteArray(len)
				arr.usePinned { pinned ->
					memcpy(pinned.addressOf(0), data.bytes, data.length)
				}
				arr
			}
		} ?: ByteArray(0)

		val contentType = responseHeaders["Content-Type"]?.toMediaTypeOrNull()
		val responseBody = rawBytes.toResponseBody(contentType)
		val finalRequestUrl = httpResponse.URL?.absoluteString?.let {
			with(HttpUrl) { it.toHttpUrlOrNull() }
		} ?: request.url

		val effectiveRequest = if (finalRequestUrl != request.url) {
			request.newBuilder().url(finalRequestUrl).build()
		} else {
			request
		}

		return Response.Builder()
			.request(effectiveRequest)
			.protocol(Protocol.HTTP_2)
			.code(statusCode)
			.message(NSHTTPURLResponse.localizedStringForStatusCode(httpResponse.statusCode))
			.headers(responseHeaders)
			.body(responseBody)
			.sentRequestAtMillis(sentAt)
			.receivedResponseAtMillis(receivedAt)
			.build()
	}

	private fun splitSetCookieHeader(raw: String): List<String> {
		// Split on ", " only when NOT inside an "Expires=Wed, 21 Oct..." attribute
		val result = ArrayList<String>()
		var start = 0
		var i = 0
		while (i < raw.length - 1) {
			if (raw[i] == ',' && raw[i + 1] == ' ') {
				val nextSegment = raw.substring(i + 2)
				val semiIdx = nextSegment.indexOf(';')
				val eqIdx = nextSegment.indexOf('=')
				if (eqIdx > 0 && (semiIdx == -1 || eqIdx < semiIdx)) {
					result.add(raw.substring(start, i).trim())
					start = i + 2
				}
			}
			i++
		}
		if (start < raw.length) {
			result.add(raw.substring(start).trim())
		}
		return result
	}
}
