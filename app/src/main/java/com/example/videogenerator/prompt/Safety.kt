package com.example.videogenerator.prompt

/**
 * Client-side mirror of server/src/safety.js for instant feedback. The server
 * re-checks everything, so keep the two lists in sync.
 */
object Safety {
    const val SFW_DIRECTIVE =
        "Content rules: keep the video safe for work. No nudity, no sexual content, no graphic gore."
    const val NSFW_DIRECTIVE =
        "Content rules: every person depicted is a consenting adult aged 18 or older with a clearly adult appearance."

    private val MINOR_TERMS = listOf(
        "child", "children", "childlike", "kid", "kids", "kiddie", "minor", "minors",
        "underage", "under-age", "under age", "preteen", "pre-teen", "preteens", "tween", "tweens",
        "teen", "teens", "teenage", "teenager", "teenagers", "adolescent", "adolescents",
        "juvenile", "juveniles", "pubescent", "prepubescent", "pre-pubescent", "infant", "infants",
        "toddler", "toddlers", "babies", "newborn", "schoolgirl", "schoolgirls", "schoolboy",
        "schoolboys", "school girl", "school boy", "loli", "lolis", "lolita", "shota", "shotacon",
        "lolicon", "jailbait", "young girl", "young girls", "young boy", "young boys",
        "little girl", "little girls", "little boy", "little boys", "daughter", "stepdaughter",
        "son", "stepson", "niece", "nephew", "grade school", "middle school", "elementary school",
        "kindergarten", "high school", "highschool", "high schooler", "junior high", "barely legal",
        "youthful body", "flat chested child", "cub",
    )

    private val minorTermRegex = Regex(
        "\\b(" + MINOR_TERMS.joinToString("|") { it.replace(Regex("[-\\s]"), "[-\\\\s]?") } + ")\\b",
        RegexOption.IGNORE_CASE,
    )

    private val underageNumberRegex = Regex(
        "\\b(?:(?:[1-9]|1[0-7])\\s*(?:-|\\s)?(?:years?|yrs?|yo|y/o|y\\.o\\.)(?:\\s*-?\\s*old)?|(?:aged?|age:)\\s*(?:[1-9]|1[0-7]))\\b",
        RegexOption.IGNORE_CASE,
    )

    /** Returns the offending phrase if [text] references minors, else null. */
    fun findMinorReference(text: String): String? =
        (minorTermRegex.find(text) ?: underageNumberRegex.find(text))?.value
}
