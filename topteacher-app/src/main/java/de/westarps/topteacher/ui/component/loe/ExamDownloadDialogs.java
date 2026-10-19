package de.westarps.topteacher.ui.component.loe;

import java.util.List;
import java.util.function.Consumer;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.radiobutton.RadioGroupVariant;
import com.vaadin.flow.data.binder.Binder;

import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

final class ExamDownloadDialogs {

	enum TaskSheetScope {
		ANONYMOUS("Kopiervorlage mit Freifeld für Name"), ALL_PUPILS("Für alle Schüler:innen"), SELECTED_PUPIL("Für ausgewählte Schüler:in");

		private final String label;

		TaskSheetScope(final String label) {
			this.label = label;
		}
	}

	enum Audience {
		PUPILS("Schüler:innen"), TEACHERS("Lehrer:innen");

		private final String label;

		Audience(final String label) {
			this.label = label;
		}
	}

	private ExamDownloadDialogs() {
	}

	static void openTaskSheet(final List<Pupil> pupils, final Consumer<String> download) {
		final RadioButtonGroup<TaskSheetScope> scope = new RadioButtonGroup<>();
		scope.setAriaLabel("Aufgabenblatt für");
		scope.setItems(TaskSheetScope.values());
		scope.setItemLabelGenerator(value -> value.label);
		scope.addThemeVariants(RadioGroupVariant.LUMO_VERTICAL);
		scope.setItemEnabledProvider(value -> value == TaskSheetScope.ANONYMOUS || !pupils.isEmpty());
		final ComboBox<Pupil> pupil = new ComboBox<>("Schüler:in");
		pupil.setItems(pupils);
		pupil.setItemLabelGenerator(value -> value.name() + " " + value.surname());
		pupil.setWidthFull();
		pupil.setVisible(false);
		scope.addValueChangeListener(event -> pupil.setVisible(event.getValue() == TaskSheetScope.SELECTED_PUPIL));
		final Binder<TaskSheetOptions> binder = new Binder<>();
		binder.forField(scope).asRequired("Bitte wähle eine Ausgabe.")
				.bind(TaskSheetOptions::getScope, TaskSheetOptions::setScope);
		binder.forField(pupil).withValidator(value -> scope.getValue() != TaskSheetScope.SELECTED_PUPIL || value != null,
				"Bitte wähle eine Schülerin oder einen Schüler.")
				.bind(TaskSheetOptions::getPupil, TaskSheetOptions::setPupil);
		final TaskSheetOptions options = new TaskSheetOptions();
		binder.readBean(options);
		final VerticalLayout content = content(scope, pupil);
		if (pupils.isEmpty()) {
			content.add(new Span("Dieser Klausur sind noch keine Schüler:innen zugeordnet."));
		}
		final Dialog dialog = create("Aufgabenblatt laden", content);
		actions(dialog, () -> {
			if (!binder.writeBeanIfValid(options)) {
				return;
			}
			download.accept(switch (options.getScope()) {
				case ANONYMOUS -> "task-sheet.pdf";
				case ALL_PUPILS -> "task-sheets.pdf";
				case SELECTED_PUPIL -> "pupils/" + options.getPupil().id() + "/task-sheet.pdf";
			});
			dialog.close();
		});
		open(dialog);
	}

	static void openResultSheet(final String title, final Consumer<Boolean> download) {
		final RadioButtonGroup<Audience> audience = new RadioButtonGroup<>();
		audience.setLabel("Für:");
		audience.setItems(Audience.values());
		audience.setItemLabelGenerator(value -> value.label);
		audience.setValue(Audience.PUPILS);
		final Dialog dialog = create(title, content(audience));
		actions(dialog, () -> {
			download.accept(audience.getValue() == Audience.TEACHERS);
			dialog.close();
		});
		open(dialog);
	}

	private static VerticalLayout content(final Component... fields) {
		final VerticalLayout content = new VerticalLayout(fields);
		content.addClassName("tt-dialog-content");
		content.setPadding(false);
		content.setWidthFull();
		return content;
	}

	private static Dialog create(final String title, final VerticalLayout content) {
		final Dialog dialog = TopTeacherDialogs.create(title);
		dialog.add(content);
		dialog.setWidth("28rem");
		dialog.setMaxWidth("100%");
		dialog.setCloseOnOutsideClick(false);
		dialog.addClosedListener(event -> dialog.getElement().removeFromParent());
		return dialog;
	}

	private static void actions(final Dialog dialog, final Runnable download) {
		final Button cancel = new Button("Abbrechen", event -> dialog.close());
		final Button load = TopTeacherDialogs.primaryButton("Laden");
		load.addClickListener(event -> download.run());
		dialog.add(TopTeacherDialogs.buttonBar(cancel, load));
	}

	private static void open(final Dialog dialog) {
		if (UI.getCurrent() != null) {
			UI.getCurrent().add(dialog);
		}
		dialog.open();
	}

	public static final class TaskSheetOptions {
		private TaskSheetScope scope = TaskSheetScope.ANONYMOUS;
		private Pupil pupil;

		public TaskSheetScope getScope() {
			return scope;
		}

		public void setScope(final TaskSheetScope scope) {
			this.scope = scope;
		}

		public Pupil getPupil() {
			return pupil;
		}

		public void setPupil(final Pupil pupil) {
			this.pupil = pupil;
		}
	}
}
