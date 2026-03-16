package com.example.litemediaplayer.comic

import com.example.litemediaplayer.data.ComicBook

private val naturalTokenRegex = Regex("\\d+|\\D+")

internal fun compareNaturally(left: String, right: String): Int {
    if (left == right) {
        return 0
    }

    val leftTokens = naturalTokenRegex.findAll(left).map { it.value }.toList()
    val rightTokens = naturalTokenRegex.findAll(right).map { it.value }.toList()
    val pairCount = minOf(leftTokens.size, rightTokens.size)

    for (index in 0 until pairCount) {
        val leftToken = leftTokens[index]
        val rightToken = rightTokens[index]
        val comparison = compareNaturalToken(leftToken, rightToken)
        if (comparison != 0) {
            return comparison
        }
    }

    return when {
        leftTokens.size != rightTokens.size -> leftTokens.size.compareTo(rightTokens.size)
        else -> left.lowercase().compareTo(right.lowercase())
    }
}

internal val comicBookNaturalComparator: Comparator<ComicBook> = Comparator { left, right ->
    val titleComparison = compareNaturally(left.title, right.title)
    if (titleComparison != 0) {
        titleComparison
    } else {
        left.createdAt.compareTo(right.createdAt)
    }
}

private fun compareNaturalToken(left: String, right: String): Int {
    val leftIsNumber = left.firstOrNull()?.isDigit() == true
    val rightIsNumber = right.firstOrNull()?.isDigit() == true

    if (leftIsNumber && rightIsNumber) {
        return compareNumericToken(left, right)
    }

    return left.lowercase().compareTo(right.lowercase())
}

private fun compareNumericToken(left: String, right: String): Int {
    val leftTrimmed = left.trimStart('0')
    val rightTrimmed = right.trimStart('0')
    val normalizedLeft = if (leftTrimmed.isEmpty()) "0" else leftTrimmed
    val normalizedRight = if (rightTrimmed.isEmpty()) "0" else rightTrimmed

    if (normalizedLeft.length != normalizedRight.length) {
        return normalizedLeft.length.compareTo(normalizedRight.length)
    }

    val valueComparison = normalizedLeft.compareTo(normalizedRight)
    if (valueComparison != 0) {
        return valueComparison
    }

    return left.length.compareTo(right.length)
}