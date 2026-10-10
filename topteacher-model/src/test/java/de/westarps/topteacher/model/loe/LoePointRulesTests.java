package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.Lifecycle;

class LoePointRulesTests {

	private static final LoePointRules RULES = new LoePointRules(
			new GradingScale(1, "Standard", 100, Lifecycle.ACTIVE));
	private static final LoeRequirement REGULAR_98 = new LoeRequirement(1, 1, "Regulär", 98, false, 0);
	private static final LoeRequirement REGULAR_2 = new LoeRequirement(2, 1, "Regulär", 2, false, 1);
	private static final LoeRequirement BONUS_4 = new LoeRequirement(3, 1, "Bonus", 4, true, 2);
	private static final LoeTask TASK_100 = new LoeTask(1, 1, "Aufgabe 1", "", 100, 0);

	@Test
	void regularPointsMustMatchTheGradingScaleMaximum() {
		final List<LoeRequirement> requirements = List.of(REGULAR_98, REGULAR_2, BONUS_4);

		assertThat(RULES.maxPoints()).isEqualTo(100);
		assertThat(RULES.regularMaxPoints(requirements)).isEqualTo(100);
		assertThat(RULES.bonusMaxPoints(requirements)).isEqualTo(4);
		assertThat(RULES.regularMaxPointsMatch(requirements)).isTrue();
		assertThat(new LoePointRules(new GradingScale(2, "Abweichend", 99, Lifecycle.ACTIVE))
				.regularMaxPointsMatch(requirements)).isFalse();
	}

	@Test
	void appliesBonusPointsOnlyUpToTheGradingScaleMaximum() {
		final List<LoeRequirement> requirements = List.of(REGULAR_98, REGULAR_2, BONUS_4);
		final Map<Integer, Integer> achievedPoints = Map.of(REGULAR_98.id(), 97, REGULAR_2.id(), 2, BONUS_4.id(), 4);

		assertThat(RULES.applicableBonusPoints(99, 4)).isEqualTo(1);
		assertThat(RULES.cappedAchievedTotal(requirements, requirement -> achievedPoints.get(requirement.id())))
				.isEqualTo(100);
	}

	@Test
	void rejectsRegularResultsAboveTheGradingScaleMaximum() {
		assertThatThrownBy(() -> RULES.applicableBonusPoints(101, 0)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("regularAchievedPoints must not exceed gradingScaleMaxPoints");
	}

	@Test
	void validatesRequirementPointsAgainstEachTask() {
		assertThat(RULES.taskMaxPoints(List.of(TASK_100))).isEqualTo(100);
		assertThat(RULES.taskMaxPointsMatch(List.of(TASK_100))).isTrue();
		assertThat(RULES.requirementMaxPointsMatchTasks(List.of(TASK_100),
				List.of(REGULAR_98, REGULAR_2, BONUS_4))).isTrue();
		assertThat(RULES.requirementMaxPointsMatchTasks(List.of(TASK_100), List.of(REGULAR_98, BONUS_4))).isFalse();
	}

	@Test
	void capsBonusPointsAtEachTaskMaximumAfterRequirementRounding() {
		final LoeTask firstTask = new LoeTask(1, 1, "Aufgabe 1", "", 60, 0);
		final LoeTask secondTask = new LoeTask(2, 1, "Aufgabe 2", "", 40, 1);
		final LoeRequirement firstRegular = new LoeRequirement(1, firstTask.id(), "Regulär", 60, false, 0);
		final LoeRequirement firstBonus = new LoeRequirement(2, firstTask.id(), "Bonus", 5, true, 1);
		final LoeRequirement secondRegular = new LoeRequirement(3, secondTask.id(), "Regulär", 40, false, 0);
		final Map<Integer, Integer> achieved = Map.of(firstRegular.id(), 58, firstBonus.id(), 5,
				secondRegular.id(), 35);

		assertThat(RULES.cappedAchievedTotal(List.of(firstTask, secondTask),
				List.of(firstRegular, firstBonus, secondRegular), requirement -> achieved.get(requirement.id())))
				.isEqualTo(95);
	}
}
