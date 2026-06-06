package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

class AbstractDesignerTests {

	@Test
	void definesFixedToolbarAndScrollableContentFrame() {
		final TestDesigner designer = new TestDesigner();

		designer.render();

		final List<Component> children = designer.getChildren().toList();
		assertThat(designer.getClassNames()).contains("tt-designer", "tt-test-designer");
		assertThat(children).hasSize(2);
		assertThat(children.get(0)).isInstanceOf(HorizontalLayout.class);
		assertThat(children.get(0).getClassNames()).contains("tt-designer-toolbar");
		assertThat(children.get(1)).isInstanceOf(VerticalLayout.class);
		assertThat(children.get(1).getClassNames()).contains("tt-designer-content");
	}

	private static final class TestDesigner extends AbstractDesigner {

		private TestDesigner() {
			super("tt-test-designer");
		}

		private void render() {
			resetDesigner();
			toolbar().add(new Span("Toolbar"));
			content().add(new Span("Content"));
			showDesigner();
		}
	}
}
