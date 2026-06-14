package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.tabs.TabSheet;

import de.westarps.topteacher.backend.repo.PupilRepository;

class PupilsViewTests {

	@Test
	void usesSingularEditorTabLabel() {
		final PupilRepository pupilRepository = mock(PupilRepository.class);
		when(pupilRepository.findAll()).thenReturn(List.of());
		when(pupilRepository.findLatestSchoolClassByPupilId()).thenReturn(Map.of());

		final PupilsView view = new PupilsView(pupilRepository);
		final TabSheet contextTabs = components(view, TabSheet.class).getFirst();

		assertThat(view.getPageTitle()).isEqualTo("Schüler:innen");
		assertThat(contextTabs.getTabAt(0).getLabel()).isEqualTo("Schüler:in");
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
