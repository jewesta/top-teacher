package de.westarps.topteacher.ui.view;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
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
			textField(tab, "Fach").setValue(" Physik ");

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

			button(tab, "Archivieren").click();
		} finally {
			UI.setCurrent(null);
		}

		verify(subjectRepository).archive(subject.id());
	}

	private static void select(final Grid<Subject> grid, final Subject subject) {
		grid.asSingleSelect().setValue(subject);
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

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
