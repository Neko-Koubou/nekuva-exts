@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package java.security

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA384
import platform.CoreCrypto.CC_SHA384_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH

public open class GeneralSecurityException : Exception {
	public constructor() : super()
	public constructor(message: String?) : super(message)
	public constructor(message: String?, cause: Throwable?) : super(message, cause)
	public constructor(cause: Throwable?) : super(cause)
}

public open class NoSuchAlgorithmException(message: String? = null) : GeneralSecurityException(message)
public open class InvalidKeyException(message: String? = null) : GeneralSecurityException(message)
public open class InvalidAlgorithmParameterException(message: String? = null) : GeneralSecurityException(message)
public open class SignatureException(message: String? = null) : GeneralSecurityException(message)
public open class KeyStoreException(message: String? = null) : GeneralSecurityException(message)

public interface Key : java.io.Serializable {
	public val algorithm: String
	public val format: String?
	public val encoded: ByteArray
	public fun getAlgorithm(): String = algorithm
	public fun getFormat(): String? = format
	public fun getEncoded(): ByteArray = encoded
}

public interface PublicKey : Key
public interface PrivateKey : Key

public abstract class MessageDigest protected constructor(public val algorithm: String) {
	private val buffer = java.io.ByteArrayOutputStream()

	public open val digestLength: Int
		get() = 0

	public fun getDigestLength(): Int = digestLength

	public fun update(input: Byte) {
		buffer.write(input.toInt() and 0xFF)
	}

	public fun update(input: ByteArray) {
		buffer.write(input, 0, input.size)
	}

	public fun update(input: ByteArray, offset: Int, len: Int) {
		buffer.write(input, offset, len)
	}

	public fun reset() {
		buffer.reset()
	}

	public fun digest(): ByteArray {
		val data = buffer.toByteArray()
		buffer.reset()
		return computeDigest(data)
	}

	public fun digest(input: ByteArray): ByteArray {
		update(input)
		return digest()
	}

	public fun digest(buf: ByteArray, offset: Int, len: Int): Int {
		val d = digest()
		val toCopy = minOf(len, d.size)
		d.copyInto(buf, destinationOffset = offset, startIndex = 0, endIndex = toCopy)
		return toCopy
	}

	protected abstract fun computeDigest(data: ByteArray): ByteArray

	public companion object {
		public fun getInstance(algorithm: String): MessageDigest {
			val norm = algorithm.uppercase().replace("-", "")
			return when (norm) {
				"MD5" -> CoreCryptoDigest(algorithm, CC_MD5_DIGEST_LENGTH) { src, len, out ->
					CC_MD5(src, len, out)
				}
				"SHA1", "SHA" -> CoreCryptoDigest(algorithm, CC_SHA1_DIGEST_LENGTH) { src, len, out ->
					CC_SHA1(src, len, out)
				}
				"SHA256" -> CoreCryptoDigest(algorithm, CC_SHA256_DIGEST_LENGTH) { src, len, out ->
					CC_SHA256(src, len, out)
				}
				"SHA384" -> CoreCryptoDigest(algorithm, CC_SHA384_DIGEST_LENGTH) { src, len, out ->
					CC_SHA384(src, len, out)
				}
				"SHA512" -> CoreCryptoDigest(algorithm, CC_SHA512_DIGEST_LENGTH) { src, len, out ->
					CC_SHA512(src, len, out)
				}
				else -> CoreCryptoDigest(algorithm, CC_SHA256_DIGEST_LENGTH) { src, len, out ->
					CC_SHA256(src, len, out)
				}
			}
		}
	}
}

private class CoreCryptoDigest(
	algorithm: String,
	override val digestLength: Int,
	private val hashFn: (
		kotlinx.cinterop.CPointer<*>?,
		UInt,
		kotlinx.cinterop.CPointer<kotlinx.cinterop.UByteVar>?,
	) -> kotlinx.cinterop.CPointer<kotlinx.cinterop.UByteVar>?,
) : MessageDigest(algorithm) {
	override fun computeDigest(data: ByteArray): ByteArray {
		val out = ByteArray(digestLength)
		out.usePinned { pinnedOut ->
			if (data.isEmpty()) {
				hashFn(null, 0u, pinnedOut.addressOf(0).reinterpret())
			} else {
				data.usePinned { pinnedIn ->
					hashFn(
						pinnedIn.addressOf(0),
						data.size.toUInt(),
						pinnedOut.addressOf(0).reinterpret(),
					)
				}
			}
		}
		return out
	}
}

public open class SecureRandom : java.util.Random() {
	public companion object {
		public fun getInstance(algorithm: String): SecureRandom = SecureRandom()
	}
}

public class KeyFactory private constructor(public val algorithm: String) {
	public fun generatePublic(keySpec: java.security.spec.KeySpec): PublicKey {
		val encoded = (keySpec as? java.security.spec.X509EncodedKeySpec)?.encoded ?: ByteArray(0)
		return object : PublicKey {
			override val algorithm: String = this@KeyFactory.algorithm
			override val format: String = "X.509"
			override val encoded: ByteArray = encoded
		}
	}

	public fun generatePrivate(keySpec: java.security.spec.KeySpec): PrivateKey {
		return object : PrivateKey {
			override val algorithm: String = this@KeyFactory.algorithm
			override val format: String = "PKCS#8"
			override val encoded: ByteArray = ByteArray(0)
		}
	}

	public companion object {
		public fun getInstance(algorithm: String): KeyFactory = KeyFactory(algorithm)
	}
}

public class Signature private constructor(public val algorithm: String) {
	public fun initVerify(publicKey: PublicKey) {}
	public fun initSign(privateKey: PrivateKey) {}
	public fun update(data: ByteArray) {}
	public fun update(data: ByteArray, off: Int, len: Int) {}
	public fun verify(signature: ByteArray): Boolean = true
	public fun sign(): ByteArray = ByteArray(0)

	public companion object {
		public fun getInstance(algorithm: String): Signature = Signature(algorithm)
	}
}

public class KeyStore private constructor(public val type: String) {
	public fun load(stream: java.io.InputStream?, password: CharArray?) {}
	public fun setCertificateEntry(alias: String, cert: java.security.cert.Certificate) {}
	public companion object {
		public fun getDefaultType(): String = "PKCS12"
		public fun getInstance(type: String): KeyStore = KeyStore(type)
	}
}
