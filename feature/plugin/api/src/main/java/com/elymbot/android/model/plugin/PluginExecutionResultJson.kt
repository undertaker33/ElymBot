package com.elymbot.android.model.plugin

import org.json.JSONArray
import org.json.JSONObject

object PluginExecutionResultJson {
    fun decodeResult(json: JSONObject): PluginExecutionResult {
        return when (readRequiredString(json, "resultType", "resultType")) {
            "text" -> TextResult(
                text = readRequiredString(json, "text", "text"),
                markdown = json.optBoolean("markdown", false),
                displayTitle = json.optString("displayTitle"),
            )

            "card" -> CardResult(
                card = decodeCardSchema(readRequiredObject(json, "card", "card"), "card"),
            )

            "media" -> MediaResult(
                items = decodeMediaItems(readOptionalArray(json, "items"), "items"),
            )

            "host_action" -> HostActionRequest(
                action = decodeHostAction(readRequiredString(json, "action", "action"), "action"),
                title = json.optString("title"),
                payload = json.optJSONObject("payload").toStringMap(),
            )

            "settings_ui" -> SettingsUiRequest(
                schema = decodeSettingsSchema(readRequiredObject(json, "schema", "schema"), "schema"),
            )

            "noop" -> NoOp(
                reason = json.optString("reason"),
            )

            "error" -> ErrorResult(
                message = readRequiredString(json, "message", "message"),
                code = json.optString("code"),
                recoverable = json.optBoolean("recoverable", false),
            )

            else -> throw IllegalArgumentException("resultType has unsupported value")
        }
    }

    private fun decodeCardSchema(json: JSONObject, path: String): PluginCardSchema {
        return PluginCardSchema(
            title = readRequiredString(json, "title", "$path.title"),
            body = json.optString("body"),
            status = decodeUiStatus(json.optString("status").ifBlank { PluginUiStatus.Info.wireValue }, "$path.status"),
            fields = decodeCardFields(readOptionalArray(json, "fields"), "$path.fields"),
            actions = decodeCardActions(readOptionalArray(json, "actions"), "$path.actions"),
        )
    }

    private fun decodeCardFields(array: JSONArray?, path: String): List<PluginCardField> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginCardField(
                        label = readRequiredString(json, "label", "$path[$index].label"),
                        value = readRequiredString(json, "value", "$path[$index].value"),
                    ),
                )
            }
        }
    }

    private fun decodeCardActions(array: JSONArray?, path: String): List<PluginCardAction> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginCardAction(
                        actionId = readRequiredString(json, "actionId", "$path[$index].actionId"),
                        label = readRequiredString(json, "label", "$path[$index].label"),
                        style = decodeUiActionStyle(
                            json.optString("style").ifBlank { PluginUiActionStyle.Default.wireValue },
                            "$path[$index].style",
                        ),
                        payload = (readOptionalObject(json, "payload") ?: JSONObject()).toStringMap("$path[$index].payload"),
                    ),
                )
            }
        }
    }

    private fun decodeSettingsSchema(json: JSONObject, path: String): PluginSettingsSchema {
        return PluginSettingsSchema(
            title = readRequiredString(json, "title", "$path.title"),
            sections = decodeSettingsSections(readOptionalArray(json, "sections"), "$path.sections"),
        )
    }

    private fun decodeSettingsSections(array: JSONArray?, path: String): List<PluginSettingsSection> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginSettingsSection(
                        sectionId = readRequiredString(json, "sectionId", "$path[$index].sectionId"),
                        title = readRequiredString(json, "title", "$path[$index].title"),
                        fields = decodeSettingsFields(readOptionalArray(json, "fields"), "$path[$index].fields"),
                    ),
                )
            }
        }
    }

    private fun decodeSettingsFields(array: JSONArray?, path: String): List<PluginSettingsField> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                val fieldType = readRequiredString(json, "fieldType", "$path[$index].fieldType")
                val fieldId = readRequiredString(json, "fieldId", "$path[$index].fieldId")
                val label = readRequiredString(json, "label", "$path[$index].label")
                add(
                    when (fieldType) {
                        "toggle" -> ToggleSettingField(
                            fieldId = fieldId,
                            label = label,
                            defaultValue = json.optBoolean("defaultValue", false),
                        )

                        "text_input" -> TextInputSettingField(
                            fieldId = fieldId,
                            label = label,
                            placeholder = json.optString("placeholder"),
                            defaultValue = json.optString("defaultValue"),
                        )

                        "select" -> SelectSettingField(
                            fieldId = fieldId,
                            label = label,
                            defaultValue = json.optString("defaultValue"),
                            options = decodeSelectOptions(readOptionalArray(json, "options"), "$path[$index].options"),
                        )

                        else -> throw IllegalArgumentException("$path[$index].fieldType has unsupported value")
                    },
                )
            }
        }
    }

    private fun decodeSelectOptions(array: JSONArray?, path: String): List<PluginSelectOption> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginSelectOption(
                        value = readRequiredString(json, "value", "$path[$index].value"),
                        label = readRequiredString(json, "label", "$path[$index].label"),
                    ),
                )
            }
        }
    }

    private fun decodeMediaItems(array: JSONArray?, path: String): List<PluginMediaItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginMediaItem(
                        source = readRequiredString(json, "source", "$path[$index].source"),
                        mimeType = json.optString("mimeType"),
                        altText = json.optString("altText"),
                    ),
                )
            }
        }
    }

    private fun decodeHostAction(value: String, path: String): PluginHostAction {
        return PluginHostAction.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun decodeUiStatus(value: String, path: String): PluginUiStatus {
        return PluginUiStatus.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun decodeUiActionStyle(value: String, path: String): PluginUiActionStyle {
        return PluginUiActionStyle.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun JSONObject?.toStringMap(path: String = ""): Map<String, String> {
        if (this == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = opt(key)
            if (value == null || value == JSONObject.NULL) {
                continue
            }
            if (value !is String) {
                val location = if (path.isBlank()) key else "$path.$key"
                throw IllegalArgumentException("$location must be a string")
            }
            result[key] = value
        }
        return result
    }

    private fun readRequiredString(json: JSONObject, key: String, path: String): String {
        val value = json.optString(key)
        if (value.isBlank()) {
            throw IllegalArgumentException("$path is required")
        }
        return value
    }

    private fun readRequiredObject(json: JSONObject, key: String, path: String): JSONObject {
        return json.optJSONObject(key)
            ?: throw IllegalArgumentException("$path must be an object")
    }

    private fun readOptionalObject(json: JSONObject, key: String): JSONObject? {
        return json.optJSONObject(key)
    }

    private fun readOptionalArray(json: JSONObject, key: String): JSONArray? {
        return json.optJSONArray(key)
    }
}
