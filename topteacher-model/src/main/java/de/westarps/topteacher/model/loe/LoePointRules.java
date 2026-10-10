package de.westarps.topteacher.model.loe;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import de.westarps.topteacher.model.GradingScale;

public final class LoePointRules {

	private final GradingScale gradingScale;

	public LoePointRules(final GradingScale gradingScale) {
		this.gradingScale = Objects.requireNonNull(gradingScale, "gradingScale must not be null");
	}

	public int maxPoints() {
		return gradingScale.maxPoints();
	}

	public int regularMaxPoints(final List<LoeRequirement> requirements) {
		return requirements(requirements).stream().filter(requirement -> !requirement.bonus())
				.mapToInt(LoeRequirement::maxPoints).sum();
	}

	public int bonusMaxPoints(final List<LoeRequirement> requirements) {
		return requirements(requirements).stream().filter(LoeRequirement::bonus).mapToInt(LoeRequirement::maxPoints)
				.sum();
	}

	public boolean regularMaxPointsMatch(final List<LoeRequirement> requirements) {
		return regularMaxPoints(requirements) == gradingScale.maxPoints();
	}

	public int taskMaxPoints(final List<LoeTask> tasks) {
		return tasks(tasks).stream().mapToInt(LoeTask::maxPoints).sum();
	}

	public boolean taskMaxPointsMatch(final List<LoeTask> tasks) {
		return taskMaxPoints(tasks) == gradingScale.maxPoints();
	}

	public boolean requirementMaxPointsMatchTasks(final List<LoeTask> tasks,
			final List<LoeRequirement> requirements) {
		final Map<Integer, Integer> regularPointsByTask = requirements(requirements).stream()
				.filter(requirement -> !requirement.bonus()).collect(Collectors.groupingBy(LoeRequirement::taskId,
						Collectors.summingInt(LoeRequirement::maxPoints)));
		return tasks(tasks).stream()
				.allMatch(task -> regularPointsByTask.getOrDefault(task.id(), 0) == task.maxPoints());
	}

	public boolean validDesign(final List<LoeTask> tasks, final List<LoeRequirement> requirements) {
		return taskMaxPointsMatch(tasks) && requirementMaxPointsMatchTasks(tasks, requirements);
	}

	/**
	 * Caps each task independently after the supplied function has rounded each
	 * requirement result to whole points.
	 */
	public int cappedAchievedTotal(final List<LoeTask> tasks, final List<LoeRequirement> requirements,
			final ToIntFunction<LoeRequirement> achievedPoints) {
		Objects.requireNonNull(achievedPoints, "achievedPoints must not be null");
		final Map<Integer, List<LoeRequirement>> requirementsByTask = requirements(requirements).stream()
				.collect(Collectors.groupingBy(LoeRequirement::taskId));
		return tasks(tasks).stream().mapToInt(task -> Math.min(task.maxPoints(),
				requirementsByTask.getOrDefault(task.id(), List.of()).stream()
						.mapToInt(requirement -> achievedPoints(requirement, achievedPoints)).sum()))
				.sum();
	}

	public int cappedAchievedTotal(final List<LoeRequirement> requirements,
			final ToIntFunction<LoeRequirement> achievedPoints) {
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

	private int regularAchievedPoints(final List<LoeRequirement> requirements,
			final ToIntFunction<LoeRequirement> achievedPoints) {
		return achievedPoints(requirements, achievedPoints, false);
	}

	private int bonusAchievedPoints(final List<LoeRequirement> requirements,
			final ToIntFunction<LoeRequirement> achievedPoints) {
		return achievedPoints(requirements, achievedPoints, true);
	}

	private static int achievedPoints(final List<LoeRequirement> requirements,
			final ToIntFunction<LoeRequirement> achievedPoints, final boolean bonus) {
		Objects.requireNonNull(achievedPoints, "achievedPoints must not be null");
		return requirements(requirements).stream().filter(requirement -> requirement.bonus() == bonus)
				.mapToInt(requirement -> achievedPoints(requirement, achievedPoints)).sum();
	}

	private static int achievedPoints(final LoeRequirement requirement,
			final ToIntFunction<LoeRequirement> achievedPoints) {
		final int points = achievedPoints.applyAsInt(requirement);
		validateNonNegative(points, "achievedPoints");
		return points;
	}

	private static List<LoeRequirement> requirements(final List<LoeRequirement> requirements) {
		return List.copyOf(Objects.requireNonNull(requirements, "requirements must not be null"));
	}

	private static List<LoeTask> tasks(final List<LoeTask> tasks) {
		return List.copyOf(Objects.requireNonNull(tasks, "tasks must not be null"));
	}

	private static void validateNonNegative(final int value, final String name) {
		if (value < 0) {
			throw new IllegalArgumentException(name + " must not be negative");
		}
	}
}
