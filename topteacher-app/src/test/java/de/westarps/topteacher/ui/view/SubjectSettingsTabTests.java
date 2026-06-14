package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Subject;

@SuppressWarnings({ "rawtypes", "unchecked" })
class SubjectSettingsTabTests {

	@Test
	void savesNewSubjectFromEditor() {
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of(), List.of());
		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			textField(tab, "Name").setValue(" Physik ");

			button(tab, "Anlegen").click();
		} finally {
			UI.setCurrent(null);
		}

		verify(subjectRepository).save(new Subject(null, "Physik", Lifecycle.ACTIVE));
	}

	@Test
	void archivesSelectedSubject() {
		final Subject subject = new Subject(1, "Erdkunde", Lifecycle.ACTIVE);
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of(subject), List.of(subject));
		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			select(grid(tab), subject);

			button(tab, "Archivieren...").click();

			verify(subjectRepository, never()).archive(subject.id());
			final ConfirmDialog confirmation = components(ui, ConfirmDialog.class).getFirst();
			assertThat(confirmation.isOpened()).isTrue();

			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));
		} finally {
			UI.setCurrent(null);
		}

		verify(subjectRepository).archive(subject.id());
	}

	@Test
	void enablesCreateOrSaveButtonOnlyWhenSingleEditorChanges() {
		final Subject subject = new Subject(1, "Erdkunde", Lifecycle.ACTIVE);
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of(subject));
		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);

		final TextField name = textField(tab, "Name");
		final Button createButton = button(tab, "Anlegen");

		assertThat(createButton.isEnabled()).isFalse();

		name.setValue("Physik");

		assertThat(createButton.isEnabled()).isTrue();

		name.clear();

		assertThat(createButton.isEnabled()).isFalse();

		select(grid(tab), subject);
		final Button saveButton = button(tab, "Speichern");

		assertThat(saveButton.isEnabled()).isFalse();

		name.setValue("Geografie");

		assertThat(saveButton.isEnabled()).isTrue();
	}

	@Test
	void quickFilterDoesNotMatchTechnicalId() {
		final Subject subject = new Subject(42, "Biologie", Lifecycle.ACTIVE);
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of(subject));
		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);

		textField(tab, "Schnellfilter").setValue("42");

		assertThat(grid(tab).getListDataView().getItems()).isEmpty();
	}

	@Test
	void hidesLifecycleInCreateModeAndShowsItInEditMode() {
		final Subject subject = new Subject(1, "Erdkunde", Lifecycle.ACTIVE);
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of(subject));
		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);

		final ComboBox<Lifecycle> lifecycle = comboBox(tab, "Status");

		assertThat(lifecycle.isVisible()).isFalse();

		select(grid(tab), subject);

		assertThat(lifecycle.isVisible()).isTrue();
	}

	@Test
	void bulkStatusFieldIsMandatoryLikeSingleEditorStatusField() {
		final Subject activeSubject = new Subject(1, "Erdkunde", Lifecycle.ACTIVE);
		final Subject archivedSubject = new Subject(2, "Biologie", Lifecycle.INACTIVE);
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of(activeSubject, archivedSubject));
		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);

		select(grid(tab), activeSubject);
		select(grid(tab), archivedSubject);

		final ComboBox<Lifecycle> lifecycle = comboBox(tab, "Status");
		assertThat(lifecycle.isRequiredIndicatorVisible()).isTrue();
		assertThat(lifecycle.isClearButtonVisible()).isFalse();
	}

	@Test
	void usesPlainSplitViewSizingInsideSettings() {
		final SubjectRepository subjectRepository = mock(SubjectRepository.class);
		when(subjectRepository.findAll()).thenReturn(List.of());

		final SubjectSettingsTab tab = new SubjectSettingsTab(subjectRepository);

		assertThat(tab.getClassNames()).contains("tt-master-data-view", "tt-subject-settings-tab")
				.doesNotContain("tt-settings-content", "tt-settings-master-data-content");
	}

	private static void select(final Grid<Subject> grid, final Subject subject) {
		grid.select(subject);
	}

	private static Grid<Subject> grid(final Component root) {
		return components(root, Grid.class).getFirst();
	}

	private static TextField textField(final Component root, final String label) {
		return components(root, TextField.class).stream().filter(field -> label.equals(field.getLabel())).findFirst()
				.orElseThrow();
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static ComboBox<Lifecycle> comboBox(final Component root, final String label) {
		return components(root, ComboBox.class).stream().filter(comboBox -> label.equals(comboBox.getLabel()))
				.findFirst().map(comboBox -> (ComboBox<Lifecycle>) comboBox).orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), children(root).flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}

	private static Stream<Component> children(final Component root) {
		final Stream<Component> ordinaryChildren = root.getChildren();
		if (!(root instanceof TabSheet tabSheet)) {
			return ordinaryChildren;
		}
		final Stream<Component> tabChildren = IntStream.range(0, tabSheet.getTabCount())
				.mapToObj(index -> tabSheet.getComponent(tabSheet.getTabAt(index)));
		return Stream.concat(ordinaryChildren, tabChildren);
	}
}
