package com.moooo_works.letsgogps.ui.savedlocations

sealed class LocationFilter {
    object All : LocationFilter()
    object Favorites : LocationFilter()
    data class Folder(val folderId: Int, val folderName: String) : LocationFilter()

    val filterMode: String
        get() = when (this) {
            is All -> "ALL"
            is Favorites -> "FAVORITES"
            is Folder -> "FOLDER"
        }

    val folderIdOrZero: Int
        get() = if (this is Folder) folderId else 0
}
