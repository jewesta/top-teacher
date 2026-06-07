package de.westarps.topteacher.ui.component;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;

public class GradingScaleViewer extends AbstractDesigner {

	private final GradingScaleRepository gradingScaleRepository;
	private final Grid<GradingScaleRange> rangeGrid = new Grid<>(GradingScaleRange.class, false);

	public GradingScaleViewer(final GradingScaleRepository gradingScaleRepository) {
		super("tt-grading-scale-viewer");
		this.gradingScaleRepository = gradingScaleRepository;

		configureGrid();
	}

	public void setCourse(final Course course) {
		resetDesigner();

		if (course == null) {
			showDesignerMessage(emptyState("Bitte wählen Sie einen Kurs aus."));
			return;
		}

		final GradingScale gradingScale = gradingScaleRepository.findById(course.gradingScaleId()).orElse(null);
		if (gradingScale == null) {
			showDesignerMessage(emptyState("Für diesen Kurs ist kein Notenschlüssel hinterlegt."));
			return;
		}

		toolbar().add(title(gradingScale), meta(gradingScale));
		rangeGrid.setItems(gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id()));
		content().add(rangeGrid);
		content().expand(rangeGrid);
		showDesigner();
	}

	private void configureGrid() {
		rangeGrid.addColumn(GradingScaleRange::getPointRangeDisplayName).setHeader("Punktzahl")
				.setTextAlign(ColumnTextAlign.CENTER).setWidth("8rem").setFlexGrow(0);
		rangeGrid.addColumn(range -> range.gradeLevel().getDisplayName()).setHeader("Note").setWidth("10rem")
				.setFlexGrow(0);
		rangeGrid.addColumn(range -> range.gradeLevel().getPoints()).setHeader("Notenpunkte")
				.setTextAlign(ColumnTextAlign.END).setWidth("8rem").setFlexGrow(0);
		rangeGrid.addClassName("tt-grading-scale-grid");
		rangeGrid.setItems(List.of());
		rangeGrid.setSelectionMode(Grid.SelectionMode.NONE);
		rangeGrid.setWidth("26rem");
		rangeGrid.setHeightFull();
	}

	private static Component title(final GradingScale gradingScale) {
		final Span title = new Span(gradingScale.name());
		title.addClassName("tt-grading-scale-title");
		return title;
	}

	private static Component meta(final GradingScale gradingScale) {
		final Span meta = new Span(gradingScale.maxPoints() + " Punkte");
		meta.addClassName("tt-grading-scale-meta");
		return meta;
	}

	private static Component emptyState(final String text) {
		final Span emptyState = new Span(text);
		emptyState.addClassName("tt-empty-state");
		return emptyState;
	}
}
