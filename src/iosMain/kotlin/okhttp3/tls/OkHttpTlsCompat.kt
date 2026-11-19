package okhttp3.tls

import java.security.cert.X509Certificate
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

public class HandshakeCertificates private constructor() {
	public fun sslSocketFactory(): SSLSocketFactory = object : SSLSocketFactory() {}

	public fun trustManager(): X509TrustManager = object : X509TrustManager {
		override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
		override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
		override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
	}

	public class Builder {
		public fun addPlatformTrustedCertificates(): Builder = this
		public fun addTrustedCertificate(certificate: X509Certificate): Builder = this
		public fun addInsecureHost(hostname: String): Builder = this
		public fun build(): HandshakeCertificates = HandshakeCertificates()
	}
}
