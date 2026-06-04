package de.topteacher.ui.component;

import java.util.List;

import com.flowingcode.vaadin.addons.markdown.MarkdownEditor;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;

import de.topteacher.backend.repo.ExpectationHorizonRepository;
import de.topteacher.model.Exam;
import de.topteacher.model.ExamNoteSection;

public class ExamNotesEditor extends VerticalLayout {

	private final ExpectationHorizonRepository expectationHorizonRepository;

	private Exam exam;
	private List<ExamNoteSection> noteSections = List.of();

	public ExamNotesEditor(final ExpectationHorizonRepository expectationHorizonRepository) {
		this.expectationHorizonRepository = expectationHorizonRepository;

		addClassName("tt-exam-notes-editor");
		setPadding(false);
		setSpacing(false);
		setSizeFull();
	}

	public void setExam(final Exam exam) {
		this.exam = exam;
		refresh();
	}

	private void refresh() {
		removeAll();
		if (exam == null) {
			add(new Span("Bitte wählen Sie eine Klausur aus."));
			return;
		}

		noteSections = expectationHorizonRepository.findNoteSectionsByExamId(exam.id());

		final Button addSection = commandButton("Abschnitt hinzufügen", VaadinIcon.PLUS, event -> {
			expectationHorizonRepository.saveNoteSection(new ExamNoteSection(null, exam.id(), "Neuer Abschnitt", "",
					expectationHorizonRepository.nextNoteSectionSortOrder(exam.id())));
			refresh();
		});
		addSection.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		final VerticalLayout content = new VerticalLayout(addSection);
		content.addClassName("tt-eh-scroll-area");
		content.setPadding(false);
		content.setSizeFull();

		if (noteSections.isEmpty()) {
			content.add(emptyState("Noch keine Notizen angelegt."));
		} else {
			noteSections.forEach(noteSection -> content.add(createNoteSectionDetails(noteSection)));
		}

		add(content);
		expand(content);
	}

	private Details createNoteSectionDetails(final ExamNoteSection noteSection) {
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
			if (!event.isFromClient()) {
				return;
			}
			if (event.getValue() == null || event.getValue().isBlank()) {
				Notification.show("Titel ist erforderlich.");
				title.setValue(noteSection.title());
				return;
			}
			expectationHorizonRepository.saveNoteSection(new ExamNoteSection(noteSection.id(), noteSection.examId(),
					event.getValue(), noteSection.descriptionMarkdown(), noteSection.sortOrder()));
		});

		final MarkdownEditor description = new MarkdownEditor(noteSection.descriptionMarkdown());
		description.addClassName("tt-markdown-editor");
		description.setPlaceholder("Beschreibung");
		description.setWidthFull();
		description.setHeight("18rem");

		final Span editorLabel = new Span("Beschreibung");
		editorLabel.addClassName("tt-field-label");

		final VerticalLayout markdownBlock = new VerticalLayout(editorLabel, description);
		markdownBlock.addClassName("tt-markdown-block");
		markdownBlock.setPadding(false);
		markdownBlock.setWidthFull();

		final Button save = commandButton("Speichern", VaadinIcon.CHECK, event -> {
			if (title.getValue() == null || title.getValue().isBlank()) {
				Notification.show("Titel ist erforderlich.");
				return;
			}
			expectationHorizonRepository.saveNoteSection(new ExamNoteSection(noteSection.id(), noteSection.examId(),
					title.getValue(), value(description), noteSection.sortOrder()));
			refresh();
		});
		save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

		final HorizontalLayout actions = new HorizontalLayout(save, moveButton("Nach oben", -1, noteSection),
				moveButton("Nach unten", 1, noteSection), deleteButton(event -> {
					expectationHorizonRepository.deleteNoteSection(noteSection.id());
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
		details.setOpened(true);
		return details;
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
			expectationHorizonRepository.moveNoteSection(noteSection, offset);
			refresh();
		});
		final int index = noteSections.indexOf(noteSection);
		button.setEnabled(index >= 0 && index + offset >= 0 && index + offset < noteSections.size());
		return button;
	}

	private Button deleteButton(final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = iconButton("Löschen", VaadinIcon.TRASH, listener);
		button.addThemeVariants(ButtonVariant.LUMO_ERROR);
		return button;
	}

	private Button commandButton(final String text, final VaadinIcon icon,
			final ComponentEventListener<ClickEvent<Button>> listener) {
		final Button button = new Button(text, icon.create(), listener);
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
}
