package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SpreadsheetGridTests {

	@Test
	void addsSpreadsheetClassAndTiltedHeaderPartName() {
		final SpreadsheetGrid<String> grid = new SpreadsheetGrid<>();

		final var column = grid.addSpreadsheetColumn(item -> item, "Sehr langer Spaltenkopf");

		assertThat(grid.hasClassName("tt-spreadsheet-grid")).isTrue();
		assertThat(column.getHeaderPartName()).isEqualTo("tt-spreadsheet-tilted-header-cell");
	}

	@Test
	void addsSpacerColumn() {
		final SpreadsheetGrid<String> grid = new SpreadsheetGrid<>();

		final var spacerColumn = grid.addSpacerColumn("5.5rem");

		assertThat(grid.getColumns()).containsExactly(spacerColumn);
		assertThat(spacerColumn.getHeaderPartName()).isEqualTo("tt-spreadsheet-tilted-header-cell");
		assertThat(spacerColumn.getWidth()).isEqualTo("5.5rem");
		assertThat(spacerColumn.getFlexGrow()).isZero();
	}

	@Test
	void rejectsHeaderLabelMaxLengthWithoutRoomForSuffix() {
		final SpreadsheetGrid<String> grid = new SpreadsheetGrid<>();

		assertThatThrownBy(() -> grid.setHeaderLabelMaxLength(3)).isInstanceOf(IllegalArgumentException.class);
	}
}
