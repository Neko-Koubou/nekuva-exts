@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package javax.crypto

import java.security.Key
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.KeySpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CCCrypt
import platform.CoreCrypto.CCHmac
import platform.CoreCrypto.CCKeyDerivationPBKDF
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA1_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA384_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA512_DIGEST_LENGTH
import platform.CoreCrypto.kCCAlgorithmAES
import platform.CoreCrypto.kCCBlockSizeAES128
import platform.CoreCrypto.kCCDecrypt
import platform.CoreCrypto.kCCEncrypt
import platform.CoreCrypto.kCCHmacAlgMD5
import platform.CoreCrypto.kCCHmacAlgSHA1
import platform.CoreCrypto.kCCHmacAlgSHA256
import platform.CoreCrypto.kCCHmacAlgSHA384
import platform.CoreCrypto.kCCHmacAlgSHA512
import platform.CoreCrypto.kCCOptionECBMode
import platform.CoreCrypto.kCCOptionPKCS7Padding
import platform.CoreCrypto.kCCPBKDF2
import platform.CoreCrypto.kCCPRFHmacAlgSHA1
import platform.CoreCrypto.kCCPRFHmacAlgSHA256
import platform.CoreCrypto.kCCPRFHmacAlgSHA512
import platform.CoreCrypto.kCCSuccess

public interface SecretKey : Key

public class Cipher private constructor(public val transformation: String) {
	private var opmode: Int = ENCRYPT_MODE
	private var keyBytes: ByteArray = ByteArray(0)
	private var ivBytes: ByteArray? = null
	private var rc4S: IntArray? = null
	private var rc4I: Int = 0
	private var rc4J: Int = 0

	public fun init(opmode: Int, key: Key) {
		this.opmode = opmode
		this.keyBytes = key.encoded
		this.ivBytes = null
		initRc4IfNeeded()
	}

	public fun init(opmode: Int, key: Key, params: AlgorithmParameterSpec?) {
		this.opmode = opmode
		this.keyBytes = key.encoded
		this.ivBytes = (params as? IvParameterSpec)?.iv
		initRc4IfNeeded()
	}

	private fun isRc4(): Boolean {
		val upper = transformation.uppercase()
		return upper.contains("RC4") || upper.contains("ARCFOUR")
	}

	private fun initRc4IfNeeded() {
		if (!isRc4() || keyBytes.isEmpty()) {
			rc4S = null
			return
		}
		val s = IntArray(256) { it }
		var j = 0
		for (i in 0..255) {
			j = (j + s[i] + (keyBytes[i % keyBytes.size].toInt() and 0xFF)) and 0xFF
			val tmp = s[i]
			s[i] = s[j]
			s[j] = tmp
		}
		rc4S = s
		rc4I = 0
		rc4J = 0
	}

	private fun rc4Step(input: ByteArray, inputOffset: Int, inputLen: Int, output: ByteArray, outputOffset: Int): Int {
		val s = rc4S ?: return 0
		var i = rc4I
		var j = rc4J
		for (k in 0 until inputLen) {
			i = (i + 1) and 0xFF
			j = (j + s[i]) and 0xFF
			val tmp = s[i]
			s[i] = s[j]
			s[j] = tmp
			val t = (s[i] + s[j]) and 0xFF
			output[outputOffset + k] = (input[inputOffset + k].toInt() xor s[t]).toByte()
		}
		rc4I = i
		rc4J = j
		return inputLen
	}

	public fun update(input: ByteArray): ByteArray {
		val out = ByteArray(input.size)
		update(input, 0, input.size, out, 0)
		return out
	}

	public fun update(
		input: ByteArray,
		inputOffset: Int,
		inputLen: Int,
		output: ByteArray,
		outputOffset: Int = 0,
	): Int {
		if (isRc4()) {
			return rc4Step(input, inputOffset, inputLen, output, outputOffset)
		}
		val res = doFinal(input, inputOffset, inputLen)
		res.copyInto(output, destinationOffset = outputOffset)
		return res.size
	}

	public fun doFinal(input: ByteArray): ByteArray =
		doFinal(input, 0, input.size)

