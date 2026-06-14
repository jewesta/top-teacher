package de.westarps.topteacher.ui.component;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Exam;
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

	public void setExam(final Exam exam) {
		resetDesigner();

		if (exam == null) {
			showDesignerMessage(emptyState("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		if (exam.gradingScaleId() == null) {
			showDesignerMessage(emptyState("Für diese Klausur ist kein Notenschlüssel hinterlegt."));
			return;
		}

		final GradingScale gradingScale = gradingScaleRepository.findById(exam.gradingScaleId()).orElse(null);
		if (gradingScale == null) {
			showDesignerMessage(emptyState("Für diese Klausur ist kein Notenschlüssel hinterlegt."));
			return;
		}

		toolbar().add(title(gradingScale), meta(gradingScale));
		rangeGrid.setItems(gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id()));
		content().add(rangeGrid);
		content().expand(rangeGrid);
		showDesigner();
	}

	private void configureGrid() {
		rangeGrid.addComponentColumn(GradingScaleViewer::pointRange).setHeader("Punktzahl")
				.setTextAlign(ColumnTextAlign.CENTER).setWidth("8rem").setFlexGrow(0);
		rangeGrid.addColumn(range -> range.gradeLevel().getDisplayName()).setHeader("Note").setWidth("11rem")
				.setFlexGrow(0);
		rangeGrid.addColumn(range -> range.gradeLevel().getPoints()).setHeader("Notenpunkte")
				.setTextAlign(ColumnTextAlign.END).setWidth("8rem").setFlexGrow(0);
		rangeGrid.addClassName("tt-grading-scale-grid");
		rangeGrid.setItems(List.of());
		rangeGrid.setSelectionMode(Grid.SelectionMode.NONE);
		rangeGrid.setWidth("27rem");
		rangeGrid.setHeightFull();
	}

	private static Component pointRange(final GradingScaleRange range) {
		final Span from = new Span(String.valueOf(range.minPoints()));
		from.addClassName("tt-grading-scale-range-from");
		final Span dash = new Span("-");
		dash.addClassName("tt-grading-scale-range-dash");
		final Span until = new Span(String.valueOf(range.maxPoints()));
		until.addClassName("tt-grading-scale-range-until");

		final Div pointRange = new Div(from, dash, until);
		pointRange.addClassName("tt-grading-scale-point-range");
		return pointRange;
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
