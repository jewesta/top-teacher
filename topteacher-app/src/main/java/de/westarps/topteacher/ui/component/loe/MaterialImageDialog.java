package de.westarps.topteacher.ui.component.loe;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.RangeInput;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;
import com.vaadin.flow.theme.lumo.LumoIcon;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences.Alignment;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.SegmentedControl;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

final class MaterialImageDialog {

	private final Dialog dialog;
	private final Binder<ImageDetails> binder = new Binder<>(ImageDetails.class);
	private final ImageDetails details = new ImageDetails();
	private final TextField description = new TextField("Beschreibung / Alternativtext");
	private final RangeInput width = new RangeInput();
	private final Span widthValue = new Span();
	private final SegmentedControl<Alignment> alignment = SegmentedControl.withComponents("Ausrichtung",
			List.of(Alignment.values()), MaterialImageDialog::alignmentIcon, MaterialImageDialog::alignmentLabel);
	private final Image preview = new Image();
	private final Button confirm;
	private LoeMaterialImage image;

	MaterialImageDialog(final Action action, final String initialDescription, final Integer initialWidth,
			final Alignment initialAlignment,
			final LoeMaterialImage prefilledImage,
			final Function<byte[], LoeMaterialImage> processor, final Consumer<Result> resultHandler) {
		Objects.requireNonNull(action, "action must not be null");
		Objects.requireNonNull(processor, "processor must not be null");
		Objects.requireNonNull(resultHandler, "resultHandler must not be null");
		confirm = TopTeacherDialogs.primaryButton(action == Action.EDIT ? "Übernehmen" : "Einfügen");

		description.setWidthFull();
		width.setMin(0);
		width.setMax(100);
		width.setStep(5.0);
		width.setValueChangeMode(ValueChangeMode.EAGER);
		width.setAriaLabel("Breite in Prozent");
		width.addClassName("tt-material-image-width-slider");
		width.setWidthFull();
		width.addValueChangeListener(event -> updateWidthValue());
		widthValue.addClassName("tt-material-image-width-value");
		alignment.setWidth("11rem");
		preview.setVisible(false);
		preview.setMaxWidth("100%");
		preview.setMaxHeight("20rem");

		binder.forField(description).asRequired("Beschreibung ist erforderlich.")
				.withValidator(value -> !value.isBlank(), "Beschreibung ist erforderlich.")
				.bind(ImageDetails::getDescription, ImageDetails::setDescription);
		binder.forField(width).withConverter(MaterialImageDialog::sliderValueToWidth,
				MaterialImageDialog::widthToSliderValue)
				.bind(ImageDetails::getWidth, ImageDetails::setWidth);
		binder.forField(alignment).bind(ImageDetails::getAlignment, ImageDetails::setAlignment);
		details.setDescription(initialDescription == null ? "" : initialDescription);
		details.setWidth(sliderWidth(initialWidth));
		details.setAlignment(initialAlignment == null ? Alignment.LEFT : initialAlignment);
		binder.readBean(details);
		updateWidthValue();
		binder.addStatusChangeListener(event -> updateConfirmEnabled());
		description.addValueChangeListener(event -> {
			preview.setAlt(event.getValue());
			updateConfirmEnabled();
		});

		final VerticalLayout fields = new VerticalLayout();
		fields.addClassName("tt-dialog-content");
		fields.setPadding(false);
		fields.setWidthFull();
		if (prefilledImage == null) {
			fields.add(upload(processor));
		} else {
			setImage(prefilledImage);
		}
		final Div widthField = widthField();
		final HorizontalLayout imageOptions = new HorizontalLayout(widthField, alignment);
		imageOptions.addClassName("tt-material-image-options");
		imageOptions.setAlignItems(FlexComponent.Alignment.END);
		imageOptions.setFlexGrow(1, widthField);
		imageOptions.setPadding(false);
		imageOptions.setWidthFull();
		fields.add(preview, description, imageOptions);

		final Button cancel = new Button("Abbrechen");
		dialog = TopTeacherDialogs.create(action == Action.EDIT ? "Bild bearbeiten" : "Bild einfügen", fields,
				TopTeacherDialogs.buttonBar(cancel, confirm));
		dialog.setCloseOnEsc(true);
		dialog.setCloseOnOutsideClick(false);
		cancel.addClickListener(event -> dialog.close());
		confirm.addClickListener(event -> {
			if (image == null || !binder.writeBeanIfValid(details)) {
				updateConfirmEnabled();
				return;
			}
			resultHandler.accept(new Result(image, details.getDescription().trim(), details.getWidth(),
					details.getAlignment()));
			dialog.close();
		});
		updateConfirmEnabled();
	}

	void open() {
		dialog.open();
	}

	Dialog dialog() {
		return dialog;
	}

