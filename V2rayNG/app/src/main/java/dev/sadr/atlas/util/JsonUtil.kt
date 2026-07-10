package dev.sadr.atlas.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.Strictness
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import dev.sadr.atlas.AppConfig
import java.io.StringReader
import java.lang.reflect.Type

object JsonUtil {
    private var gson = Gson()

    /**
     * Converts an object to its JSON representation.
     *
     * @param src The object to convert.
     * @return The JSON representation of the object.
     */
    fun toJson(src: Any?): String {
        return gson.toJson(src)
    }

    /**
     * Parses a JSON string into an object of the specified class.
     *
     * @param src The JSON string to parse.
     * @param cls The class of the object to parse into.
     * @return The parsed object.
     */
    fun <T> fromJson(src: String, cls: Class<T>): T? {
        return gson.fromJson(src, cls)
    }

    /**
     * Safely parses a JSON string into an object of the specified class.
     * Returns null if parsing fails instead of throwing an exception.
     *
     * @param src The JSON string to parse.
     * @param cls The class of the object to parse into.
     * @return The parsed object, or null if parsing fails.
     */
    fun <T> fromJsonSafe(src: String, cls: Class<T>): T? {
        return try {
            gson.fromJson(src, cls)
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to parse JSON", e)
            null
        }
    }

    /**
     * Converts an object to its pretty-printed JSON representation.
     *
     * @param src The object to convert.
     * @return The pretty-printed JSON representation of the object, or null if the object is null.
     */
    fun toJsonPretty(src: Any?): String? {
        if (src == null)
            return null
        val gsonPre = GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter( // custom serializer is needed here since JSON by default parse number as Double, core will fail to start
                object : TypeToken<Double>() {}.type,
                JsonSerializer { src: Double?, _: Type?, _: JsonSerializationContext? ->
                    JsonPrimitive(
                        src?.toInt()
                    )
                }
            )
            .create()
        return gsonPre.toJson(src)
    }

    /**
     * Parses a JSON string into a JsonObject.
     * @return The parsed JsonObject, or null if parsing fails.
     */
    fun parseString(src: String?): JsonObject? {
        if (src.isNullOrBlank())
            return null
        
        // Attempt 1: Strict parse (fastest, handles well-formed JSON)
        try {
            return JsonParser.parseString(src).getAsJsonObject()
        } catch (_: Exception) {
            // fall through to lenient attempt
        }

        // Attempt 2: Lenient parse (handles trailing commas, unquoted keys, etc.)
        return try {
            val reader = JsonReader(StringReader(src))
            reader.setStrictness(Strictness.LENIENT)
            JsonParser.parseReader(reader).asJsonObject
        } catch (e: Exception) {
            LogUtil.d(AppConfig.TAG, "Failed to parse JSON string (both strict and lenient): ${e.message}")
            null
        }
    }
}