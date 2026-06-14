package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.ExamNoteSection;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.vaadin.markdown.MarkdownEditor;

public class ExamNotesEditor extends AbstractDesigner {

	private final LevelOfExpectationsRepository levelOfExpectationsRepository;
	private final Set<String> collapsedDetails = new HashSet<>();
	private final Button saveButton = Buttons.save();

	private Exam exam;
	private List<ExamNoteSection> noteSections = List.of();
	private List<NoteSectionEditor> noteSectionEditors = List.of();

	public ExamNotesEditor(final LevelOfExpectationsRepository levelOfExpectationsRepository) {
		super("tt-exam-notes-editor");
		this.levelOfExpectationsRepository = levelOfExpectationsRepository;

		saveButton.addThemeVariants(ButtonVariant.LUMO_SMALL);
		saveButton.addClickListener(event -> saveDirtySections());
		saveButton.setEnabled(false);
	}

	public void setExam(final Exam exam) {
		if (this.exam == null || exam == null || !this.exam.id().equals(exam.id())) {
			collapsedDetails.clear();
		}
		this.exam = exam;
		refresh();
	}

	private void refresh() {
		resetDesigner();
		noteSectionEditors = List.of();
		if (exam == null) {
			showDesignerMessage(new Span("Bitte wähle eine Klausur aus."));
			return;
		}

		noteSections = levelOfExpectationsRepository.findNoteSectionsByExamId(exam.id());

		if (noteSections.isEmpty()) {
			content().add(emptyState("Noch keine Notizen angelegt."));
		} else {
			noteSectionEditors = noteSections.stream().map(this::createNoteSectionEditor).toList();
			noteSectionEditors.forEach(noteSectionEditor -> content().add(noteSectionEditor.details()));
		}

		configureToolbar();
		showDesigner();
		updateSaveButton();
	}

	private void configureToolbar() {
		final Button addSection = commandButton("Abschnitt hinzufügen", VaadinIcon.PLUS, event -> {
			levelOfExpectationsRepository.saveNoteSection(new ExamNoteSection(null, exam.id(), "Neuer Abschnitt", "",
					levelOfExpectationsRepository.nextNoteSectionSortOrder(exam.id())));
			refresh();
		});
		addSection.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		toolbar().add(saveButton, addSection);
	}

	private NoteSectionEditor createNoteSectionEditor(final ExamNoteSection noteSection) {
		final TextField title = new TextField();
		title.addClassName("tt-eh-summary-title-field");
		title.setValue(noteSection.title());
		title.setWidthFull();
		title.getElement().setAttribute("aria-label", "Titel");
		title.addAttachListener(event -> title.getElement().executeJs("""
				this.addEventListener('click', event => event.stopPropagation());
				this.addEventListener('keydown', event => event.stopPropagation());
				"""));
		title.addValueChangeListener(event -> {
			title.setInvalid(false);
			if (event.isFromClient()) {
				updateSaveButton();
			}
		});

		final MarkdownEditor description = new MarkdownEditor(noteSection.descriptionMarkdown());
		description.addClassName("tt-markdown-editor");
		description.setPlaceholder("Beschreibung");
		description.setWidthFull();
		description.setHeight("18rem");
		description.addValueChangeListener(event -> updateSaveButton());

		final VerticalLayout markdownBlock = new VerticalLayout(description);
		markdownBlock.addClassName("tt-markdown-block");
		markdownBlock.setPadding(false);
		markdownBlock.setWidthFull();

		final HorizontalLayout actions = new HorizontalLayout(moveButton("Nach oben", -1, noteSection),
				moveButton("Nach unten", 1, noteSection), deleteButton(() -> {
					levelOfExpectationsRepository.deleteNoteSection(noteSection.id());
					refresh();
				}));
		actions.addClassName("tt-eh-actions");
		actions.setPadding(false);

		final VerticalLayout content = new VerticalLayout(markdownBlock, actions);
		content.addClassName("tt-eh-section-content");
		content.setPadding(false);
		content.setWidthFull();

		final Details details = new Details(summary(title), content);
		details.addClassNames("tt-eh-details", "tt-exam-note-section");
		configureOpenedState(details, detailKey("note", noteSection.id()));
		return new NoteSectionEditor(noteSection, title, description, details);
	}

