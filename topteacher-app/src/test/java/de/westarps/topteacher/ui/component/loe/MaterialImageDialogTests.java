package de.westarps.topteacher.ui.component.loe;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.data.value.ValueChangeMode;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences.Alignment;
import de.westarps.topteacher.ui.component.SegmentedControl;

class MaterialImageDialogTests {

	private static final LoeMaterialImage IMAGE = new LoeMaterialImage("abcd1234", "0".repeat(64), "image/png",
			1, 1, 1, new byte[] {
					0
			});
	private UI ui;

	@BeforeEach
	void createUi() {
		ui = new UI();
		UI.setCurrent(ui);
	}

	@AfterEach
	void clearUi() {
		UI.setCurrent(null);
	}

	@Test
	void presentsWidthAndAlignmentOnOneRowWithRequestedDefaults() {
		final MaterialImageDialog dialog = openDialog(MaterialImageDialog.Action.INSERT, null, null, result -> {
		});

		final RangeInput width = components(dialog.dialog(), RangeInput.class).getFirst();
		final Span widthValue = components(dialog.dialog(), Span.class).stream()
				.filter(span -> span.hasClassName("tt-material-image-width-value")).findFirst().orElseThrow();
		final SegmentedControl<?> alignment = components(dialog.dialog(), SegmentedControl.class).getFirst();
		final HorizontalLayout options = components(dialog.dialog(), HorizontalLayout.class).stream()
				.filter(layout -> layout.hasClassName("tt-material-image-options")).findFirst().orElseThrow();
		final Div previewStage = components(dialog.dialog(), Div.class).stream()
				.filter(div -> div.hasClassName("tt-material-image-preview-stage")).findFirst().orElseThrow();
		final Div previewPage = components(dialog.dialog(), Div.class).stream()
				.filter(div -> div.hasClassName("tt-material-image-preview-page")).findFirst().orElseThrow();
		final Div previewContent = components(dialog.dialog(), Div.class).stream()
				.filter(div -> div.hasClassName("tt-material-image-preview-content")).findFirst().orElseThrow();
		final Image preview = components(dialog.dialog(), Image.class).getFirst();
		final List<Button> alignmentButtons = components(alignment, Button.class);

		assertThat(options.getChildren().toList().getLast()).isSameAs(alignment);
		assertThat(previewStage.isVisible()).isTrue();
		assertThat(previewStage.getElement().getAttribute("aria-label"))
				.isEqualTo("Bildvorschau auf einer A4-Seite");
		assertThat(previewStage.getChildren()).containsExactly(previewPage);
		assertThat(previewPage.getChildren()).containsExactly(previewContent);
		assertThat(previewContent.getChildren()).containsExactly(preview);
		assertThat(preview.getStyle().get("width")).isNotBlank();
		assertThat(preview.getStyle().get("margin-left")).isEqualTo("auto");
		assertThat(preview.getStyle().get("margin-right")).isEqualTo("auto");
		assertThat(width.getAriaLabel()).contains("Breite in Prozent");
		assertThat(width.getMin()).isZero();
		assertThat(width.getMax()).isEqualTo(100.0);
		assertThat(width.getStep()).isEqualTo(5.0);
		assertThat(width.getValueChangeMode()).isEqualTo(ValueChangeMode.EAGER);
		assertThat(width.getValue()).isZero();
		assertThat(widthValue.getText()).isEqualTo("Auto");
		assertThat(width.getElement().getAttribute("aria-valuetext")).isEqualTo("Auto");
		assertThat(alignment.getLabel()).isEqualTo("Ausrichtung");
		assertThat(alignment.getValue()).isEqualTo(Alignment.CENTER);
		assertThat(alignmentButtons).extracting(button -> button.getIcon().getElement().getAttribute("icon"))
				.containsExactly("lumo:align-left", "lumo:align-center", "lumo:align-right");
		assertThat(alignmentButtons).extracting(button -> button.getElement().getAttribute("aria-label"))
				.containsExactly("Linksbündig", "Zentriert", "Rechtsbündig");
		assertThat(alignmentButtons).extracting(Button::getText).containsOnly("");
	}

	@Test
	void savesSliderAndSegmentValues() {
		final AtomicReference<MaterialImageDialog.Result> result = new AtomicReference<>();
		final MaterialImageDialog dialog = openDialog(MaterialImageDialog.Action.EDIT, 73, Alignment.RIGHT,
				result::set);
		final RangeInput width = components(dialog.dialog(), RangeInput.class).getFirst();
		final SegmentedControl<?> alignment = components(dialog.dialog(), SegmentedControl.class).getFirst();
		final Image preview = components(dialog.dialog(), Image.class).getFirst();

		assertThat(width.getValue()).isEqualTo(75.0);
		assertThat(alignment.getValue()).isEqualTo(Alignment.RIGHT);
		assertThat(preview.getStyle().get("width")).isEqualTo("75.000%");
		assertThat(preview.getStyle().get("margin-left")).isEqualTo("auto");
		assertThat(preview.getStyle().get("margin-right")).isEqualTo("0");
		width.setValue(65.0);
		assertThat(components(dialog.dialog(), Span.class).stream()
				.filter(span -> span.hasClassName("tt-material-image-width-value")).findFirst().orElseThrow().getText())
				.isEqualTo("65 %");
		components(alignment, Button.class).stream()
				.filter(button -> "Zentriert".equals(button.getElement().getAttribute("aria-label"))).findFirst()
				.orElseThrow().click();
		assertThat(preview.getStyle().get("width")).isEqualTo("65.000%");
		assertThat(preview.getStyle().get("margin-left")).isEqualTo("auto");
		assertThat(preview.getStyle().get("margin-right")).isEqualTo("auto");
		components(dialog.dialog(), Button.class).stream().filter(button -> "Übernehmen".equals(button.getText()))
				.findFirst().orElseThrow().click();

		assertThat(result.get()).isNotNull();
		assertThat(result.get().width()).isEqualTo(65);
		assertThat(result.get().alignment()).isEqualTo(Alignment.CENTER);
	}

	@Test
	void savesZeroAsAutomaticWidth() {
		final AtomicReference<MaterialImageDialog.Result> result = new AtomicReference<>();
		final MaterialImageDialog dialog = openDialog(MaterialImageDialog.Action.EDIT, 50, Alignment.LEFT,
				result::set);
		final RangeInput width = components(dialog.dialog(), RangeInput.class).getFirst();

		width.setValue(0.0);
		components(dialog.dialog(), Button.class).stream().filter(button -> "Übernehmen".equals(button.getText()))
				.findFirst().orElseThrow().click();

		assertThat(result.get()).isNotNull();
		assertThat(result.get().width()).isNull();
		assertThat(result.get().alignment()).isEqualTo(Alignment.LEFT);
	}

	private MaterialImageDialog openDialog(final MaterialImageDialog.Action action, final Integer width,
			final Alignment alignment,
			final java.util.function.Consumer<MaterialImageDialog.Result> resultHandler) {
		final MaterialImageDialog dialog = new MaterialImageDialog(action, "Diagramm", width, alignment, IMAGE,
				bytes -> IMAGE, resultHandler);
		dialog.open();
		return dialog;
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
