package com.campilot.core.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ExposureRecommendation(
    val iso: Int,
    val shutterSpeed: String,
    val aperture: String,
    val confidence: Float
)

data class CompositionHint(
    val description: String,
    val severity: HintSeverity
)

enum class HintSeverity { INFO, WARNING }

@Singleton
class AiRecommendationEngine @Inject constructor() {

    private val recommendationFlow = MutableStateFlow(
        ExposureRecommendation(
            iso = 100,
            shutterSpeed = "1/125",
            aperture = "f/4.0",
            confidence = 0.65f
        )
    )

    private val hintsFlow = MutableStateFlow(
        listOf(
            CompositionHint("保持地平线水平", HintSeverity.INFO)
        )
    )

    fun recommendations(): Flow<ExposureRecommendation> = recommendationFlow.asStateFlow()

    fun compositionHints(): Flow<List<CompositionHint>> = hintsFlow.asStateFlow()

    fun refresh(scene: String? = null) {
        // TODO: integrate with actual model inference.
        val iso = listOf(100, 200, 400).random()
        val shutter = listOf("1/60", "1/125", "1/250").random()
        val aperture = listOf("f/2.8", "f/4.0", "f/5.6").random()
        val confidence = Random.nextDouble(0.5, 0.95).toFloat()
        recommendationFlow.value = ExposureRecommendation(iso, shutter, aperture, confidence)
    }
}
