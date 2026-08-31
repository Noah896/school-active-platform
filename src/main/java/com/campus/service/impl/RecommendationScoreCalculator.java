package com.campus.service.impl;

import org.springframework.stereotype.Component;

@Component
public class RecommendationScoreCalculator {

    private static final double BASE_WEIGHT = 0.70D;
    private static final double VECTOR_WEIGHT = 0.30D;
    private static final double DUAL_RECALL_BONUS = 5D;
    private static final double TAG_WEIGHT = 40D;
    private static final double CATEGORY_WEIGHT = 20D;
    private static final double HEAT_WEIGHT = 20D;
    private static final double TIME_WEIGHT = 10D;
    private static final double INTERACTION_WEIGHT = 10D;

    public double calculate(RecommendationServiceImpl.RecommendationCandidate candidate) {
        if (candidate == null) {
            return 0D;
        }
        double baseScore = candidate.getTagScore()
                + candidate.getCategoryScore()
                + candidate.getHeatScore()
                + candidate.getTimeScore()
                + candidate.getInteractionScore();
        double vectorScore = candidate.getVectorScore() == null ? 0D : Math.max(0D, candidate.getVectorScore());
        double finalScore = baseScore * BASE_WEIGHT + vectorScore * VECTOR_WEIGHT;
        if (Boolean.TRUE.equals(candidate.getVectorMatched())
                && Boolean.TRUE.equals(candidate.getRuleMatched())) {
            finalScore += DUAL_RECALL_BONUS;
        }
        return round(Math.min(100D, finalScore));
    }

    public double calculateTagScore(int matchedTagCount, int preferenceTagCount) {
        if (matchedTagCount <= 0 || preferenceTagCount <= 0) {
            return 0D;
        }
        double ratio = Math.min(1D, matchedTagCount / (double) preferenceTagCount);
        return round(TAG_WEIGHT * ratio);
    }

    public double calculateCategoryScore(boolean matchedPrimaryCategory) {
        return matchedPrimaryCategory ? CATEGORY_WEIGHT : 0D;
    }

    public double calculateHeatScore(int registeredCount, int maxRegisteredCount) {
        if (registeredCount <= 0 || maxRegisteredCount <= 0) {
            return 0D;
        }
        return round(HEAT_WEIGHT * registeredCount / (double) maxRegisteredCount);
    }

    public double calculateInteractionScore(long interactionCount, long maxInteractionCount) {
        if (interactionCount <= 0 || maxInteractionCount <= 0) {
            return 0D;
        }
        return round(INTERACTION_WEIGHT * interactionCount / (double) maxInteractionCount);
    }

    public double calculateTimeScore(long daysUntilStart) {
        if (daysUntilStart <= 3L) {
            return 10D;
        }
        if (daysUntilStart <= 7L) {
            return 6D;
        }
        if (daysUntilStart <= 30L) {
            return 3D;
        }
        return 1D;
    }

    private double round(double value) {
        return Math.round(value * 10D) / 10D;
    }
}
