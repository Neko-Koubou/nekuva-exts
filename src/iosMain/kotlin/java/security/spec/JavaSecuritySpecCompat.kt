package java.security.spec

public interface KeySpec

public interface AlgorithmParameterSpec

public open class EncodedKeySpec(private val encodedKey: ByteArray) : KeySpec {
	public val encoded: ByteArray
		get() = encodedKey.copyOf()

	public fun getEncoded(): ByteArray = encoded
}

public class X509EncodedKeySpec(encodedKey: ByteArray) : EncodedKeySpec(encodedKey)

public class PKCS8EncodedKeySpec(encodedKey: ByteArray) : EncodedKeySpec(encodedKey)
