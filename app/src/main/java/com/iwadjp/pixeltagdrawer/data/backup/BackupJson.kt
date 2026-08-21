package com.iwadjp.pixeltagdrawer.data.backup

/**
 * BackupPayload の JSON (de)serialization。
 * データ形状が固定 (シンプルなobject/array/string/number/boolean/null) のため、
 * 外部JSONライブラリを追加せず最小限の手書き実装にする。
 * decode は例外を投げず、必ず BackupDecodeResult で結果を返す (Import 側の validation-first 方針に合わせる)。
 */
object BackupJson {

    fun encode(payload: BackupPayload): String = buildString {
        append('{')
        appendField("formatVersion", payload.formatVersion)
        append(',')
        appendField("exportedAt", payload.exportedAt)
        append(',')
        appendField("appVersion", payload.appVersion)
        append(',')
        append("\"tags\":[")
        payload.tags.forEachIndexed { index, tag ->
            if (index > 0) append(',')
            append('{')
            appendField("tagId", tag.tagId)
            append(',')
            appendField("name", tag.name)
            append(',')
            appendField("sortOrder", tag.sortOrder)
            append(',')
            appendNullableField("displayLabel", tag.displayLabel)
            append('}')
        }
        append("],")
        append("\"launcherApps\":[")
        payload.launcherApps.forEachIndexed { index, app ->
            if (index > 0) append(',')
            append('{')
            appendField("packageName", app.packageName)
            append(',')
            appendField("className", app.className)
            append(',')
            appendField("label", app.label)
            append(',')
            appendField("isInstalled", app.isInstalled)
            append(',')
            appendField("lastSeenAt", app.lastSeenAt)
            append(',')
            appendField("launchCount", app.launchCount)
            append(',')
            appendField("lastLaunchedAt", app.lastLaunchedAt)
            append('}')
        }
        append("],")
        append("\"appTags\":[")
        payload.appTags.forEachIndexed { index, ref ->
            if (index > 0) append(',')
            append('{')
            appendField("packageName", ref.packageName)
            append(',')
            appendField("className", ref.className)
            append(',')
            appendField("tagId", ref.tagId)
            append('}')
        }
        append("],")
        append("\"preferences\":{")
        appendField("isGridMode", payload.preferences.isGridMode)
        append(',')
        appendField("showTagManagement", payload.preferences.showTagManagement)
        append(',')
        appendField("showUntaggedOnly", payload.preferences.showUntaggedOnly)
        append(',')
        appendField("multiSelectFilter", payload.preferences.multiSelectFilter)
        append(',')
        append("\"selectedFilterTagIds\":[")
        payload.preferences.selectedFilterTagIds.forEachIndexed { index, id ->
            if (index > 0) append(',')
            append(id)
        }
        append("],")
        appendField("appSortMode", payload.preferences.appSortMode)
        append(',')
        appendNullableField("untaggedDisplayLabel", payload.preferences.untaggedDisplayLabel)
        append('}')
        append('}')
    }

