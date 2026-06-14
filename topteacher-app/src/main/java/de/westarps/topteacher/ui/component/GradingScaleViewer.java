package de.westarps.topteacher.ui.component;

import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;

public class GradingScaleViewer extends AbstractDesigner {

	private final GradingScaleRepository gradingScaleRepository;
	private final GradingScaleRangeGridGroup<GradingScaleRange> rangeGrids = GradingScaleRangeGridGroup.passive(
			GradingScaleRange.class, GradingScaleRange::gradeLevel, GradingScaleRange::minPoints,
			GradingScaleRange::maxPoints);

	public GradingScaleViewer(final GradingScaleRepository gradingScaleRepository) {
		super("tt-grading-scale-viewer");
		this.gradingScaleRepository = gradingScaleRepository;
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
		setRangeGridItems(gradingScaleRepository.findRangesByGradingScaleId(gradingScale.id()));
		content().add(rangeGrids);
		showDesigner();
	}

	private void setRangeGridItems(final List<GradingScaleRange> ranges) {
		rangeGrids.setItems(ranges);
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
