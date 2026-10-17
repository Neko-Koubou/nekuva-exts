package org.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

public open class JSONException : Exception {
	public constructor(message: String?) : super(message)
	public constructor(message: String?, cause: Throwable?) : super(message, cause)
	public constructor(cause: Throwable?) : super(cause)
}

internal val lenientJson = Json {
	isLenient = true
	ignoreUnknownKeys = true
	allowSpecialFloatingPointValues = true
	allowStructuredMapKeys = true
}

internal fun jsonElementToAny(element: JsonElement): Any {
	return when (element) {
		is JsonNull -> JSONObject.NULL
		is JsonObject -> JSONObject(element)
		is JsonArray -> JSONArray(element)
		is JsonPrimitive -> {
			if (element.isString) {
				element.content
			} else {
				element.booleanOrNull
					?: element.intOrNull
					?: element.longOrNull
					?: element.doubleOrNull
					?: element.content
			}
		}
	}
}

internal fun anyToJsonString(value: Any?): String {
	return when (value) {
		null, JSONObject.NULL -> "null"
		is JSONObject -> value.toString()
		is JSONArray -> value.toString()
		is Boolean, is Number -> value.toString()
		is Map<*, *> -> JSONObject(value).toString()
		is Collection<*> -> JSONArray(value).toString()
		is Array<*> -> JSONArray(value.toList()).toString()
		else -> JSONObject.quote(value.toString())
	}
}

public open class JSONObject {
	internal val map: MutableMap<String, Any?> = LinkedHashMap()

	public constructor()

	public constructor(json: String) {
		val trimmed = json.trimStart('\uFEFF').trim()
		if (trimmed.isEmpty()) {
			throw JSONException("Empty JSON string")
		}
		val parsed = try {
			lenientJson.parseToJsonElement(trimmed)
		} catch (e: Throwable) {
			throw JSONException("Failed to parse JSONObject: ${e.message}", e)
		}
		if (parsed !is JsonObject) {
			throw JSONException("Expected JSONObject but found ${parsed::class.simpleName}")
		}
		populateFromJsonObject(parsed)
	}

	internal constructor(jsonObj: JsonObject) {
		populateFromJsonObject(jsonObj)
	}

	public constructor(copyFrom: Map<*, *>?) {
		if (copyFrom != null) {
			for ((k, v) in copyFrom) {
				if (k != null) {
					map[k.toString()] = wrap(v)
				}
			}
		}
	}

	private fun populateFromJsonObject(jsonObj: JsonObject) {
		for ((k, v) in jsonObj) {
			map[k] = jsonElementToAny(v)
		}
	}

	public fun length(): Int = map.size

	public fun isEmpty(): Boolean = map.isEmpty()

	public fun has(name: String?): Boolean {
		if (name == null) return false
		return map.containsKey(name)
	}

	public fun isNull(name: String?): Boolean {
		if (name == null) return true
		val v = map[name]
		return v == null || v === NULL
	}

	public fun keys(): Iterator<String> = map.keys.toList().iterator()

	public fun keySet(): MutableSet<String> = map.keys

	public fun names(): JSONArray? {
		if (map.isEmpty()) return null
		return JSONArray(map.keys.toList())
	}

	public operator fun get(name: String): Any {
		val v = map[name] ?: throw JSONException("No value for $name")
		return v
	}

	public fun opt(name: String?): Any? {
		if (name == null) return null
		return map[name]
	}

	public fun getBoolean(name: String): Boolean {
		val v = get(name)
		return toBooleanValue(v) ?: throw JSONException("Value $v at $name cannot be converted to boolean")
	}

	public fun optBoolean(name: String?, fallback: Boolean = false): Boolean {
		val v = opt(name) ?: return fallback
		return toBooleanValue(v) ?: fallback
	}

	public fun getDouble(name: String): Double {
		val v = get(name)
		return toDoubleValue(v) ?: throw JSONException("Value $v at $name cannot be converted to double")
	}

	public fun optDouble(name: String?, fallback: Double = Double.NaN): Double {
		val v = opt(name) ?: return fallback
		return toDoubleValue(v) ?: fallback
	}

	public fun getFloat(name: String): Float = getDouble(name).toFloat()

	public fun optFloat(name: String?, fallback: Float = Float.NaN): Float {
		val d = optDouble(name, Double.NaN)
		return if (d.isNaN()) fallback else d.toFloat()
	}

	public fun getInt(name: String): Int {
		val v = get(name)
		return toIntValue(v) ?: throw JSONException("Value $v at $name cannot be converted to int")
	}

	public fun optInt(name: String?, fallback: Int = 0): Int {
		val v = opt(name) ?: return fallback
		return toIntValue(v) ?: fallback
	}

	public fun getLong(name: String): Long {
		val v = get(name)
		return toLongValue(v) ?: throw JSONException("Value $v at $name cannot be converted to long")
	}

	public fun optLong(name: String?, fallback: Long = 0L): Long {
		val v = opt(name) ?: return fallback
		return toLongValue(v) ?: fallback
	}