	private Upload upload(final Function<byte[], LoeMaterialImage> processor) {
		final Upload upload = new Upload(inMemoryHandler((metadata, bytes) -> {
			try {
				setImage(processor.apply(bytes));
				uploadFinished();
			} catch (final IllegalArgumentException exception) {
				Notification.show(exception.getMessage());
			}
		}));
		upload.setAcceptedFileTypes("image/png", "image/jpeg");
		upload.setMaxFiles(1);
		upload.setMaxFileSize(MaterialImageProcessor.MAX_UPLOAD_BYTES);
		upload.addClassName("tt-material-image-upload");
		upload.setUploadButton(Buttons.dialogOpener("Datei auswählen"));
		final Span separator = new Span("oder");
		separator.addClassName("tt-material-image-upload-separator");
		upload.setDropLabelIcon(separator);
		final Div dropTarget = new Div(new Span("PNG- oder JPEG-Bild hier ablegen"));
		dropTarget.addClassName("tt-material-image-drop-target");
		upload.setDropLabel(dropTarget);
		restrictDropTarget(upload);
		upload.addFileRejectedListener(event -> Notification.show("Bitte wähle eine PNG- oder JPEG-Datei bis 10 MiB."));
		upload.setWidthFull();
		return upload;
	}

	private static void restrictDropTarget(final Upload upload) {
		upload.getElement().executeJs("""
         const isDropTarget = event => event.composedPath().some(
            element => element.classList?.contains('tt-material-image-drop-target'));
         ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(type =>
            this.addEventListener(type, event => {
               if (!isDropTarget(event)) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
               }
            }, { capture: true }));
         """);
	}

	private void uploadFinished() {
		dialog.getChildren().flatMap(component -> component.getChildren())
				.filter(Upload.class::isInstance).map(Upload.class::cast).forEach(upload -> upload.setVisible(false));
	}

	private void setImage(final LoeMaterialImage image) {
		this.image = image;
		preview.setSrc("data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.data()));
		preview.setAlt(description.getValue());
		preview.setVisible(true);
		updateConfirmEnabled();
	}

	private void updateConfirmEnabled() {
		confirm.setEnabled(image != null && !description.getValue().isBlank() && binder.isValid());
	}

	private Div widthField() {
		final Span label = new Span("Breite");
		label.addClassName("tt-material-image-options-label");
		final Div control = new Div(width, widthValue);
		control.addClassName("tt-material-image-width-control");
		final Div field = new Div(label, control);
		field.addClassName("tt-material-image-width-field");
		return field;
	}

	private void updateWidthValue() {
		final int value = width.getValue().intValue();
		final String text = value == 0 ? "Auto" : value + " %";
		widthValue.setText(text);
		width.getElement().setAttribute("aria-valuetext", text);
	}

	private static Component alignmentIcon(final Alignment value) {
		return switch (value) {
			case LEFT -> LumoIcon.ALIGN_LEFT.create();
			case CENTER -> LumoIcon.ALIGN_CENTER.create();
			case RIGHT -> LumoIcon.ALIGN_RIGHT.create();
		};
	}

	private static String alignmentLabel(final Alignment value) {
		return switch (value) {
			case LEFT -> "Linksbündig";
			case CENTER -> "Zentriert";
			case RIGHT -> "Rechtsbündig";
		};
	}

	private static int sliderWidth(final Integer initialWidth) {
		if (initialWidth == null) {
			return 0;
		}
		return Math.max(5, Math.min(100, Math.round(initialWidth / 5.0f) * 5));
	}

	private static Integer sliderValueToWidth(final Double value) {
		return value.intValue() == 0 ? null : value.intValue();
	}

	private static Double widthToSliderValue(final Integer value) {
		return value == null ? 0.0 : value.doubleValue();
	}

	static InMemoryUploadHandler inMemoryHandler(final com.vaadin.flow.server.streams.InMemoryUploadCallback callback) {
		return new InMemoryUploadHandler(callback) {

			@Override
			public long getFileSizeMax() {
				return MaterialImageProcessor.MAX_UPLOAD_BYTES;
			}

			@Override
			public long getFileCountMax() {
				return 1;
			}
		};
	}

	record Result(LoeMaterialImage image, String description, Integer width, Alignment alignment) {
	}

	enum Action {
		INSERT,
		EDIT
	}

	public static final class ImageDetails {

		private String description = "";
		private Integer width;
		private Alignment alignment = Alignment.LEFT;

		public String getDescription() {
			return description;
		}

		public void setDescription(final String description) {
			this.description = description;
		}

		public Integer getWidth() {
			return width;
		}

		public void setWidth(final Integer width) {
			this.width = width;
		}

		public Alignment getAlignment() {
			return alignment;
		}

		public void setAlignment(final Alignment alignment) {
			this.alignment = alignment;
		}
	}
}
