package de.westarps.topteacher.ui.component.loe;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.server.streams.InMemoryUploadHandler;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

final class MaterialImageDialog extends Dialog {

	private final Binder<ImageDetails> binder = new Binder<>(ImageDetails.class);
	private final ImageDetails details = new ImageDetails();
	private final TextField description = new TextField("Beschreibung / Alternativtext");
	private final ComboBox<Integer> width = new ComboBox<>("Breite");
	private final Image preview = new Image();
	private final Button insert = new Button("Einfügen");
	private LoeMaterialImage image;

	MaterialImageDialog(final String initialDescription, final LoeMaterialImage prefilledImage,
			final Function<byte[], LoeMaterialImage> processor, final Consumer<Result> resultHandler) {
		Objects.requireNonNull(processor, "processor must not be null");
		Objects.requireNonNull(resultHandler, "resultHandler must not be null");
		setHeaderTitle("Bild einfügen");
		setCloseOnEsc(true);
		setCloseOnOutsideClick(false);

		description.setWidthFull();
		description.setValue(initialDescription == null ? "" : initialDescription);
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
		binder.readBean(details);
		description.setValue(initialDescription == null ? "" : initialDescription);
		binder.addStatusChangeListener(event -> updateInsertEnabled());
		description.addValueChangeListener(event -> {
			preview.setAlt(event.getValue());
			updateInsertEnabled();
		});

		final VerticalLayout fields = new VerticalLayout();
		fields.setPadding(false);
		fields.setWidthFull();
		if (prefilledImage == null) {
			fields.add(upload(processor));
		} else {
			setImage(prefilledImage);
		}
		fields.add(preview, description, width);
		add(fields);

		final Button cancel = new Button("Abbrechen", event -> close());
		insert.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
		insert.addClickListener(event -> {
			if (image == null || !binder.writeBeanIfValid(details)) {
				updateInsertEnabled();
				return;
			}
			resultHandler.accept(new Result(image, details.getDescription().trim(), details.getWidth()));
			close();
		});
		getFooter().add(new HorizontalLayout(cancel, insert));
		updateInsertEnabled();
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
		upload.setUploadButton(new Button("Datei auswählen"));
		upload.setDropLabel(new com.vaadin.flow.component.html.Span("PNG- oder JPEG-Bild hier ablegen"));
		upload.addFileRejectedListener(event -> Notification.show("Bitte wähle eine PNG- oder JPEG-Datei bis 10 MiB."));
		upload.setWidthFull();
		return upload;
	}

	private void uploadFinished() {
		getChildren().flatMap(component -> component.getChildren())
				.filter(Upload.class::isInstance).map(Upload.class::cast).forEach(upload -> upload.setVisible(false));
	}

	private void setImage(final LoeMaterialImage image) {
		this.image = image;
		preview.setSrc("data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.data()));
		preview.setAlt(description.getValue());
		preview.setVisible(true);
		updateInsertEnabled();
	}

	private void updateInsertEnabled() {
		insert.setEnabled(image != null && !description.getValue().isBlank() && binder.isValid());
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