	public fun getString(name: String): String {
		val v = get(name)
		if (v === NULL) throw JSONException("Value at $name is null")
		return v.toString()
	}

	public fun optString(name: String?): String = optString(name, "")

	public fun optString(name: String?, fallback: String?): String {
		val v = opt(name)
		if (v == null || v === NULL) return fallback ?: ""
		return v.toString()
	}

	public fun getJSONArray(name: String): JSONArray {
		val v = get(name)
		if (v is JSONArray) return v
		throw JSONException("Value $v at $name is not a JSONArray")
	}

	public fun optJSONArray(name: String?): JSONArray? {
		val v = opt(name)
		return v as? JSONArray
	}

	public fun getJSONObject(name: String): JSONObject {
		val v = get(name)
		if (v is JSONObject) return v
		throw JSONException("Value $v at $name is not a JSONObject")
	}

	public fun optJSONObject(name: String?): JSONObject? {
		val v = opt(name)
		return v as? JSONObject
	}

	public fun put(name: String, value: Boolean): JSONObject {
		map[name] = value
		return this
	}

	public fun put(name: String, value: Double): JSONObject {
		map[name] = value
		return this
	}

	public fun put(name: String, value: Int): JSONObject {
		map[name] = value
		return this
	}

	public fun put(name: String, value: Long): JSONObject {
		map[name] = value
		return this
	}

	public fun put(name: String, value: Any?): JSONObject {
		if (value == null) {
			map.remove(name)
		} else {
			map[name] = value
		}
		return this
	}

	public fun putOpt(name: String?, value: Any?): JSONObject {
		if (name != null && value != null) {
			put(name, value)
		}
		return this
	}

	public fun remove(name: String?): Any? {
		if (name == null) return null
		return map.remove(name)
	}

	override fun toString(): String {
		val sb = StringBuilder("{")
		var first = true
		for ((k, v) in map) {
			if (!first) sb.append(',')
			first = false
			sb.append(quote(k)).append(':').append(anyToJsonString(v))
		}
		sb.append('}')
		return sb.toString()
	}

	public fun toString(indentSpaces: Int): String = toString()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is JSONObject) return false
		return map == other.map
	}

	override fun hashCode(): Int = map.hashCode()

	public companion object {
		public val NULL: Any = object {
			override fun equals(other: Any?): Boolean = other === this || other == null
			override fun hashCode(): Int = 0
			override fun toString(): String = "null"
		}

		public fun wrap(o: Any?): Any? {
			return when (o) {
				null -> NULL
				NULL, is JSONObject, is JSONArray, is Boolean, is Number, is String -> o
				is Collection<*> -> JSONArray(o)
				is Array<*> -> JSONArray(o.toList())
				is Map<*, *> -> JSONObject(o)
				else -> o.toString()
			}
		}

		public fun quote(data: String?): String {
			if (data == null) return "\"\""
			val sb = StringBuilder("\"")
			for (ch in data) {
				when (ch) {
					'\\' -> sb.append("\\\\")
					'"' -> sb.append("\\\"")
					'\b' -> sb.append("\\b")
					'\u000C' -> sb.append("\\f")
					'\n' -> sb.append("\\n")
					'\r' -> sb.append("\\r")
					'\t' -> sb.append("\\t")
					else -> {
						if (ch < ' ') {
							val hex = ch.code.toString(16).padStart(4, '0')
							sb.append("\\u").append(hex)
						} else {
							sb.append(ch)
						}
					}
				}
			}
			sb.append('"')
			return sb.toString()
		}

		internal fun toBooleanValue(v: Any?): Boolean? = when (v) {
			is Boolean -> v
			is String -> when {
				v.equals("true", ignoreCase = true) -> true
				v.equals("false", ignoreCase = true) -> false
				else -> null
			}
			is Number -> v.toInt() != 0
			else -> null
		}

		internal fun toDoubleValue(v: Any?): Double? = when (v) {
			is Number -> v.toDouble()
			is String -> v.toDoubleOrNull()
			else -> null
		}

		internal fun toIntValue(v: Any?): Int? = when (v) {
			is Number -> v.toInt()
			is String -> v.toIntOrNull() ?: v.toDoubleOrNull()?.toInt()
			else -> null
		}

		internal fun toLongValue(v: Any?): Long? = when (v) {
			is Number -> v.toLong()
			is String -> v.toLongOrNull() ?: v.toDoubleOrNull()?.toLong()
			else -> null
		}
	}
}

public open class JSONArray : Iterable<Any?> {
	internal val list: MutableList<Any?> = ArrayList()

	public constructor()

	public constructor(json: String) {
		val trimmed = json.trimStart('\uFEFF').trim()
		if (trimmed.isEmpty()) {
			throw JSONException("Empty JSONArray string")
		}
		val parsed = try {
			lenientJson.parseToJsonElement(trimmed)
		} catch (e: Throwable) {
			throw JSONException("Failed to parse JSONArray: ${e.message}", e)
		}
		if (parsed !is JsonArray) {
			throw JSONException("Expected JSONArray but found ${parsed::class.simpleName}")
		}
		populateFromJsonArray(parsed)
	}

