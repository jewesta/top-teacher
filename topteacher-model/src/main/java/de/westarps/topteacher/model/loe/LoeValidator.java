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
					"Erwartungshorizont: " + assignMorePoints(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.totalPoints(),
					"Erwartungshorizont: " + assignFewerPoints(-difference)));
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
					assignMoreCriterionPoints(difference)));
		} else if (difference < 0) {
			results.add(ValidationResult.error(LoeValidationTarget.requirementCriteria(requirement.id()),
					assignFewerCriterionPoints(-difference)));
		}
	}

	private static String assignMorePoints(final int points) {
		return points == 1 ? "Vergib einen weiteren Punkt." : "Vergib weitere " + points + " Punkte.";
	}

	private static String assignFewerPoints(final int points) {
		return points == 1 ? "Vergib einen Punkt weniger." : "Vergib " + points + " Punkte weniger.";
	}

	private static String assignMoreCriterionPoints(final int points) {
		return points == 1 ? "Vergib einen weiteren Kriterienpunkt."
				: "Vergib weitere " + points + " Kriterienpunkte.";
	}

	private static String assignFewerCriterionPoints(final int points) {
		return points == 1 ? "Vergib einen Kriterienpunkt weniger."
				: "Vergib " + points + " Kriterienpunkte weniger.";
	}
}
