package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.vaadin.markdown.MarkdownEditor;
import de.westarps.vaadin.markdown.MarkdownViewer;

class ExamMaterialEditorTests {

	private static final Exam EXAM = new Exam(1, 10, "Klausur", LocalDate.of(2026, 9, 1));
	private static final LoePart PART = new LoePart(1, EXAM.id(), "Klausurteil A", 0);
	private static final LoeMaterial MATERIAL = new LoeMaterial(2, PART.id(), "Quelle 1", "Ein Quellentext", 0);
	private UI ui;

	@BeforeEach
	void createUi() {
		ui = new UI();
		UI.setCurrent(ui);
	}

	@AfterEach
	void clearUi() {
		UI.setCurrent(null);
		ui = null;
	}

	@Test
	void confirmsDiscardingMaterialChangesAndPreservesThemOnCancel() {
		final LevelOfExpectationsRepository repository = repository(false);
		final ExamMaterialEditor editor = new ExamMaterialEditor(repository);
		editor.setExam(EXAM);
		final TextField title = components(editor, TextField.class).getFirst();
		title.setValue("Geänderte Quelle");
		final Button discard = buttonsByAriaLabel(editor, "Verwerfen...").getFirst();
		assertThat(discard.getTooltip().getText()).isEqualTo("Verwerfen...");

		discard.click();

		final ConfirmDialog confirmation = components(editor, ConfirmDialog.class).getFirst();
		assertThat(confirmation.isOpened()).isTrue();
		assertThat(title.getValue()).isEqualTo("Geänderte Quelle");
		confirmation.close();
		assertThat(editor.hasUnsavedChanges()).isTrue();
		discard.click();
		ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

		assertThat(confirmation.isOpened()).isFalse();
		assertThat(editor.hasUnsavedChanges()).isFalse();
		assertThat(components(editor, TextField.class).getFirst().getValue()).isEqualTo(MATERIAL.title());
		verify(repository, never()).saveMaterialWithImages(any(), any());
	}

	@Test
	void savesMaterialWithoutRebuildingTheTree() {
		final LevelOfExpectationsRepository repository = repository(false);
		final ExamMaterialEditor editor = new ExamMaterialEditor(repository);
		editor.setExam(EXAM);
		final Details partDetails = components(editor, Details.class).getFirst();
		clearInvocations(repository);

		components(editor, TextField.class).getFirst().setValue("Quelle A");
		components(editor, MarkdownEditor.class).getFirst().setValue("Ein **wichtiger** Quellentext");
		buttonsByAriaLabel(editor, "Speichern").getFirst().click();

		verify(repository).saveMaterialWithImages(new LoeMaterial(MATERIAL.id(), MATERIAL.partId(), "Quelle A",
				"Ein **wichtiger** Quellentext", MATERIAL.sortOrder()), List.of());
		verify(repository, never()).findPartsByExamId(anyInt());
		verify(repository, never()).findMaterialsByExamId(anyInt());
		assertThat(components(editor, Details.class).getFirst()).isSameAs(partDetails);
		assertThat(buttonsByAriaLabel(editor, "Speichern").getFirst().isEnabled()).isFalse();
	}

	@Test
	void locksEveryMaterialFieldAndActionAfterFirstResult() {
		final ExamMaterialEditor editor = new ExamMaterialEditor(repository(true));

		editor.setExam(EXAM);

		assertThat(components(editor, TextField.class)).allMatch(TextField::isReadOnly);
		assertThat(components(editor, MarkdownEditor.class)).isEmpty();
		assertThat(components(editor, MarkdownViewer.class)).extracting(MarkdownViewer::getContent)
				.containsExactly(MATERIAL.descriptionMarkdown());
		assertThat(button(editor, "Material hinzufügen").isEnabled()).isFalse();
		assertThat(buttonsByAriaLabel(editor, "Löschen...")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Nach oben")).extracting(Button::isEnabled).containsOnly(false);
		assertThat(buttonsByAriaLabel(editor, "Nach unten")).extracting(Button::isEnabled).containsOnly(false);
	}

	@Test
	void placesSharedSelfAndSubtreeTogglesInSectionHeaders() {
		final ExamMaterialEditor editor = new ExamMaterialEditor(repository(false));
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		final List<Button> sectionToggles = details.stream()
				.map(detail -> buttonsByDataAction(detail.getSummary(), "toggle-section").getFirst()).toList();
		final List<Button> subtreeToggles = details.stream()
				.flatMap(detail -> buttonsByDataAction(detail.getSummary(), "toggle-subtree").stream()).toList();

		assertThat(details).hasSize(2);
		assertThat(sectionToggles).hasSize(2);
		assertThat(subtreeToggles).hasSize(1);
		assertThat(buttonsByDataAction(editor, "toggle-all")).hasSize(1);
		for (int index = 0; index < details.size(); index++) {
			assertThat(sectionToggles.get(index).getParent().orElseThrow()).isSameAs(details.get(index).getSummary());
		}
		assertThat(subtreeToggles.getFirst().getParent().orElseThrow()).isSameAs(details.getFirst().getSummary());

		subtreeToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsOnly(false);
		subtreeToggles.getFirst().click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);

		sectionToggles.getLast().click();
		assertThat(details).extracting(Details::isOpened).containsExactly(true, false);
	}

	@Test
	void groupTogglesExpandMixedStateBeforeTheyCollapseEverything() {
		final ExamMaterialEditor editor = new ExamMaterialEditor(repository(false));
		editor.setExam(EXAM);
		final List<Details> details = components(editor, Details.class);
		final Button materialToggle = buttonsByDataAction(details.getLast().getSummary(), "toggle-section").getFirst();
		final Button subtreeToggle = buttonsByDataAction(details.getFirst().getSummary(), "toggle-subtree").getFirst();
		final Button allToggle = buttonsByDataAction(editor, "toggle-all").getFirst();

		materialToggle.click();
		assertThat(subtreeToggle.getElement().getAttribute("aria-label"))
				.isEqualTo("Klausurteil „Klausurteil A“ samt Unterpunkten ausklappen");
		assertThat(allToggle.getElement().getAttribute("aria-label")).isEqualTo("Alle Abschnitte ausklappen");

		subtreeToggle.click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);
		materialToggle.click();
		allToggle.click();
		assertThat(details).extracting(Details::isOpened).containsOnly(true);
	}

	private static LevelOfExpectationsRepository repository(final boolean locked) {
		final LevelOfExpectationsRepository repository = mock(LevelOfExpectationsRepository.class);
		when(repository.hasResultsForExam(EXAM.id())).thenReturn(locked);
		when(repository.findPartsByExamId(EXAM.id())).thenReturn(List.of(PART));
		when(repository.findMaterialsByExamId(EXAM.id())).thenReturn(List.of(MATERIAL));
		when(repository.findMaterialImagesByIds(any())).thenReturn(List.of());
		when(repository.saveMaterialWithImages(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
		return repository;
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static List<Button> buttonsByAriaLabel(final Component root, final String label) {
		return components(root, Button.class).stream()
				.filter(button -> label.equals(button.getElement().getAttribute("aria-label"))).toList();
	}

	private static List<Button> buttonsByDataAction(final Component root, final String action) {
		return components(root, Button.class).stream()
				.filter(button -> action.equals(button.getElement().getAttribute("data-action"))).toList();
	}
}

