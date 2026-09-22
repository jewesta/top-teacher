package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ContextTabContentTests {

	@Test
	void allContextTabContentsShareTheSameOuterLayout() {
		final List<ContextTabContent> contents = List.of(new SplitEditorTabContent(),
				new PupilAssignmentGrid("Schüler:innen suchen"), new TestDesigner());

		for (final ContextTabContent content : contents) {
			assertThat(content.getClassNames()).contains("tt-context-tab-content");
			assertThat(content.isPadding()).isFalse();
			assertThat(content.isSpacing()).isFalse();
			assertThat(content.getWidth()).isEqualTo("100%");
			assertThat(content.getHeight()).isEqualTo("100%");
		}
	}

	private static final class TestDesigner extends AbstractDesigner {

		private TestDesigner() {
			super("tt-test-designer");
		}
	}
}
