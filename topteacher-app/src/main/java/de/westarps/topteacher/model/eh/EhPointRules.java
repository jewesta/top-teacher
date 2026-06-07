package de.westarps.topteacher.model.eh;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

public final class EhPointRules {

	private EhPointRules() {
	}

	public static int regularMaxPoints(final List<EhRequirement> requirements) {
		return requirements(requirements).stream().filter(requirement -> !requirement.bonus())
				.mapToInt(EhRequirement::maxPoints).sum();
	}

	public static int bonusMaxPoints(final List<EhRequirement> requirements) {
		return requirements(requirements).stream().filter(EhRequirement::bonus).mapToInt(EhRequirement::maxPoints)
				.sum();
	}

	public static boolean regularMaxPointsMatch(final List<EhRequirement> requirements,
			final int gradingScaleMaxPoints) {
		validateNonNegative(gradingScaleMaxPoints, "gradingScaleMaxPoints");
		return regularMaxPoints(requirements) == gradingScaleMaxPoints;
	}

	public static int cappedAchievedTotal(final List<EhRequirement> requirements,
			final ToIntFunction<EhRequirement> achievedPoints, final int gradingScaleMaxPoints) {
		final int regularPoints = regularAchievedPoints(requirements, achievedPoints);
		final int bonusPoints = bonusAchievedPoints(requirements, achievedPoints);
		return regularPoints + applicableBonusPoints(regularPoints, bonusPoints, gradingScaleMaxPoints);
	}

	public static int applicableBonusPoints(final int regularAchievedPoints, final int bonusAchievedPoints,
			final int gradingScaleMaxPoints) {
		validateNonNegative(regularAchievedPoints, "regularAchievedPoints");
		validateNonNegative(bonusAchievedPoints, "bonusAchievedPoints");
		validateNonNegative(gradingScaleMaxPoints, "gradingScaleMaxPoints");
		if (regularAchievedPoints > gradingScaleMaxPoints) {
			throw new IllegalArgumentException("regularAchievedPoints must not exceed gradingScaleMaxPoints");
		}
		return Math.min(bonusAchievedPoints, gradingScaleMaxPoints - regularAchievedPoints);
	}

	private static int regularAchievedPoints(final List<EhRequirement> requirements,
			final ToIntFunction<EhRequirement> achievedPoints) {
		return achievedPoints(requirements, achievedPoints, false);
	}

	private static int bonusAchievedPoints(final List<EhRequirement> requirements,
			final ToIntFunction<EhRequirement> achievedPoints) {
		return achievedPoints(requirements, achievedPoints, true);
	}

	private static int achievedPoints(final List<EhRequirement> requirements,
			final ToIntFunction<EhRequirement> achievedPoints, final boolean bonus) {
		Objects.requireNonNull(achievedPoints, "achievedPoints must not be null");
		return requirements(requirements).stream().filter(requirement -> requirement.bonus() == bonus)
				.mapToInt(requirement -> achievedPoints(requirement, achievedPoints)).sum();
	}

	private static int achievedPoints(final EhRequirement requirement,
			final ToIntFunction<EhRequirement> achievedPoints) {
		final int points = achievedPoints.applyAsInt(requirement);
		validateNonNegative(points, "achievedPoints");
		return points;
	}

	private static List<EhRequirement> requirements(final List<EhRequirement> requirements) {
		return List.copyOf(Objects.requireNonNull(requirements, "requirements must not be null"));
	}

	private static void validateNonNegative(final int value, final String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must not be negative");
		}
	}
}
