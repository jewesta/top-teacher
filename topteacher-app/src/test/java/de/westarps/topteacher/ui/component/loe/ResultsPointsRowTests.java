package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;

class ResultsPointsRowTests {

	@Test
	void checkboxOccupiesItsOwnColumnWithoutChangingTheStepper() {
		final Checkbox checkbox = new Checkbox();
		checkbox.setAriaLabel("Kriterium: volle Punktzahl");
		final LoePointStepper stepper = new LoePointStepper("Kriterium Punkte");
		stepper.setMaximumPointUnits(4);
		stepper.setPointUnits(1);
		final ResultsPointsRow row = new ResultsPointsRow(checkbox, stepper);

		final List<Component> columns = row.getChildren().toList();
		assertThat(columns).hasSize(2);
		assertThat(columns.getFirst().getClassNames()).contains("tt-results-points-status");
		assertThat(columns.getFirst().getChildren()).containsExactly(checkbox);
		assertThat(columns.getFirst().getElement().getAttribute("aria-hidden")).isNull();
		assertThat(columns.getLast()).isSameAs(stepper);
		assertThat(stepper.getPointUnits()).isEqualTo(1);
		assertThat(stepper.getMaximumPointUnits()).isEqualTo(4);
		assertThat(checkbox.getAriaLabel()).contains("Kriterium: volle Punktzahl");
	}

	@Test
	void pointOnlyRowsReserveAnEmptyStatusColumnWithoutAddingAnIcon() {
		for (final Component points : List.of(new ResultsRequirementPointCell(), new LoePointStepper("Freie Punkte"))) {
			final ResultsPointsRow row = new ResultsPointsRow(points);
			final List<Component> columns = row.getChildren().toList();

			assertThat(columns).hasSize(2);
			assertThat(columns.getFirst().getChildren()).isEmpty();
			assertThat(columns.getFirst().getElement().getAttribute("aria-hidden")).isEqualTo("true");
			assertThat(columns.getLast()).isSameAs(points);
		}
	}
}