    fun decode(text: String): BackupDecodeResult {
        val root = try {
            MiniJsonParser(text).parseRoot()
        } catch (e: Exception) {
            return BackupDecodeResult.InvalidJson
        }
        val obj = root as? Map<*, *> ?: return BackupDecodeResult.InvalidStructure

        val formatVersion = (obj["formatVersion"] as? Number)?.toInt()
            ?: return BackupDecodeResult.InvalidStructure
        if (formatVersion != BackupPayload.CURRENT_FORMAT_VERSION) {
            return BackupDecodeResult.UnsupportedVersion
        }
        val exportedAt = obj["exportedAt"] as? String ?: return BackupDecodeResult.InvalidStructure
        val appVersion = obj["appVersion"] as? String ?: return BackupDecodeResult.InvalidStructure

        val tagsRaw = obj["tags"] as? List<*> ?: return BackupDecodeResult.InvalidStructure
        val tags = tagsRaw.map { entry ->
            val map = entry as? Map<*, *> ?: return BackupDecodeResult.InvalidStructure
            val tagId = (map["tagId"] as? Number)?.toLong() ?: return BackupDecodeResult.InvalidStructure
            val name = map["name"] as? String ?: return BackupDecodeResult.InvalidStructure
            val sortOrder = (map["sortOrder"] as? Number)?.toInt() ?: return BackupDecodeResult.InvalidStructure
            val displayLabel = map["displayLabel"] as? String
            BackupTag(tagId = tagId, name = name, sortOrder = sortOrder, displayLabel = displayLabel)
        }

        val appsRaw = obj["launcherApps"] as? List<*> ?: return BackupDecodeResult.InvalidStructure
        val apps = appsRaw.map { entry ->
            val map = entry as? Map<*, *> ?: return BackupDecodeResult.InvalidStructure
            BackupLauncherApp(
                packageName = map["packageName"] as? String ?: return BackupDecodeResult.InvalidStructure,
                className = map["className"] as? String ?: return BackupDecodeResult.InvalidStructure,
                label = map["label"] as? String ?: return BackupDecodeResult.InvalidStructure,
                isInstalled = map["isInstalled"] as? Boolean ?: return BackupDecodeResult.InvalidStructure,
                lastSeenAt = (map["lastSeenAt"] as? Number)?.toLong() ?: return BackupDecodeResult.InvalidStructure,
                launchCount = (map["launchCount"] as? Number)?.toInt() ?: return BackupDecodeResult.InvalidStructure,
                lastLaunchedAt = (map["lastLaunchedAt"] as? Number)?.toLong()
                    ?: return BackupDecodeResult.InvalidStructure,
            )
        }

        val appTagsRaw = obj["appTags"] as? List<*> ?: return BackupDecodeResult.InvalidStructure
        val appTags = appTagsRaw.map { entry ->
            val map = entry as? Map<*, *> ?: return BackupDecodeResult.InvalidStructure
            BackupAppTag(
                packageName = map["packageName"] as? String ?: return BackupDecodeResult.InvalidStructure,
                className = map["className"] as? String ?: return BackupDecodeResult.InvalidStructure,
                tagId = (map["tagId"] as? Number)?.toLong() ?: return BackupDecodeResult.InvalidStructure,
            )
        }

        val prefsRaw = obj["preferences"] as? Map<*, *> ?: return BackupDecodeResult.InvalidStructure
        val selectedIdsRaw = prefsRaw["selectedFilterTagIds"] as? List<*> ?: return BackupDecodeResult.InvalidStructure
        val selectedIds = selectedIdsRaw.map { (it as? Number)?.toLong() ?: return BackupDecodeResult.InvalidStructure }
        val preferences = BackupPreferences(
            isGridMode = prefsRaw["isGridMode"] as? Boolean ?: return BackupDecodeResult.InvalidStructure,
            showTagManagement = prefsRaw["showTagManagement"] as? Boolean ?: return BackupDecodeResult.InvalidStructure,
            showUntaggedOnly = prefsRaw["showUntaggedOnly"] as? Boolean ?: return BackupDecodeResult.InvalidStructure,
            multiSelectFilter = prefsRaw["multiSelectFilter"] as? Boolean ?: return BackupDecodeResult.InvalidStructure,
            selectedFilterTagIds = selectedIds,
            appSortMode = prefsRaw["appSortMode"] as? String ?: return BackupDecodeResult.InvalidStructure,
            untaggedDisplayLabel = prefsRaw["untaggedDisplayLabel"] as? String,
        )

        // 参照整合性: app_tags が指す tagId は必ず tags 側に存在すること。
        val tagIds = tags.mapTo(mutableSetOf()) { it.tagId }
        if (appTags.any { it.tagId !in tagIds }) {
            return BackupDecodeResult.MalformedReferences
        }
        // 参照整合性: app_tags の packageName/className は空であってはならない
        // (端末に存在しないアプリの記録自体は許容するが、キーが壊れているものは拒否する)。
        if (appTags.any { it.packageName.isBlank() || it.className.isBlank() }) {
            return BackupDecodeResult.MalformedReferences
        }

        return BackupDecodeResult.Success(
            BackupPayload(
                formatVersion = formatVersion,
                exportedAt = exportedAt,
                appVersion = appVersion,
                tags = tags,
                launcherApps = apps,
                appTags = appTags,
                preferences = preferences,
            ),
        )
    }

