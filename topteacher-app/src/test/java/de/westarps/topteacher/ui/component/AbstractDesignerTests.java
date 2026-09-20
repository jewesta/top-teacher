package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import de.westarps.validate.TestResults;
import de.westarps.validate.ValidationSummary;
import de.westarps.vaadin.tray.StatusTray;

class AbstractDesignerTests {

	@Test
	void definesFixedToolbarAndScrollableContentFrame() {
		final TestDesigner designer = new TestDesigner();

		designer.render();

		final List<Component> children = designer.getChildren().toList();
		assertThat(designer.getClassNames()).contains("tt-designer", "tt-test-designer");
		assertThat(children).hasSize(3);
		assertThat(children.get(0)).isInstanceOf(HorizontalLayout.class);
		assertThat(children.get(0).getClassNames()).contains("tt-designer-toolbar");
		assertThat(children.get(1)).isInstanceOf(VerticalLayout.class);
		assertThat(children.get(1).getClassNames()).contains("tt-designer-content");
		assertThat(children.get(2)).isInstanceOf(StatusTray.class);
		assertThat(children.get(2).isVisible()).isFalse();
	}

	@Test
	void skipsEmptyToolbarRows() {
		final TestDesigner designer = new TestDesigner();

		designer.renderSummaryOnly();

		final List<Component> children = designer.getChildren().toList();
		assertThat(children).hasSize(3);
		assertThat(children.get(0)).isInstanceOf(HorizontalLayout.class);
		assertThat(children.get(0).getClassNames()).contains("tt-designer-toolbar-summary");
		assertThat(children.get(1)).isInstanceOf(VerticalLayout.class);
		assertThat(children.get(1).getClassNames()).contains("tt-designer-content");
		assertThat(children.get(2)).isInstanceOf(StatusTray.class);
	}

	@Test
	void preservesOneStatusTrayAcrossDesignerLayouts() {
		final TestDesigner designer = new TestDesigner();
		designer.render();
		final StatusTray tray = designer.exposedStatusTray();

		designer.showValidation(TestResults.warning("Unvollständig"));

		assertThat(tray.isVisible()).isTrue();
		assertThat(tray.getResults().getTestResults()).extracting(result -> result.message())
				.containsExactly("Unvollständig");

		designer.renderMessage();

		assertThat(designer.getChildren().toList()).containsExactly(designer.message(), tray);
		assertThat(designer.exposedStatusTray()).isSameAs(tray);
	}

	private static final class TestDesigner extends AbstractDesigner {

		private final Span message = new Span("Message");

		private TestDesigner() {
			super("tt-test-designer");
		}

		private void render() {
			resetDesigner();
			toolbar().add(new Span("Toolbar"));
			content().add(new Span("Content"));
			showDesigner();
		}

		private void renderSummaryOnly() {
			resetDesigner();
			toolbarSummary().add(new Span("Summary"));
			content().add(new Span("Content"));
			showDesigner();
		}

		private void renderMessage() {
			showDesignerMessage(message);
		}

		private void showValidation(final ValidationSummary results) {
			setValidationResults(results);
		}

		private StatusTray exposedStatusTray() {
			return statusTray();
		}

		private Span message() {
			return message;
		}
	}
}
