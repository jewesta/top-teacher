package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;

class AbstractFormEditorTests {

	@Test
	void responsiveEditorUsesSharedTwoColumnFormContract() {
		final AbstractFormEditor editor = AbstractFormEditor.responsive("tt-test-editor",
				List.of(new TextField("A"), new TextField("B")), List.of(new Button("Speichern")));

		assertThat(editor.getClassNames()).contains("tt-editor", "tt-test-editor");
		assertThat(editor.form().getClassNames()).contains("tt-editor-form");
		assertThat(editor.form().getResponsiveSteps()).map(FormLayout.ResponsiveStep::toJson)
				.extracting(step -> step.get("minWidth").asString()).containsExactly("0", "32rem");
		assertThat(editor.form().getResponsiveSteps()).map(FormLayout.ResponsiveStep::toJson)
				.extracting(step -> step.get("columns").asInt()).containsExactly(1, 2);
		assertThat(editor.actions().getClassNames()).contains("tt-editor-actions");
		assertThat(components(editor, FormLayout.class)).hasSize(1);
		assertThat(components(editor, HorizontalLayout.class)).hasSize(1);
	}

	@Test
	void contentOnlyEditorKeepsEditorContainerWithoutAddingEmptyFormOrActions() {
		final AbstractFormEditor editor = AbstractFormEditor.contentOnly("tt-test-bulk-editor",
				List.of(new Span("Auswahl")));

		assertThat(editor.getClassNames()).contains("tt-editor", "tt-test-bulk-editor");
		assertThat(components(editor, FormLayout.class)).isEmpty();
		assertThat(components(editor, HorizontalLayout.class)).isEmpty();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return root.getChildren()
				.flatMap(child -> java.util.stream.Stream.concat(java.util.stream.Stream.of(child),
						components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
