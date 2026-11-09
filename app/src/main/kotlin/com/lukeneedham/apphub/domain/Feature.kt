package com.lukeneedham.apphub.domain

import kotlinx.serialization.Serializable

/** A feature is a new piece of work that ends in a new pull request; a tweak changes an existing pull request. */
@Serializable
enum class FeatureKind { Feature, Tweak }

@Serializable
enum class FeatureStatus {
    /** The model is reading, planning or writing code. */
    Working,

    /** The model asked a question and waits for the answer: the one time the user types after the first prompt. */
    NeedsAnswer,

    /** The changes are pushed and the CI is running. */
    WaitingForCi,

    /** The CI failed and the model is fixing it. */
    FixingCi,

    /** The CI is green and the session is closed. */
    ReadyForReview,

    Failed,
    ;

    /** A session is running for it. */
    val isActive: Boolean get() = this == Working || this == NeedsAnswer || this == WaitingForCi || this == FixingCi
}

/** One run of the automation, a feature or a tweak, as kept on the device. */
@Serializable
data class Feature(
    val id: String,
    val kind: FeatureKind,
    /** `owner/name` */
    val repo: String,
    /** What the user typed, as typed. */
    val prompt: String,
    /** A line about the work: from the prompt, then the title of the pull request. */
    val summary: String,
    val status: FeatureStatus,
    /** The pull request: given for a tweak, made by the session for a feature. */
    val prNumber: Int? = null,
    val prUrl: String? = null,
    val branch: String? = null,
    /** What the model asks, while [status] is [FeatureStatus.NeedsAnswer]. */
    val question: String? = null,
    /** Why it is [FeatureStatus.Failed]. */
    val error: String? = null,
    /** What the session did, oldest first. */
    val log: List<String> = emptyList(),
    val createdAtMillis: Long,
) {
    val isTweak: Boolean get() = kind == FeatureKind.Tweak

    fun belongsTo(repo: String, number: Int) = this.repo == repo && prNumber == number
}

private const val maxSummaryLength = 80

/** The first line of [prompt], shortened to fit a row. */
fun summarize(prompt: String): String {
    val line = prompt.trim().lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    return if (line.length <= maxSummaryLength) line else line.take(maxSummaryLength - 1).trimEnd() + "…"
}

/** The branch a feature is made on: `feature/<words of the prompt>-<id>`, so two features never meet. */
fun featureBranchName(prompt: String, id: String): String {
    val words = prompt.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).trim('-')
    return "feature/" + (if (words.isEmpty()) "" else "$words-") + id.take(6)
}