	private Component summary(final Component title) {
		final Span typeLabel = new Span("Notiz");
		typeLabel.addClassName("tt-eh-summary-type");

		final HorizontalLayout summary = new HorizontalLayout(typeLabel, title);
		summary.addClassName("tt-eh-summary");
		summary.setAlignItems(Alignment.CENTER);
		summary.setPadding(false);
		summary.setWidthFull();
		return summary;
	}

	private Button moveButton(final String label, final int offset, final ExamNoteSection noteSection) {
		final Button button = iconButton(label, offset < 0 ? VaadinIcon.ARROW_UP : VaadinIcon.ARROW_DOWN, event -> {
			levelOfExpectationsRepository.moveNoteSection(noteSection, offset);
			refresh();
		});
		final int index = noteSections.indexOf(noteSection);
		button.setEnabled(index >= 0 && index + offset >= 0 && index + offset < noteSections.size());
		return button;
	}

	private Button deleteButton(final Runnable deleteAction) {
		final Button button = Buttons.deleteIcon("Löschen", "Notiz löschen?", deleteAction);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		return button;
	}

	private Button commandButton(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = Buttons.command(text, icon, listener);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL);
		return button;
	}

	private Button iconButton(final String label, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(icon.create(), listener);
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		return button;
	}

	private Component emptyState(final String text) {
		final Span emptyState = new Span(text);
		emptyState.addClassName("tt-empty-state");
		return emptyState;
	}

	private String value(final MarkdownEditor editor) {
		return editor.getValue() == null ? "" : editor.getValue();
	}

	private void updateSaveButton() {
		saveButton.setEnabled(noteSectionEditors.stream().anyMatch(NoteSectionEditor::isDirty));
	}

	private void saveDirtySections() {
		for (final NoteSectionEditor noteSectionEditor : new ArrayList<>(noteSectionEditors)) {
			if (!noteSectionEditor.save()) {
				return;
			}
		}
		updateSaveButton();
	}

	private void configureOpenedState(final Details details, final String key) {
		details.setOpened(!collapsedDetails.contains(key));
		details.addOpenedChangeListener(event -> {
			if (event.isOpened()) {
				collapsedDetails.remove(key);
			} else {
				collapsedDetails.add(key);
			}
		});
	}

	private String detailKey(final String type, final Integer id) {
		return type + ":" + id;
	}

	private void replaceNoteSection(final ExamNoteSection noteSection) {
		noteSections = noteSections.stream()
				.map(currentNoteSection -> currentNoteSection.id().equals(noteSection.id()) ? noteSection
						: currentNoteSection)
				.toList();
	}

	private final class NoteSectionEditor {

		private ExamNoteSection noteSection;
		private final TextField title;
		private final MarkdownEditor description;
		private final Details details;
		private String savedTitle;
		private String savedDescriptionMarkdown;

		private NoteSectionEditor(final ExamNoteSection noteSection, final TextField title,
				final MarkdownEditor description, final Details details) {
			this.noteSection = noteSection;
			this.title = title;
			this.description = description;
			this.details = details;
			this.savedTitle = noteSection.title();
			this.savedDescriptionMarkdown = value(description);
		}

		private Details details() {
			return details;
		}

		private boolean isDirty() {
			return !Objects.equals(savedTitle, title.getValue())
					|| !Objects.equals(savedDescriptionMarkdown, value(description));
		}

		private boolean save() {
			if (!isDirty()) {
				return true;
			}
			if (title.getValue() == null || title.getValue().isBlank()) {
				title.setErrorMessage("Titel ist erforderlich.");
				title.setInvalid(true);
				return false;
			}
			final ExamNoteSection updatedNoteSection = new ExamNoteSection(noteSection.id(), noteSection.examId(),
					title.getValue(), value(description), noteSection.sortOrder());
			levelOfExpectationsRepository.saveNoteSection(updatedNoteSection);
			replaceNoteSection(updatedNoteSection);
			noteSection = updatedNoteSection;
			savedTitle = updatedNoteSection.title();
			savedDescriptionMarkdown = updatedNoteSection.descriptionMarkdown();
			return true;
		}
	}
}
