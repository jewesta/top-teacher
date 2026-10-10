package de.westarps.topteacher.model.loe;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import de.westarps.validate.ValidationResult;
import de.westarps.validate.ValidationResults;

public final class LoeValidator {

	private LoeValidator() {
	}

	public static ValidationResults<LoeValidationTarget> validate(final int gradingScaleMaxPoints,
			final Collection<LoeRequirement> requirements) {
		if (gradingScaleMaxPoints < 0) {
			throw new IllegalArgumentException("gradingScaleMaxPoints must not be negative");
		}
		Objects.requireNonNull(requirements, "requirements must not be null");

		final ValidationResults.Builder<LoeValidationTarget> results = ValidationResults.builder();
		validateTotalPoints(gradingScaleMaxPoints, requirements, results);
		results.addAll(validateRequirements(requirements).getResults());
		return results.build();
	}

	public static ValidationResults<LoeValidationTarget> validate(final int gradingScaleMaxPoints,
			final Collection<LoeTask> tasks, final Collection<LoeRequirement> requirements) {
		if (gradingScaleMaxPoints < 0) {
			throw new IllegalArgumentException("gradingScaleMaxPoints must not be negative");
		}
		Objects.requireNonNull(tasks, "tasks must not be null");
		Objects.requireNonNull(requirements, "requirements must not be null");

		final ValidationResults.Builder<LoeValidationTarget> results = ValidationResults.builder();
		validateTaskTotalPoints(gradingScaleMaxPoints, tasks, results);
		results.addAll(validate(tasks, requirements).getResults());
		return results.build();
	}

	public static ValidationResults<LoeValidationTarget> validate(final Collection<LoeTask> tasks,
			final Collection<LoeRequirement> requirements) {
		Objects.requireNonNull(tasks, "tasks must not be null");
		Objects.requireNonNull(requirements, "requirements must not be null");
		final ValidationResults.Builder<LoeValidationTarget> results = ValidationResults.builder();
		validateRequirementTotalsByTask(tasks, requirements, results);
		results.addAll(validateRequirements(requirements).getResults());
		return results.build();
	}

	public static ValidationResults<LoeValidationTarget> validateRequirements(
			final Collection<LoeRequirement> requirements) {
		Objects.requireNonNull(requirements, "requirements must not be null");
		final ValidationResults.Builder<LoeValidationTarget> results = ValidationResults.builder();
		requirements.forEach(requirement -> validateRequirement(requirement, results));
		return results.build();
	}

