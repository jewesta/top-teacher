package de.westarps.topteacher.ui.component.loe;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;

import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

final class MaterialImageDialog {

	private final Dialog dialog;
	private final Binder<ImageDetails> binder = new Binder<>(ImageDetails.class);
	private final ImageDetails details = new ImageDetails();
	private final TextField description = new TextField("Beschreibung / Alternativtext");
	private final ComboBox<Integer> width = new ComboBox<>("Breite");
	private final Image preview = new Image();
	private final Button confirm;
	private LoeMaterialImage image;

	MaterialImageDialog(final Action action, final String initialDescription, final Integer initialWidth,
			final LoeMaterialImage prefilledImage,
			final Function<byte[], LoeMaterialImage> processor, final Consumer<Result> resultHandler) {
		Objects.requireNonNull(action, "action must not be null");
		Objects.requireNonNull(processor, "processor must not be null");
		Objects.requireNonNull(resultHandler, "resultHandler must not be null");
		confirm = TopTeacherDialogs.primaryButton(action == Action.EDIT ? "Übernehmen" : "Einfügen");

		description.setWidthFull();
		width.setItems(List.of(25, 50, 75, 100));
		width.setItemLabelGenerator(value -> value + " %");
		width.setPlaceholder("Automatisch");
		width.setClearButtonVisible(true);
		width.setWidthFull();
		preview.setVisible(false);
		preview.setMaxWidth("100%");
		preview.setMaxHeight("20rem");

		binder.forField(description).asRequired("Beschreibung ist erforderlich.")
				.withValidator(value -> !value.isBlank(), "Beschreibung ist erforderlich.")
				.bind(ImageDetails::getDescription, ImageDetails::setDescription);
		binder.forField(width).bind(ImageDetails::getWidth, ImageDetails::setWidth);
		details.setDescription(initialDescription == null ? "" : initialDescription);
		details.setWidth(initialWidth);
		binder.readBean(details);
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
		fields.add(preview, description, width);

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
			resultHandler.accept(new Result(image, details.getDescription().trim(), details.getWidth()));
			dialog.close();
		});
		updateConfirmEnabled();
	}

	void open() {
		dialog.open();
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

	record Result(LoeMaterialImage image, String description, Integer width) {
	}

	enum Action {
		INSERT,
		EDIT
	}

	public static final class ImageDetails {

		private String description = "";
		private Integer width;

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
	}
}
