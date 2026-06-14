package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.ExamNoteSection;

class ExamNotesEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final ExamNoteSection NOTE_SECTION = new ExamNoteSection(2, EXAM.id(), "Notiz", "Text", 0);

	@Test
	void deleteNoteSectionAsksForConfirmationAndUsesNeutralIconStyle() {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.findNoteSectionsByExamId(EXAM.id())).thenReturn(List.of(NOTE_SECTION));
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			final ExamNotesEditor editor = new ExamNotesEditor(repository);
			ui.add(editor);
			editor.setExam(EXAM);
			clearInvocations(repository);
			final Button deleteButton = buttonsByAriaLabel(editor, "Löschen").getFirst();

			assertThat(deleteButton.getThemeNames()).doesNotContain(ButtonVariant.LUMO_ERROR.getVariantName());

			deleteButton.click();

			verify(repository, never()).deleteNoteSection(NOTE_SECTION.id());
			final ConfirmDialog confirmation = components(ui, ConfirmDialog.class).getFirst();
			assertThat(confirmation.isOpened()).isTrue();

			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			verify(repository).deleteNoteSection(NOTE_SECTION.id());
		} finally {
			UI.setCurrent(null);
		}
	}

	private static List<Button> buttonsByAriaLabel(final Component root, final String ariaLabel) {
		return components(root, Button.class).stream()
				.filter(button -> ariaLabel.equals(button.getElement().getAttribute("aria-label"))).toList();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
