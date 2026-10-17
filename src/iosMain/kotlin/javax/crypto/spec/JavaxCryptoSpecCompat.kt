package javax.crypto.spec

import java.security.spec.AlgorithmParameterSpec
import java.security.spec.KeySpec
import javax.crypto.SecretKey

public class SecretKeySpec : KeySpec, SecretKey {
	private val keyBytes: ByteArray
	override val algorithm: String

	public constructor(key: ByteArray, algorithm: String) {
		this.keyBytes = key.copyOf()
		this.algorithm = algorithm
	}

	public constructor(key: ByteArray, offset: Int, len: Int, algorithm: String) {
		this.keyBytes = key.copyOfRange(offset, offset + len)
		this.algorithm = algorithm
	}

	override val format: String
		get() = "RAW"

	override val encoded: ByteArray
		get() = keyBytes.copyOf()

	override fun equals(other: Any?): Boolean =
		other is SecretKeySpec && algorithm.equals(other.algorithm, ignoreCase = true) && keyBytes.contentEquals(other.keyBytes)

	override fun hashCode(): Int = keyBytes.contentHashCode() * 31 + algorithm.lowercase().hashCode()
}

public class IvParameterSpec : AlgorithmParameterSpec {
	private val ivBytes: ByteArray

	public constructor(iv: ByteArray) {
		this.ivBytes = iv.copyOf()
	}

	public constructor(iv: ByteArray, offset: Int, len: Int) {
		this.ivBytes = iv.copyOfRange(offset, offset + len)
	}

	public val iv: ByteArray
		get() = ivBytes.copyOf()

	public fun getIV(): ByteArray = iv
}

public class PBEKeySpec(
	password: CharArray?,
	salt: ByteArray?,
	public val iterationCount: Int,
	public val keyLength: Int = 256,
) : KeySpec {
	public val password: CharArray = password?.copyOf() ?: CharArray(0)
	public val salt: ByteArray = salt?.copyOf() ?: ByteArray(0)

	public fun clearPassword() {
		password.fill('\u0000')
	}
}
