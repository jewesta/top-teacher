package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

import de.westarps.topteacher.ui.component.ActionToolbar;

class LoeSectionComponentsTests {

	@Test
	void treeActionRowsAdoptSharedToolbarPresentationWithoutRecreatingControls() {
		final LoeSectionComponents components = new LoeSectionComponents(new LoeSaveController());
		final AtomicInteger additions = new AtomicInteger();
		final Button add = components.commandButton("Teilaufgabe hinzufügen", VaadinIcon.PLUS,
				event -> additions.incrementAndGet());
		final Button up = components.moveButton("Nach oben", -1, List.of("A", "B"), "A", event -> { });
		final Button down = components.moveButton("Nach unten", 1, List.of("A", "B"), "A", event -> { });
		final Button delete = components.deleteButton("Aufgabe löschen?", () -> { });

		final HorizontalLayout additionsRow = components.actionRow(up, down, add, delete);
		final HorizontalLayout taskRow = components.actionRow(
				components.moveButton("Nach oben", -1, List.of("A", "B"), "B", event -> { }),
				components.moveButton("Nach unten", 1, List.of("A", "B"), "B", event -> { }),
				components.deleteButton("Aufgabe löschen?", () -> { }));

		assertThat(additionsRow.getChildren()).containsExactly(add, up, down, delete);
		for (final HorizontalLayout row : List.of(additionsRow, taskRow)) {
			assertThat(row).isInstanceOf(ActionToolbar.class);
			assertThat(row.getClassNames()).contains("tt-action-toolbar", "tt-eh-actions");
			assertThat(row.getThemeNames()).doesNotContain("spacing");
			assertThat(row.getAlignItems()).isEqualTo(HorizontalLayout.Alignment.CENTER);
			assertThat(row.getChildren().map(Button.class::cast)).allSatisfy(button -> {
				assertThat(button.getThemeNames()).doesNotContain(ButtonVariant.LUMO_SMALL.getVariantName(),
						ButtonVariant.LUMO_TERTIARY_INLINE.getVariantName(), ButtonVariant.LUMO_PRIMARY.getVariantName());
				assertThat(button.getClassNames()).contains("tt-toolbar-element", "tt-toolbar-action");
			});
		}
		assertThat(add.getText()).isEqualTo("Teilaufgabe hinzufügen");
		assertThat(add.getThemeNames()).doesNotContain(ButtonVariant.LUMO_ICON.getVariantName(),
				ButtonVariant.LUMO_TERTIARY.getVariantName());
		assertThat(up.isEnabled()).isFalse();
		assertThat(down.isEnabled()).isTrue();
		assertThat(delete.getTooltip().getText()).isEqualTo("Löschen...");
		add.click();
		assertThat(additions).hasValue(1);
	}

	@Test
	void normalizingLockedTreeActionsPreservesTheirReasonAndDisabledState() {
		final LoeSectionComponents components = new LoeSectionComponents(new LoeSaveController());
		final Button up = components.moveButton("Nach oben", -1, List.of("A", "B"), "B", event -> { }, true);
		final Button delete = components.deleteButton("Anforderung löschen?", () -> { });
		components.lockCorrectionModeAction(delete);

		components.actionRow(up, delete);

		for (final Button button : List.of(up, delete)) {
			assertThat(button.isEnabled()).isFalse();
			assertThat(button.getTooltip().getText()).isEqualTo(LoeSectionComponents.CORRECTION_MODE_TOOLTIP);
			assertThat(button.getThemeNames()).contains(ButtonVariant.LUMO_ICON.getVariantName(),
					ButtonVariant.LUMO_TERTIARY.getVariantName())
					.doesNotContain(ButtonVariant.LUMO_SMALL.getVariantName());
		}
		assertThat(delete.getAriaLabel()).contains("Löschen...");
	}
}
