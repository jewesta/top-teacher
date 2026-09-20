package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.westarps.validate.ValidationSeverity;

class LoeValidatorTests {

	@Test
	void acceptsMatchingRegularAndCriterionPoints() {
		final LoeRequirement requirement = requirement(1, "[Erstes](eh:1) [Zweites](eh:2)", 2, false);

		assertThat(LoeValidator.validate(2, List.of(requirement)).getTestResults()).isEmpty();
	}

	@Test
	void warnsAboutMissingRegularAndCriterionPoints() {
		final LoeRequirement requirement = requirement(1, "[Erstes](eh:1)", 2, false);

		final var results = LoeValidator.validate(4, List.of(requirement));

		assertThat(results.getTestResults()).extracting(result -> result.severity())
				.containsExactly(ValidationSeverity.WARNING, ValidationSeverity.WARNING);
		assertThat(results.messages(ValidationSeverity.WARNING)).containsExactly(
				"Erwartungshorizont: Vergib weitere 2 Punkte.", "Vergib einen weiteren Kriterienpunkt.");
		assertThat(results.getTargets()).containsExactly(LoeValidationTarget.totalPoints(),
				LoeValidationTarget.requirementCriteria(requirement.id()));
	}

	@Test
	void rejectsExcessiveRegularAndCriterionPoints() {
		final LoeRequirement requirement = requirement(1,
				"[Erstes](eh:1) [Zweites](eh:2) [Drittes](eh:3)", 2, false);

		final var results = LoeValidator.validate(1, List.of(requirement));

		assertThat(results.getTestResults()).extracting(result -> result.severity())
				.containsExactly(ValidationSeverity.ERROR, ValidationSeverity.ERROR);
		assertThat(results.messages(ValidationSeverity.ERROR)).containsExactly(
				"Erwartungshorizont: Vergib einen Punkt weniger.", "Vergib einen Kriterienpunkt weniger.");
	}

	@Test
	void excludesBonusPointsFromTheGradingScaleTotalButValidatesTheirCriteria() {
		final LoeRequirement regular = requirement(1, "[Regulär](eh:1)", 1, false);
		final LoeRequirement bonus = requirement(2, "[Bonus](eh:1)", 2, true);

		final var results = LoeValidator.validate(1, List.of(regular, bonus));

		assertThat(results.getTestResults()).singleElement().satisfies(result -> {
			assertThat(result.severity()).isEqualTo(ValidationSeverity.WARNING);
			assertThat(result.message()).isEqualTo("Vergib einen weiteren Kriterienpunkt.");
		});
		assertThat(results.getTargets()).containsExactly(LoeValidationTarget.requirementCriteria(bonus.id()));
	}

	private static LoeRequirement requirement(final int id, final String description, final int maxPoints,
			final boolean bonus) {
		return new LoeRequirement(id, 10, description, maxPoints, bonus, 0);
	}
}
