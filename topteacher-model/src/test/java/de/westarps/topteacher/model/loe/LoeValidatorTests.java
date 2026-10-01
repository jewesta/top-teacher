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
				"Erwartungshorizont: Ordne weitere 2 Punkte zu.", "Ordne einen weiteren Kriterienpunkt zu.");
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
				"Erwartungshorizont: Ordne einen Punkt weniger zu.", "Ordne einen Kriterienpunkt weniger zu.");
	}

	@Test
	void excludesBonusPointsFromTheGradingScaleTotalButValidatesTheirCriteria() {
		final LoeRequirement regular = requirement(1, "[Regulär](eh:1)", 1, false);
		final LoeRequirement bonus = requirement(2, "[Bonus](eh:1)", 2, true);

		final var results = LoeValidator.validate(1, List.of(regular, bonus));

		assertThat(results.getTestResults()).singleElement().satisfies(result -> {
			assertThat(result.severity()).isEqualTo(ValidationSeverity.WARNING);
			assertThat(result.message()).isEqualTo("Ordne einen weiteren Kriterienpunkt zu.");
		});
		assertThat(results.getTargets()).containsExactly(LoeValidationTarget.requirementCriteria(bonus.id()));
	}

	@Test
	void acceptsCriterionFreeRequirements() {
		final LoeRequirement requirement = requirement(1, "Ganzheitlich bewerten.", 8, false);

		assertThat(LoeValidator.validateRequirements(List.of(requirement)).getTestResults()).isEmpty();
	}

	@Test
	void acceptsHalfPointCriteriaWhoseUnitsMatchTheRequirement() {
		final LoeRequirement requirement = requirement(1, "[Halb](eh:1/0,5) [Anderthalb](eh:2/1.5)", 2,
				false);

		assertThat(LoeValidator.validateRequirements(List.of(requirement)).getTestResults()).isEmpty();
	}

	@Test
	void reportsHalfPointAllocationDifferences() {
		final LoeRequirement requirement = requirement(1, "[Anderthalb](eh:1/1,5)", 2, false);

		assertThat(LoeValidator.validateRequirements(List.of(requirement)).messages(ValidationSeverity.WARNING))
				.containsExactly("Ordne weitere 0,5 Kriterienpunkte zu.");
	}

	@Test
	void rejectsMalformedPointPlaceholdersWithoutTreatingTheRequirementAsCriterionFree() {
		final LoeRequirement requirement = requirement(1, "[Begründung](eh:1/?)", 2, false);

		assertThat(LoeValidator.validateRequirements(List.of(requirement)).messages(ValidationSeverity.ERROR))
				.containsExactly("Kriterium „Begründung“: Ordne eine gültige Punktzahl zu.");
	}

	private static LoeRequirement requirement(final int id, final String description, final int maxPoints,
			final boolean bonus) {
		return new LoeRequirement(id, 10, description, maxPoints, bonus, 0);
	}
}
