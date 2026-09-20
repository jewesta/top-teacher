package de.westarps.topteacher.model.loe;

import java.util.Collection;
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
					"Im Erwartungshorizont sind " + assignedPoints + " von " + expectedPoints
							+ " regulären Punkten vergeben. " + missingPoints(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.totalPoints(),
					"Im Erwartungshorizont sind " + assignedPoints + " von " + expectedPoints
							+ " regulären Punkten vergeben. " + excessivePoints(-difference)));
		}
	}

	private static void validateRequirement(final LoeRequirement requirement,
			final ValidationResults.Builder<LoeValidationTarget> results) {
		Objects.requireNonNull(requirement, "requirement must not be null");
		final int criterionPoints = LoeCriterionParser
				.parse(requirement.id() == null ? 0 : requirement.id(), requirement.descriptionMarkdown()).size();
		final int difference = requirement.maxPoints() - criterionPoints;
		if (difference > 0) {
			results.add(ValidationResult.warning(LoeValidationTarget.requirementCriteria(requirement.id()),
					"Es sind " + criterionPoints + " von " + requirement.maxPoints()
							+ " Kriterienpunkten vergeben. " + missingCriterionPoints(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.requirementCriteria(requirement.id()),
					"Es sind " + criterionPoints + " von " + requirement.maxPoints()
							+ " Kriterienpunkten vergeben. " + excessiveCriterionPoints(-difference)));
		}
	}

	private static String missingPoints(final int points) {
		return points == 1 ? "Es fehlt 1 Punkt." : "Es fehlen " + points + " Punkte.";
	}

	private static String excessivePoints(final int points) {
		return points == 1 ? "1 Punkt ist zu viel." : points + " Punkte sind zu viel.";
	}

	private static String missingCriterionPoints(final int points) {
		return points == 1 ? "Es fehlt 1 Kriterienpunkt." : "Es fehlen " + points + " Kriterienpunkte.";
	}

	private static String excessiveCriterionPoints(final int points) {
		return points == 1 ? "1 Kriterienpunkt ist zu viel." : points + " Kriterienpunkte sind zu viel.";
	}
}