	public fun doFinal(input: ByteArray, inputOffset: Int, inputLen: Int): ByteArray {
		if (isRc4()) {
			val out = ByteArray(inputLen)
			rc4Step(input, inputOffset, inputLen, out, 0)
			return out
		}
		val slice = if (inputOffset == 0 && inputLen == input.size) {
			input
		} else {
			input.copyOfRange(inputOffset, inputOffset + inputLen)
		}
		val upper = transformation.uppercase()
		val isEcb = upper.contains("/ECB/") || !upper.contains("/")
		val noPadding = upper.contains("NOPADDING")
		val isCtr = upper.contains("/CTR/")

		if (isCtr) {
			return aesCtrTransform(slice, keyBytes, ivBytes ?: ByteArray(16))
		}

		val op = if (opmode == ENCRYPT_MODE) kCCEncrypt else kCCDecrypt
		var options: UInt = 0u
		if (isEcb) options = options or kCCOptionECBMode
		if (!noPadding) options = options or kCCOptionPKCS7Padding

		val outCap = slice.size + kCCBlockSizeAES128.toInt() * 2
		val outBuf = ByteArray(outCap)

		memScoped {
			val dataOutMoved = alloc<ULongVar>()
			val status = keyBytes.usePinned { pinnedKey ->
				outBuf.usePinned { pinnedOut ->
					val iv = ivBytes
					if (slice.isEmpty()) {
						if (iv != null && !isEcb) {
							iv.usePinned { pinnedIv ->
								CCCrypt(
									op,
									kCCAlgorithmAES,
									options,
									pinnedKey.addressOf(0),
									keyBytes.size.toULong(),
									pinnedIv.addressOf(0),
									null,
									0uL,
									pinnedOut.addressOf(0),
									outCap.toULong(),
									dataOutMoved.ptr,
								)
							}
						} else {
							CCCrypt(
								op,
								kCCAlgorithmAES,
								options,
								pinnedKey.addressOf(0),
								keyBytes.size.toULong(),
								null,
								null,
								0uL,
								pinnedOut.addressOf(0),
								outCap.toULong(),
								dataOutMoved.ptr,
							)
						}
					} else {
						slice.usePinned { pinnedIn ->
							if (iv != null && !isEcb) {
								iv.usePinned { pinnedIv ->
									CCCrypt(
										op,
										kCCAlgorithmAES,
										options,
										pinnedKey.addressOf(0),
										keyBytes.size.toULong(),
										pinnedIv.addressOf(0),
										pinnedIn.addressOf(0),
										slice.size.toULong(),
										pinnedOut.addressOf(0),
										outCap.toULong(),
										dataOutMoved.ptr,
									)
								}
							} else {
								CCCrypt(
									op,
									kCCAlgorithmAES,
									options,
									pinnedKey.addressOf(0),
									keyBytes.size.toULong(),
									null,
									pinnedIn.addressOf(0),
									slice.size.toULong(),
									pinnedOut.addressOf(0),
									outCap.toULong(),
									dataOutMoved.ptr,
								)
							}
						}
					}
				}
			}
			if (status != kCCSuccess) {
				throw java.security.GeneralSecurityException("CCCrypt failed with status $status for $transformation")
			}
			return outBuf.copyOf(dataOutMoved.value.toInt())
		}
	}

	private fun aesCtrTransform(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
		val ecbCipher = Cipher("AES/ECB/NoPadding").apply {
			init(ENCRYPT_MODE, SecretKeySpec(key, "AES"))
		}
		val counter = iv.copyOf(16)
		val out = ByteArray(data.size)
		var offset = 0
		while (offset < data.size) {
			val keystream = ecbCipher.doFinal(counter)
			val blockLen = minOf(16, data.size - offset)
			for (i in 0 until blockLen) {
				out[offset + i] = (data[offset + i].toInt() xor keystream[i].toInt()).toByte()
			}
			// Increment 128-bit big-endian counter
			for (i in 15 downTo 0) {
				val next = (counter[i].toInt() and 0xFF) + 1
				counter[i] = next.toByte()
				if ((next and 0x100) == 0) break
			}
			offset += blockLen
		}
		return out
	}