	internal constructor(jsonArr: JsonArray) {
		populateFromJsonArray(jsonArr)
	}

	public constructor(copyFrom: Collection<*>?) {
		if (copyFrom != null) {
			for (item in copyFrom) {
				list.add(JSONObject.wrap(item))
			}
		}
	}

	public constructor(array: Any?) {
		when (array) {
			is Array<*> -> for (item in array) list.add(JSONObject.wrap(item))
			is Collection<*> -> for (item in array) list.add(JSONObject.wrap(item))
			else -> throw JSONException("Not a primitive array or collection")
		}
	}

	private fun populateFromJsonArray(jsonArr: JsonArray) {
		for (el in jsonArr) {
			list.add(jsonElementToAny(el))
		}
	}

	public fun length(): Int = list.size

	public fun isEmpty(): Boolean = list.isEmpty()

	public fun isNull(index: Int): Boolean {
		val v = opt(index)
		return v == null || v === JSONObject.NULL
	}

	public operator fun get(index: Int): Any {
		if (index < 0 || index >= list.size) {
			throw JSONException("Index $index out of range [0..${list.size})")
		}
		return list[index] ?: JSONObject.NULL
	}

	public fun opt(index: Int): Any? {
		if (index < 0 || index >= list.size) return null
		return list[index]
	}

	public fun getBoolean(index: Int): Boolean {
		val v = get(index)
		return JSONObject.toBooleanValue(v)
			?: throw JSONException("Value $v at $index cannot be converted to boolean")
	}

	public fun optBoolean(index: Int, fallback: Boolean = false): Boolean {
		val v = opt(index) ?: return fallback
		return JSONObject.toBooleanValue(v) ?: fallback
	}

	public fun getDouble(index: Int): Double {
		val v = get(index)
		return JSONObject.toDoubleValue(v)
			?: throw JSONException("Value $v at $index cannot be converted to double")
	}

	public fun optDouble(index: Int, fallback: Double = Double.NaN): Double {
		val v = opt(index) ?: return fallback
		return JSONObject.toDoubleValue(v) ?: fallback
	}

	public fun getInt(index: Int): Int {
		val v = get(index)
		return JSONObject.toIntValue(v)
			?: throw JSONException("Value $v at $index cannot be converted to int")
	}

	public fun optInt(index: Int, fallback: Int = 0): Int {
		val v = opt(index) ?: return fallback
		return JSONObject.toIntValue(v) ?: fallback
	}

	public fun getLong(index: Int): Long {
		val v = get(index)
		return JSONObject.toLongValue(v)
			?: throw JSONException("Value $v at $index cannot be converted to long")
	}

	public fun optLong(index: Int, fallback: Long = 0L): Long {
		val v = opt(index) ?: return fallback
		return JSONObject.toLongValue(v) ?: fallback
	}

	public fun getString(index: Int): String {
		val v = get(index)
		if (v === JSONObject.NULL) throw JSONException("Value at $index is null")
		return v.toString()
	}

	public fun optString(index: Int): String = optString(index, "")

	public fun optString(index: Int, fallback: String?): String {
		val v = opt(index)
		if (v == null || v === JSONObject.NULL) return fallback ?: ""
		return v.toString()
	}

	public fun getJSONObject(index: Int): JSONObject {
		val v = get(index)
		if (v is JSONObject) return v
		throw JSONException("Value $v at $index is not a JSONObject")
	}

	public fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

	public fun getJSONArray(index: Int): JSONArray {
		val v = get(index)
		if (v is JSONArray) return v
		throw JSONException("Value $v at $index is not a JSONArray")
	}

	public fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

	public fun put(value: Boolean): JSONArray {
		list.add(value)
		return this
	}

	public fun put(value: Double): JSONArray {
		list.add(value)
		return this
	}

	public fun put(value: Int): JSONArray {
		list.add(value)
		return this
	}

	public fun put(value: Long): JSONArray {
		list.add(value)
		return this
	}

	public fun put(value: Any?): JSONArray {
		list.add(value ?: JSONObject.NULL)
		return this
	}

	public fun put(index: Int, value: Any?): JSONArray {
		while (list.size <= index) {
			list.add(JSONObject.NULL)
		}
		list[index] = value ?: JSONObject.NULL
		return this
	}

	public fun remove(index: Int): Any? {
		if (index < 0 || index >= list.size) return null
		return list.removeAt(index)
	}

	override fun iterator(): Iterator<Any?> = list.iterator()

	public fun join(separator: String): String =
		list.joinToString(separator) { anyToJsonString(it) }

	override fun toString(): String {
		val sb = StringBuilder("[")
		for (i in list.indices) {
			if (i > 0) sb.append(',')
			sb.append(anyToJsonString(list[i]))
		}
		sb.append(']')
		return sb.toString()
	}

	public fun toString(indentSpaces: Int): String = toString()

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is JSONArray) return false
		return list == other.list
	}

	override fun hashCode(): Int = list.hashCode()
}
