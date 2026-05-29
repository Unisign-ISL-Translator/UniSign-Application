package com.unisign.unisign.logic

import android.content.Context
import org.json.JSONObject

object GlossMapper {
    private var idxToGloss: Map<Int, String> = emptyMap()
    private var glossToHebrew: Map<String, String> = emptyMap()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val json = context.assets.open("gloss_mapping.json").bufferedReader().use { it.readText() }
        val root = JSONObject(json)

        val idxObj = root.getJSONObject("idx_to_gloss")
        idxToGloss = buildMap {
            for (key in idxObj.keys()) {
                put(key.toInt(), idxObj.getString(key))
            }
        }

        val hebrewObj = root.getJSONObject("gloss_hebrew")
        glossToHebrew = buildMap {
            for (key in hebrewObj.keys()) {
                put(key, hebrewObj.getString(key))
            }
        }

        initialized = true
    }

    fun idxToGloss(idx: Int): String = idxToGloss[idx] ?: "unknown"

    fun glossToHebrew(gloss: String): String = glossToHebrew[gloss] ?: gloss

    fun idxToHebrew(idx: Int): String {
        val gloss = idxToGloss(idx)
        return glossToHebrew(gloss)
    }
}
