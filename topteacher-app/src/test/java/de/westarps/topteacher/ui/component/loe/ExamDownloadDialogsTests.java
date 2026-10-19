package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.data.provider.Query;

import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.ui.component.loe.ExamDownloadDialogs.Audience;
import de.westarps.topteacher.ui.component.loe.ExamDownloadDialogs.TaskSheetScope;

class ExamDownloadDialogsTests {

	private static final Pupil PUPIL = new Pupil(42, "Mia", "Weber", Lifecycle.ACTIVE);
	private UI previousUi;
	private UI ui;

	@BeforeEach
	void createUi() {
		previousUi = UI.getCurrent();
		ui = new UI();
		UI.setCurrent(ui);
	}

	@AfterEach
	void restoreUi() {
		UI.setCurrent(previousUi);
	}

	@Test
	void anonymousIsFirstAndUsesStandardDialogAndPrimaryLoadAction() {
		final List<String> downloads = new ArrayList<>();
		ExamDownloadDialogs.openTaskSheet(List.of(PUPIL), downloads::add);
		final Dialog dialog = dialog();
		assertThat(dialog.getClassNames()).contains("tt-dialog");
		assertThat(components(dialog, Span.class)).extracting(Span::getText).contains("Aufgabenblatt laden");
		final var scope = scope(dialog);
		assertThat(scope.getDataProvider().fetch(new Query<>()).map(scope.getItemLabelGenerator()::apply))
				.containsExactly("Kopiervorlage mit Freifeld für Name", "Für alle Schüler:innen", "Für ausgewählte Schüler:in");
		assertThat(scope.getThemeNames()).contains("vertical");
		assertThat(scope.getValue()).isEqualTo(TaskSheetScope.ANONYMOUS);
		assertThat(components(dialog, Button.class)).extracting(Button::getText).containsExactly("Abbrechen", "Laden");
		assertThat(button(dialog, "Laden").getThemeNames()).contains("primary");
		button(dialog, "Laden").click();
		assertThat(downloads).containsExactly("task-sheet.pdf");
		assertThat(dialog.isOpened()).isFalse();
	}

	@Test
	void allPupilsDownloadsTheCombinedTaskSheets() {
		final List<String> downloads = new ArrayList<>();
		ExamDownloadDialogs.openTaskSheet(List.of(PUPIL), downloads::add);
		final Dialog dialog = dialog();
		scope(dialog).setValue(TaskSheetScope.ALL_PUPILS);
		assertThat(pupil(dialog).isVisible()).isFalse();
		button(dialog, "Laden").click();
		assertThat(downloads).containsExactly("task-sheets.pdf");
	}

	@Test
	void selectedPupilRequiresAChoiceAndUsesThatPupilOnly() {
		final List<String> downloads = new ArrayList<>();
		final var second = new Pupil(43, "Anna", "Klein", Lifecycle.ACTIVE);
		ExamDownloadDialogs.openTaskSheet(List.of(PUPIL, second), downloads::add);
		final Dialog dialog = dialog();
		scope(dialog).setValue(TaskSheetScope.SELECTED_PUPIL);
		assertThat(pupil(dialog).isVisible()).isTrue();
		button(dialog, "Laden").click();
		assertThat(downloads).isEmpty();
		assertThat(dialog.isOpened()).isTrue();
		assertThat(pupil(dialog).isInvalid()).isTrue();
		pupil(dialog).setValue(second);
		button(dialog, "Laden").click();
		assertThat(downloads).containsExactly("pupils/43/task-sheet.pdf");
	}

	@Test
	void switchingBackToAnonymousDoesNotRequireAPupil() {
		final List<String> downloads = new ArrayList<>();
		ExamDownloadDialogs.openTaskSheet(List.of(PUPIL), downloads::add);
		final Dialog dialog = dialog();
		scope(dialog).setValue(TaskSheetScope.SELECTED_PUPIL);
		button(dialog, "Laden").click();
		scope(dialog).setValue(TaskSheetScope.ANONYMOUS);
		button(dialog, "Laden").click();
		assertThat(downloads).containsExactly("task-sheet.pdf");
	}

	@Test
	void emptyRosterStillAllowsAnonymousAndDisablesPersonalizedModes() {
		final List<String> downloads = new ArrayList<>();
		ExamDownloadDialogs.openTaskSheet(List.of(), downloads::add);
		final Dialog dialog = dialog();
		assertThat(scope(dialog).getItemEnabledProvider().test(TaskSheetScope.ANONYMOUS)).isTrue();
		assertThat(scope(dialog).getItemEnabledProvider().test(TaskSheetScope.ALL_PUPILS)).isFalse();
		assertThat(scope(dialog).getItemEnabledProvider().test(TaskSheetScope.SELECTED_PUPIL)).isFalse();
		button(dialog, "Laden").click();
		assertThat(downloads).containsExactly("task-sheet.pdf");
	}

	@Test
	void resultSheetsUseTheSameAudienceChoiceForIndividualAndCombinedDownloads() {
		for (final String title : List.of("Ergebnisbogen laden", "Alle Ergebnisbögen laden")) {
			final List<Boolean> downloads = new ArrayList<>();
			ExamDownloadDialogs.openResultSheet(title, downloads::add);
			Dialog dialog = dialog();
			assertThat(dialog.getClassNames()).contains("tt-dialog");
			assertThat(audience(dialog).getLabel()).isEqualTo("Für:");
			assertThat(audience(dialog).getDataProvider().fetch(new Query<>()).map(audience(dialog).getItemLabelGenerator()::apply))
					.containsExactly("Schüler:innen", "Lehrer:innen");
			assertThat(audience(dialog).getValue()).isEqualTo(Audience.PUPILS);
			button(dialog, "Laden").click();
			assertThat(downloads).containsExactly(false);
			ExamDownloadDialogs.openResultSheet(title, downloads::add);
			dialog = dialog();
			audience(dialog).setValue(Audience.TEACHERS);
			button(dialog, "Laden").click();
			assertThat(downloads).containsExactly(false, true);
		}
	}

	@Test
	void cancelDoesNotDownload() {
		final List<Object> downloads = new ArrayList<>();
		ExamDownloadDialogs.openTaskSheet(List.of(PUPIL), downloads::add);
		button(dialog(), "Abbrechen").click();
		ExamDownloadDialogs.openResultSheet("Ergebnisbogen laden", downloads::add);
		button(dialog(), "Abbrechen").click();
		assertThat(downloads).isEmpty();
		assertThat(components(ui, Dialog.class)).noneMatch(Dialog::isOpened);
	}

	private Dialog dialog() {
		return components(ui, Dialog.class).stream().filter(Dialog::isOpened).findFirst().orElseThrow();
	}

	@SuppressWarnings("unchecked")
	private static RadioButtonGroup<TaskSheetScope> scope(final Dialog dialog) {
		return components(dialog, RadioButtonGroup.class).getFirst();
	}

	@SuppressWarnings("unchecked")
	private static RadioButtonGroup<Audience> audience(final Dialog dialog) {
		return components(dialog, RadioButtonGroup.class).getFirst();
	}

	@SuppressWarnings("unchecked")
	private static ComboBox<Pupil> pupil(final Dialog dialog) {
		return components(dialog, ComboBox.class).getFirst();
	}

	private static Button button(final Dialog dialog, final String label) {
		return components(dialog, Button.class).stream().filter(button -> label.equals(button.getText())).findFirst().orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(type.isInstance(root) ? Stream.of(type.cast(root)) : Stream.empty(),
				root.getChildren().flatMap(child -> components(child, type).stream())).toList();
	}
}
