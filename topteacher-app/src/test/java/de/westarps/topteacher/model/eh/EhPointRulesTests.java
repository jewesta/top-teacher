package de.westarps.topteacher.model.eh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EhPointRulesTests {

	private static final EhRequirement REGULAR_98 = new EhRequirement(1, 1, "Regulär", 98, false, 0);
	private static final EhRequirement REGULAR_2 = new EhRequirement(2, 1, "Regulär", 2, false, 1);
	private static final EhRequirement BONUS_4 = new EhRequirement(3, 1, "Bonus", 4, true, 2);

	@Test
	void regularPointsMustMatchTheGradingScaleMaximum() {
		final List<EhRequirement> requirements = List.of(REGULAR_98, REGULAR_2, BONUS_4);

		assertThat(EhPointRules.regularMaxPoints(requirements)).isEqualTo(100);
		assertThat(EhPointRules.bonusMaxPoints(requirements)).isEqualTo(4);
		assertThat(EhPointRules.regularMaxPointsMatch(requirements, 100)).isTrue();
		assertThat(EhPointRules.regularMaxPointsMatch(requirements, 99)).isFalse();
	}

	@Test
	void appliesBonusPointsOnlyUpToTheGradingScaleMaximum() {
		final List<EhRequirement> requirements = List.of(REGULAR_98, REGULAR_2, BONUS_4);
		final Map<Integer, Integer> achievedPoints = Map.of(
				REGULAR_98.id(), 97,
				REGULAR_2.id(), 2,
				BONUS_4.id(), 4);

		assertThat(EhPointRules.applicableBonusPoints(99, 4, 100)).isEqualTo(1);
		assertThat(EhPointRules.cappedAchievedTotal(requirements,
				requirement -> achievedPoints.get(requirement.id()), 100)).isEqualTo(100);
	}

	@Test
	void rejectsRegularResultsAboveTheGradingScaleMaximum() {
		assertThatThrownBy(() -> EhPointRules.applicableBonusPoints(101, 0, 100))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("regularAchievedPoints must not exceed gradingScaleMaxPoints");
	}
}
