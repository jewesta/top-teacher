package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.data.provider.Query;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;

class GradingScaleViewerTests {

	@Test
	void splitsRangesIntoTwoEightRowGrids() {
		final GradingScale gradingScale = new GradingScale(1, "Standard", 150, Lifecycle.ACTIVE);
		final Exam exam = new Exam(1, 1, "Klausur", LocalDate.of(2026, 6, 14), null, gradingScale.id());
		final List<GradingScaleRange> ranges = ranges(gradingScale.id());
		final GradingScaleRepository gradingScaleRepository = mock(GradingScaleRepository.class);
		when(gradingScaleRepository.findById(gradingScale.id())).thenReturn(Optional.of(gradingScale));
		when(gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id())).thenReturn(ranges);

		final GradingScaleViewer viewer = new GradingScaleViewer(gradingScaleRepository);

		viewer.setExam(exam);

		final List<Grid> grids = components(viewer, Grid.class);
		assertThat(grids).hasSize(2);
		assertThat(items(grids.get(0)).stream().map(range -> range.gradeLevel().getPoints())).containsExactly(15, 14,
				13, 12, 11, 10, 9, 8);
		assertThat(items(grids.get(1)).stream().map(range -> range.gradeLevel().getPoints())).containsExactly(7, 6, 5,
				4, 3, 2, 1, 0);
		assertThat(grids.get(0).getColumns()).hasSize(4);
		assertThat(grids.get(1).getColumns()).hasSize(4);
		assertThat(headerTokens(grids.get(0), 0)).containsExactly("Noten-", "<br>", "punkte");
		assertThat(headerTokens(grids.get(1), 0)).containsExactly("Noten-", "<br>", "punkte");
		assertThat(column(grids.get(0), 0).getWidth()).isEqualTo("7rem");
		assertThat(column(grids.get(1), 0).getWidth()).isEqualTo("7rem");
		assertThat(column(grids.get(0), 1).getHeaderText()).isEqualTo("Note");
		assertThat(column(grids.get(0), 2).getHeaderText()).isEqualTo("ab Punkte");
		assertThat(column(grids.get(0), 3).getHeaderText()).isEqualTo("bis Punkte");
	}

	private static List<GradingScaleRange> ranges(final int gradingScaleId) {
		return Stream.of(GradeLevel.values())
				.map(gradeLevel -> new GradingScaleRange(null, gradingScaleId, gradeLevel,
						gradeLevel == GradeLevel.UNGENUEGEND ? 0 : gradeLevel.getPoints() * 10,
						gradeLevel == GradeLevel.SEHR_GUT_PLUS ? 150 : gradeLevel.getPoints() * 10 + 9))
				.toList();
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static List<GradingScaleRange> items(final Grid grid) {
		return grid.getDataProvider().fetch(new Query()).toList();
	}

	private static Grid.Column<?> column(final Grid grid, final int index) {
		return (Grid.Column<?>) grid.getColumns().get(index);
	}

	private static List<String> headerTokens(final Grid grid, final int columnIndex) {
		return column(grid, columnIndex).getHeaderComponent().getChildren().map(GradingScaleViewerTests::headerToken)
				.toList();
	}

	private static String headerToken(final Component component) {
		if (component instanceof Text text) {
			return text.getText();
		}
		if (component instanceof Html) {
			return "<" + component.getElement().getTag() + ">";
		}
		return component.getElement().getText();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
