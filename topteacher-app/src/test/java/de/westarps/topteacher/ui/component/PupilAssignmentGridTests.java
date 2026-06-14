package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

@SuppressWarnings({ "rawtypes", "unchecked" })
class PupilAssignmentGridTests {

	@Test
	void quickFilterDoesNotMatchTechnicalId() {
		final PupilAssignmentGrid assignmentGrid = new PupilAssignmentGrid("Schüler:innen suchen");
		final Pupil pupil = new Pupil(42, "Ada", "Lovelace", Lifecycle.ACTIVE);
		assignmentGrid.setRows(List.of(pupil), List.of());

		textField(assignmentGrid, "Schnellfilter").setValue("42");

		assertThat(grid(assignmentGrid).getListDataView().getItems()).isEmpty();
	}

	private static Grid grid(final Component root) {
		return components(root, Grid.class).getFirst();
	}

	private static TextField textField(final Component root, final String label) {
		return components(root, TextField.class).stream().filter(field -> label.equals(field.getLabel())).findFirst()
				.orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
