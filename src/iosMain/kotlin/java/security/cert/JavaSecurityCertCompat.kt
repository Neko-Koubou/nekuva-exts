package java.security.cert

import java.security.PublicKey

public abstract class Certificate(public val type: String) : java.io.Serializable {
	public abstract val encoded: ByteArray
	public abstract val publicKey: PublicKey
}

public abstract class X509Certificate : Certificate("X.509") {
	override val encoded: ByteArray
		get() = ByteArray(0)
	override val publicKey: PublicKey
		get() = object : PublicKey {
			override val algorithm: String = "RSA"
			override val format: String = "X.509"
			override val encoded: ByteArray = ByteArray(0)
		}

	public open fun checkValidity() {}
}

public class CertificateFactory private constructor(public val type: String) {
	public fun generateCertificate(inStream: java.io.InputStream): Certificate =
		object : X509Certificate() {}

	public fun generateCertificates(inStream: java.io.InputStream): Collection<Certificate> =
		listOf(generateCertificate(inStream))

	public companion object {
		public fun getInstance(type: String): CertificateFactory = CertificateFactory(type)
	}
}
