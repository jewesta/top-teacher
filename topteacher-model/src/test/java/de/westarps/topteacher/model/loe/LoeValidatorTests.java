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
				"Im Erwartungshorizont sind 2 von 4 regulären Punkten vergeben. Es fehlen 2 Punkte.",
				"Es sind 1 von 2 Kriterienpunkten vergeben. Es fehlt 1 Kriterienpunkt.");
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
				"Im Erwartungshorizont sind 2 von 1 regulären Punkten vergeben. 1 Punkt ist zu viel.",
				"Es sind 3 von 2 Kriterienpunkten vergeben. 1 Kriterienpunkt ist zu viel.");
	}

	@Test
	void excludesBonusPointsFromTheGradingScaleTotalButValidatesTheirCriteria() {
		final LoeRequirement regular = requirement(1, "[Regulär](eh:1)", 1, false);
		final LoeRequirement bonus = requirement(2, "[Bonus](eh:1)", 2, true);

		final var results = LoeValidator.validate(1, List.of(regular, bonus));

		assertThat(results.getTestResults()).singleElement().satisfies(result -> {
			assertThat(result.severity()).isEqualTo(ValidationSeverity.WARNING);
			assertThat(result.message()).contains("1 von 2 Kriterienpunkten", "1 Kriterienpunkt");
		});
		assertThat(results.getTargets()).containsExactly(LoeValidationTarget.requirementCriteria(bonus.id()));
	}

	private static LoeRequirement requirement(final int id, final String description, final int maxPoints,
			final boolean bonus) {
		return new LoeRequirement(id, 10, description, maxPoints, bonus, 0);
	}
}