    private fun StringBuilder.appendField(name: String, value: String) {
        append('"').append(name).append("\":")
        appendJsonString(value)
    }

    private fun StringBuilder.appendField(name: String, value: Int) {
        append('"').append(name).append("\":").append(value)
    }

    private fun StringBuilder.appendField(name: String, value: Long) {
        append('"').append(name).append("\":").append(value)
    }

    private fun StringBuilder.appendField(name: String, value: Boolean) {
        append('"').append(name).append("\":").append(value)
    }

    private fun StringBuilder.appendNullableField(name: String, value: String?) {
        append('"').append(name).append("\":")
        if (value == null) append("null") else appendJsonString(value)
    }

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
            }
        }
        append('"')
    }
}

/**
 * 最小の再帰下降 JSON パーサ。object/array/string/number/boolean/null のみ対応。
 * 戻り値: Map<String, Any?> / List<Any?> / String / Double or Long / Boolean / null。
 * 不正な入力は例外を投げる (呼び出し側で catch して InvalidJson に変換する)。
 */
private class MiniJsonParser(private val text: String) {
    private var pos = 0

    fun parseRoot(): Any? {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        if (pos != text.length) error("Unexpected trailing content at $pos")
        return value
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (pos >= text.length) error("Unexpected end of input")
        return when (text[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> parseNumber()
        }
    }

    private fun parseObject(): Map<String, Any?> {
        expect('{')
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') {
            pos++
            return map
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            val value = parseValue()
            map[key] = value
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    pos++
                }
                '}' -> {
                    pos++
                    return map
                }
                else -> error("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val list = ArrayList<Any?>()
        skipWhitespace()
        if (peek() == ']') {
            pos++
            return list
        }
        while (true) {
            list.add(parseValue())
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    pos++
                }
                ']' -> {
                    pos++
                    return list
                }
                else -> error("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val sb = StringBuilder()
        while (true) {
            if (pos >= text.length) error("Unterminated string")
            val c = text[pos++]
            when (c) {
                '"' -> return sb.toString()
                '\\' -> {
                    if (pos >= text.length) error("Unterminated escape")
                    when (val escaped = text[pos++]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'b' -> sb.append('\b')
                        'u' -> {
                            if (pos + 4 > text.length) error("Invalid unicode escape")
                            val hex = text.substring(pos, pos + 4)
                            sb.append(hex.toInt(16).toChar())
                            pos += 4
                        }
                        else -> error("Invalid escape '\\$escaped'")
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun parseLiteral(literal: String, value: Any?): Any? {
        if (pos + literal.length > text.length || text.substring(pos, pos + literal.length) != literal) {
            error("Expected literal '$literal' at $pos")
        }
        pos += literal.length
        return value
    }

    private fun parseNumber(): Number {
        val start = pos
        if (peek() == '-') pos++
        while (pos < text.length && text[pos].isDigit()) pos++
        var isDouble = false
        if (pos < text.length && text[pos] == '.') {
            isDouble = true
            pos++
            while (pos < text.length && text[pos].isDigit()) pos++
        }
        if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
            isDouble = true
            pos++
            if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
            while (pos < text.length && text[pos].isDigit()) pos++
        }
        val raw = text.substring(start, pos)
        if (raw.isEmpty() || raw == "-") error("Invalid number at $start")
        return if (isDouble) raw.toDouble() else (raw.toLongOrNull() ?: raw.toDouble())
    }

    private fun peek(): Char {
        skipWhitespace()
        if (pos >= text.length) error("Unexpected end of input")
        return text[pos]
    }

    private fun expect(c: Char) {
        skipWhitespace()
        if (pos >= text.length || text[pos] != c) error("Expected '$c' at $pos")
        pos++
    }

    private fun skipWhitespace() {
        while (pos < text.length && text[pos].isWhitespace()) pos++
    }

    private fun error(message: String): Nothing = throw IllegalArgumentException(message)
}