	private static void validateTotalPoints(final int expectedPoints, final Collection<LoeRequirement> requirements,
			final ValidationResults.Builder<LoeValidationTarget> results) {
		final int assignedPoints = requirements.stream().filter(requirement -> !requirement.bonus())
				.mapToInt(LoeRequirement::maxPoints).sum();
		final int difference = expectedPoints - assignedPoints;
		if (difference > 0) {
			results.add(ValidationResult.warning(LoeValidationTarget.totalPoints(),
					"Erwartungshorizont: " + assignMorePoints(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.totalPoints(),
					"Erwartungshorizont: " + assignFewerPoints(-difference)));
		}
	}

	private static void validateTaskTotalPoints(final int expectedPoints, final Collection<LoeTask> tasks,
			final ValidationResults.Builder<LoeValidationTarget> results) {
		final int assignedPoints = tasks.stream().mapToInt(LoeTask::maxPoints).sum();
		final int difference = expectedPoints - assignedPoints;
		if (difference > 0) {
			results.add(ValidationResult.warning(LoeValidationTarget.totalPoints(),
					"Aufgaben: " + assignMorePoints(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.totalPoints(),
					"Aufgaben: " + assignFewerPoints(-difference)));
		}
	}

	private static void validateRequirementTotalsByTask(final Collection<LoeTask> tasks,
			final Collection<LoeRequirement> requirements,
			final ValidationResults.Builder<LoeValidationTarget> results) {
		final List<LoeRequirement> requirementList = List.copyOf(requirements);
		for (final LoeTask task : tasks) {
			final int assignedPoints = requirementList.stream()
					.filter(requirement -> requirement.taskId().equals(task.id()) && !requirement.bonus())
					.mapToInt(LoeRequirement::maxPoints).sum();
			final int difference = task.maxPoints() - assignedPoints;
			if (difference > 0) {
				results.add(ValidationResult.warning(LoeValidationTarget.taskPoints(task.id()),
						assignMoreRequirementPoints(difference)));
			} else if (difference < 0) {
				results.add(ValidationResult.error(LoeValidationTarget.taskPoints(task.id()),
						assignFewerRequirementPoints(-difference)));
			}
		}
	}

	private static void validateRequirement(final LoeRequirement requirement,
			final ValidationResults.Builder<LoeValidationTarget> results) {
		Objects.requireNonNull(requirement, "requirement must not be null");
		final LoeCriterionParseResult parseResult = LoeCriterionParser
				.analyze(requirement.id() == null ? 0 : requirement.id(), requirement.descriptionMarkdown());
		if (!parseResult.hasTags()) {
			return;
		}
		if (!parseResult.issues().isEmpty()) {
			parseResult.issues().forEach(issue -> results.add(ValidationResult.error(
					LoeValidationTarget.requirementCriteria(requirement.id()), criterionIssueMessage(issue))));
			return;
		}

		final int criterionPointUnits = parseResult.criteria().stream().mapToInt(LoeCriterion::pointUnits).sum();
		final int difference = LoePointUnits.fromWholePoints(requirement.maxPoints()) - criterionPointUnits;
		if (difference > 0) {
			results.add(ValidationResult.warning(LoeValidationTarget.requirementCriteria(requirement.id()),
					assignMoreCriterionPointUnits(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.requirementCriteria(requirement.id()),
					assignFewerCriterionPointUnits(-difference)));
		}
	}

	private static String criterionIssueMessage(final LoeCriterionIssue issue) {
		final String criterion = issue.label().isBlank() ? "Kriterium" : "Kriterium „" + issue.label() + "“";
		return switch (issue.kind()) {
			case INVALID_POINTS -> criterion + ": Ordne eine gültige Punktzahl zu.";
			case DUPLICATE_KEY -> criterion + ": Entferne die doppelte Markierung und markiere es erneut.";
			case INVALID_KEY -> criterion + ": Entferne die ungültige Markierung und markiere es erneut.";
			case INVALID_LABEL -> "Kriterium: Markiere einen aussagekräftigen Text.";
		};
	}

	private static String assignMorePoints(final int points) {
		return points == 1 ? "Ordne einen weiteren Punkt zu." : "Ordne weitere " + points + " Punkte zu.";
	}

	private static String assignFewerPoints(final int points) {
		return points == 1 ? "Ordne einen Punkt weniger zu." : "Ordne " + points + " Punkte weniger zu.";
	}

	private static String assignMoreRequirementPoints(final int points) {
		return points == 1 ? "Ordne Anforderungen einen weiteren Punkt zu."
				: "Ordne Anforderungen weitere " + points + " Punkte zu.";
	}

	private static String assignFewerRequirementPoints(final int points) {
		return points == 1 ? "Ordne Anforderungen einen Punkt weniger zu."
				: "Ordne Anforderungen " + points + " Punkte weniger zu.";
	}

	private static String assignMoreCriterionPointUnits(final int pointUnits) {
		return pointUnits == LoePointUnits.UNITS_PER_POINT ? "Ordne einen weiteren Kriterienpunkt zu."
				: "Ordne weitere " + LoePointUnits.formatGerman(pointUnits) + " Kriterienpunkte zu.";
	}

	private static String assignFewerCriterionPointUnits(final int pointUnits) {
		return pointUnits == LoePointUnits.UNITS_PER_POINT ? "Ordne einen Kriterienpunkt weniger zu."
				: "Ordne " + LoePointUnits.formatGerman(pointUnits) + " Kriterienpunkte weniger zu.";
	}
}
