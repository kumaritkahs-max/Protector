package com.filevault.pro.util

object MediaQueue {
    var filePaths: List<String> = emptyList()
    var currentIndex: Int = 0

    fun set(currentPath: String, allPaths: List<String>) {
        filePaths = allPaths
        currentIndex = allPaths.indexOf(currentPath).coerceAtLeast(0)
    }

    fun hasNext(): Boolean = currentIndex < filePaths.size - 1
    fun hasPrev(): Boolean = currentIndex > 0

    fun peekNext(): String? = if (hasNext()) filePaths[currentIndex + 1] else null
    fun peekPrev(): String? = if (hasPrev()) filePaths[currentIndex - 1] else null

    fun goNext(): String? = if (hasNext()) { currentIndex++; filePaths[currentIndex] } else null
    fun goPrev(): String? = if (hasPrev()) { currentIndex--; filePaths[currentIndex] } else null

    fun clear() {
        filePaths = emptyList()
        currentIndex = 0
    }
}
