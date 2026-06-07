package de.westarps.topteacher.model.eh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;

class EhPointRulesTests {

	private static final EhPointRules RULES =
			new EhPointRules(new GradingScale(1, "Standard", 100, Lifecycle.ACTIVE));
	private static final EhRequirement REGULAR_98 = new EhRequirement(1, 1, "Regulär", 98, false, 0);
	private static final EhRequirement REGULAR_2 = new EhRequirement(2, 1, "Regulär", 2, false, 1);
	private static final EhRequirement BONUS_4 = new EhRequirement(3, 1, "Bonus", 4, true, 2);

	@Test
	void regularPointsMustMatchTheGradingScaleMaximum() {
		final List<EhRequirement> requirements = List.of(REGULAR_98, REGULAR_2, BONUS_4);

		assertThat(RULES.maxPoints()).isEqualTo(100);
		assertThat(RULES.regularMaxPoints(requirements)).isEqualTo(100);
		assertThat(RULES.bonusMaxPoints(requirements)).isEqualTo(4);
		assertThat(RULES.regularMaxPointsMatch(requirements)).isTrue();
		assertThat(new EhPointRules(new GradingScale(2, "Abweichend", 99, Lifecycle.ACTIVE))
				.regularMaxPointsMatch(requirements)).isFalse();
	}

	@Test
	void appliesBonusPointsOnlyUpToTheGradingScaleMaximum() {
		final List<EhRequirement> requirements = List.of(REGULAR_98, REGULAR_2, BONUS_4);
		final Map<Integer, Integer> achievedPoints = Map.of(
				REGULAR_98.id(), 97,
				REGULAR_2.id(), 2,
				BONUS_4.id(), 4);

		assertThat(RULES.applicableBonusPoints(99, 4)).isEqualTo(1);
		assertThat(RULES.cappedAchievedTotal(requirements,
				requirement -> achievedPoints.get(requirement.id()))).isEqualTo(100);
	}

	@Test
	void rejectsRegularResultsAboveTheGradingScaleMaximum() {
		assertThatThrownBy(() -> RULES.applicableBonusPoints(101, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("regularAchievedPoints must not exceed gradingScaleMaxPoints");
	}
}
