package com.moooo_works.letsgogps.data.model

data class FolderWithCount(
    val id: Int,
    val name: String,
    val createdAt: Long,
    val locationCount: Int
)
