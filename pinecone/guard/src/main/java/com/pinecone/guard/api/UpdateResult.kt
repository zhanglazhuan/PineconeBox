package com.pinecone.guard.api

sealed class UpdateResult {
    data object UpToDate : UpdateResult()

    data class Available(
        val versionCode: Int,
        val versionName: String,
        val url: String,
        val size: Long,
        val sha256: String,
        val changelog: String
    ) : UpdateResult()

    data class Error(val message: String) : UpdateResult()
}
