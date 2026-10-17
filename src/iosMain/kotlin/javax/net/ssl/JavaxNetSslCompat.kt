package javax.net.ssl

import java.security.SecureRandom
import java.security.cert.X509Certificate

public interface TrustManager

public interface X509TrustManager : TrustManager {
	public fun checkClientTrusted(chain: Array<X509Certificate>, authType: String)
	public fun checkServerTrusted(chain: Array<X509Certificate>, authType: String)
	public fun getAcceptedIssuers(): Array<X509Certificate>
}

public interface KeyManager

public abstract class SSLSocketFactory {
	public open val defaultCipherSuites: Array<String> = emptyArray()
	public open val supportedCipherSuites: Array<String> = emptyArray()
}

public class SSLContext private constructor(public val protocol: String) {
	public fun init(km: Array<out KeyManager>?, tm: Array<out TrustManager>?, random: SecureRandom?) {}

	public val socketFactory: SSLSocketFactory = object : SSLSocketFactory() {}

	public companion object {
		public fun getInstance(protocol: String): SSLContext = SSLContext(protocol)
	}
}

public interface SSLSession

public fun interface HostnameVerifier {
	public fun verify(hostname: String, session: SSLSession): Boolean
}

public class TrustManagerFactory private constructor(public val algorithm: String) {
	public fun init(ks: java.security.KeyStore?) {}
	public val trustManagers: Array<TrustManager> = arrayOf(
		object : X509TrustManager {
			override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
			override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
			override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
		},
	)
	public companion object {
		public fun getDefaultAlgorithm(): String = "PKIX"
		public fun getInstance(algorithm: String): TrustManagerFactory = TrustManagerFactory(algorithm)
	}
}

public open class SSLException(message: String? = null, cause: Throwable? = null) : java.io.IOException(message, cause)
public open class SSLHandshakeException(message: String? = null) : SSLException(message)
public open class SSLPeerUnverifiedException(message: String? = null) : SSLException(message)
