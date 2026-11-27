package com.example.headsuppoker.engine

import com.example.headsuppoker.model.CardDto

data class EvaluatedHand(
    val category: Int,
    val categoryName: String,
    val score: Long,
) : Comparable<EvaluatedHand> {
    override fun compareTo(other: EvaluatedHand): Int = this.score.compareTo(other.score)
}

object HandEvaluator {
    private val CATEGORY_NAMES = listOf(
        "High Card",
        "One Pair",
        "Two Pair",
        "Three of a Kind",
        "Straight",
        "Flush",
        "Full House",
        "Four of a Kind",
        "Straight Flush",
        "Royal Flush",
    )

    fun evaluateBestHand(cards: List<CardDto>): EvaluatedHand {
        val visible = cards.filter { !it.hidden && it.rank in 2..14 }
        if (visible.size < 5) {
            return EvaluatedHand(0, "High Card", 0L)
        }
        var best: EvaluatedHand? = null
        val n = visible.size
        for (a in 0 until n - 4) {
            for (b in a + 1 until n - 3) {
                for (c in b + 1 until n - 2) {
                    for (d in c + 1 until n - 1) {
                        for (e in d + 1 until n) {
                            val candidate = evaluate5Cards(
                                listOf(visible[a], visible[b], visible[c], visible[d], visible[e])
                            )
                            if (best == null || candidate.score > best.score) {
                                best = candidate
                            }
                        }
                    }
                }
            }
        }
        return best ?: EvaluatedHand(0, "High Card", 0L)
    }

    fun evaluate5Cards(cards: List<CardDto>): EvaluatedHand {
        require(cards.size == 5) { "Expected 5 cards" }
        val ranks = cards.map { it.rank }.sortedDescending()
        val suits = cards.map { it.suit }
        val isFlush = suits.all { it == suits[0] }

        val uniqueRanks = ranks.distinct()
        var isStraight = false
        var straightHigh = 0
        if (uniqueRanks.size == 5) {
            if (uniqueRanks[0] - uniqueRanks[4] == 4) {
                isStraight = true
                straightHigh = uniqueRanks[0]
            } else if (uniqueRanks == listOf(14, 5, 4, 3, 2)) {
                isStraight = true
                straightHigh = 5
            }
        }

        val groups = ranks.groupingBy { it }.eachCount().entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenByDescending { it.key })

        val category: Int
        val tiebreakers: List<Int>

        when {
            isFlush && isStraight -> {
                category = if (straightHigh == 14) 9 else 8
                tiebreakers = listOf(straightHigh)
            }
            groups[0].value == 4 -> {
                category = 7
                tiebreakers = listOf(groups[0].key, groups[1].key)
            }
            groups[0].value == 3 && groups[1].value == 2 -> {
                category = 6
                tiebreakers = listOf(groups[0].key, groups[1].key)
            }
            isFlush -> {
                category = 5
                tiebreakers = ranks
            }
            isStraight -> {
                category = 4
                tiebreakers = listOf(straightHigh)
            }
            groups[0].value == 3 -> {
                category = 3
                tiebreakers = listOf(groups[0].key, groups[1].key, groups[2].key)
            }
            groups[0].value == 2 && groups[1].value == 2 -> {
                category = 2
                tiebreakers = listOf(groups[0].key, groups[1].key, groups[2].key)
            }
            groups[0].value == 2 -> {
                category = 1
                tiebreakers = listOf(groups[0].key, groups[1].key, groups[2].key, groups[3].key)
            }
            else -> {
                category = 0
                tiebreakers = ranks
            }
        }

        var score = category.toLong()
        for (i in 0 until 5) {
            score = score * 16L + (tiebreakers.getOrNull(i) ?: 0).toLong()
        }

        return EvaluatedHand(
            category = category,
            categoryName = CATEGORY_NAMES[category],
            score = score,
        )
    }
}
