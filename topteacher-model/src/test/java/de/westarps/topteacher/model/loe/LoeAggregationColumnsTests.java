package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class LoeAggregationColumnsTests {

	private static final LoePart PART = new LoePart(1, 1, "Klausurteil A", 0);
	private static final LoeCategory CATEGORY = new LoeCategory(2, PART.id(), "Inhalt", "", 0);
	private static final LoeTask TASK = new LoeTask(3, CATEGORY.id(), "Teilaufgabe 1", 0);
	private static final LoeRequirement REQUIREMENT = new LoeRequirement(4, TASK.id(), "Anforderung", 5, false, 0);
	private static final LoePart SECOND_PART = new LoePart(5, 1, "Klausurteil B", 1);
	private static final LoeCategory SECOND_CATEGORY = new LoeCategory(6, SECOND_PART.id(), "Sprache", "", 0);
	private static final LoeTask SECOND_TASK = new LoeTask(7, SECOND_CATEGORY.id(), "Teilaufgabe 2", 0);
	private static final LoeRequirement SECOND_REQUIREMENT =
			new LoeRequirement(8, SECOND_TASK.id(), "Zweite Anforderung", 2, false, 0);

	@Test
	void removesColumnsThatDuplicateTheWholeLevelOfExpectations() {
		final List<LoeAggregationColumns.Column> columns = LoeAggregationColumns.from(List.of(PART),
				List.of(CATEGORY), List.of(TASK), List.of(REQUIREMENT));

		assertThat(columns).isEmpty();
	}

	@Test
	void keepsUpperNodeWhenSingleChildBranchesCollapse() {
		final List<LoeAggregationColumns.Column> columns = LoeAggregationColumns.from(List.of(PART, SECOND_PART),
				List.of(CATEGORY, SECOND_CATEGORY), List.of(TASK, SECOND_TASK),
				List.of(REQUIREMENT, SECOND_REQUIREMENT));

		assertThat(columns).extracting(LoeAggregationColumns.Column::title)
				.containsExactly("Klausurteil A", "Klausurteil B");
	}
}
