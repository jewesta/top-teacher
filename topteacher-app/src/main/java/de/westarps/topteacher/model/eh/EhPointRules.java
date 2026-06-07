package de.westarps.topteacher.model.eh;

import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

import de.westarps.topteacher.model.GradingScale;

public final class EhPointRules {

	private final GradingScale gradingScale;

	public EhPointRules(final GradingScale gradingScale) {
		this.gradingScale = Objects.requireNonNull(gradingScale, "gradingScale must not be null");
	}

	public int maxPoints() {
		return gradingScale.maxPoints();
	}

	public int regularMaxPoints(final List<EhRequirement> requirements) {
		return requirements(requirements).stream().filter(requirement -> !requirement.bonus())
				.mapToInt(EhRequirement::maxPoints).sum();
	}

	public int bonusMaxPoints(final List<EhRequirement> requirements) {
		return requirements(requirements).stream().filter(EhRequirement::bonus).mapToInt(EhRequirement::maxPoints)
				.sum();
	}

	public boolean regularMaxPointsMatch(final List<EhRequirement> requirements) {
		return regularMaxPoints(requirements) == gradingScale.maxPoints();
	}

	public int cappedAchievedTotal(final List<EhRequirement> requirements,
			final ToIntFunction<EhRequirement> achievedPoints) {
		final int regularPoints = regularAchievedPoints(requirements, achievedPoints);
		final int bonusPoints = bonusAchievedPoints(requirements, achievedPoints);
		return regularPoints + applicableBonusPoints(regularPoints, bonusPoints);
	}

	public int applicableBonusPoints(final int regularAchievedPoints, final int bonusAchievedPoints) {
		validateNonNegative(regularAchievedPoints, "regularAchievedPoints");
		validateNonNegative(bonusAchievedPoints, "bonusAchievedPoints");
		if (regularAchievedPoints > gradingScale.maxPoints()) {
			throw new IllegalArgumentException("regularAchievedPoints must not exceed gradingScaleMaxPoints");
		}
		return Math.min(bonusAchievedPoints, gradingScale.maxPoints() - regularAchievedPoints);
	}

	private int regularAchievedPoints(final List<EhRequirement> requirements,
			final ToIntFunction<EhRequirement> achievedPoints) {
		return achievedPoints(requirements, achievedPoints, false);
	}

	private int bonusAchievedPoints(final List<EhRequirement> requirements,
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
