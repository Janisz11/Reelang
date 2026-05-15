package com.example.reelang.network.models

import com.google.gson.annotations.SerializedName

data class WordLookupResponse(
    @SerializedName("term") val term: String,
    @SerializedName("clean_term") val cleanTerm: String,
    @SerializedName("language") val language: String,
    @SerializedName("definition") val definition: String?,
    @SerializedName("translation") val translation: String?,
    @SerializedName("target_lang") val targetLang: String
)