	public companion object {
		public const val ENCRYPT_MODE: Int = 1
		public const val DECRYPT_MODE: Int = 2

		public fun getInstance(transformation: String): Cipher = Cipher(transformation)
	}
}

public class Mac private constructor(public val algorithm: String) {
	private var keyBytes: ByteArray = ByteArray(0)
	private val buffer = java.io.ByteArrayOutputStream()

	public fun init(key: Key) {
		keyBytes = key.encoded ?: ByteArray(0)
		buffer.reset()
	}

	public fun update(input: Byte) {
		buffer.write(input.toInt() and 0xFF)
	}

	public fun update(input: ByteArray) {
		buffer.write(input, 0, input.size)
	}

	public fun update(input: ByteArray, offset: Int, len: Int) {
		buffer.write(input, offset, len)
	}

	public fun doFinal(): ByteArray {
		val data = buffer.toByteArray()
		buffer.reset()
		val norm = algorithm.uppercase().replace("-", "")
		val (alg, len) = when (norm) {
			"HMACMD5" -> kCCHmacAlgMD5 to CC_MD5_DIGEST_LENGTH
			"HMACSHA1" -> kCCHmacAlgSHA1 to CC_SHA1_DIGEST_LENGTH
			"HMACSHA384" -> kCCHmacAlgSHA384 to CC_SHA384_DIGEST_LENGTH
			"HMACSHA512" -> kCCHmacAlgSHA512 to CC_SHA512_DIGEST_LENGTH
			else -> kCCHmacAlgSHA256 to CC_SHA256_DIGEST_LENGTH
		}
		val out = ByteArray(len)
		out.usePinned { pinnedOut ->
			val k = if (keyBytes.isEmpty()) ByteArray(1) else keyBytes
			k.usePinned { pinnedKey ->
				if (data.isEmpty()) {
					CCHmac(
						alg,
						pinnedKey.addressOf(0),
						keyBytes.size.toULong(),
						null,
						0uL,
						pinnedOut.addressOf(0),
					)
				} else {
					data.usePinned { pinnedData ->
						CCHmac(
							alg,
							pinnedKey.addressOf(0),
							keyBytes.size.toULong(),
							pinnedData.addressOf(0),
							data.size.toULong(),
							pinnedOut.addressOf(0),
						)
					}
				}
			}
		}
		return out
	}

	public fun doFinal(input: ByteArray): ByteArray {
		update(input)
		return doFinal()
	}

	public companion object {
		public fun getInstance(algorithm: String): Mac = Mac(algorithm)
	}
}

public class SecretKeyFactory private constructor(public val algorithm: String) {
	public fun generateSecret(keySpec: KeySpec): SecretKey {
		if (keySpec is PBEKeySpec) {
			val prf = when {
				algorithm.contains("SHA512", ignoreCase = true) -> kCCPRFHmacAlgSHA512
				algorithm.contains("SHA256", ignoreCase = true) -> kCCPRFHmacAlgSHA256
				else -> kCCPRFHmacAlgSHA1
			}
			val derivedLen = (keySpec.keyLength / 8).coerceAtLeast(16)
			val out = ByteArray(derivedLen)
			val passStr = keySpec.password.concatToString()
			val salt = keySpec.salt
			out.usePinned { pinnedOut ->
				salt.usePinned { pinnedSalt ->
					CCKeyDerivationPBKDF(
						kCCPBKDF2,
						passStr,
						passStr.encodeToByteArray().size.toULong(),
						pinnedSalt.addressOf(0).reinterpret(),
						salt.size.toULong(),
						prf,
						keySpec.iterationCount.toUInt(),
						pinnedOut.addressOf(0).reinterpret(),
						derivedLen.toULong(),
					)
				}
			}
			return SecretKeySpec(out, algorithm)
		}
		if (keySpec is SecretKeySpec) return keySpec
		return SecretKeySpec(ByteArray(16), algorithm)
	}

	public companion object {
		public fun getInstance(algorithm: String): SecretKeyFactory = SecretKeyFactory(algorithm)
	}
}
