package de.westarps.topteacher.ui.component.loe;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.SvgIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;

import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.ui.UiUrls;
import de.westarps.vaadin.markdown.MarkdownEditor;

final class LoeMaterialSection extends AbstractLoeSection<LoeMaterial> {

	private final TextField title;
	private final MaterialMarkdownEditor description;
	private final Handler handler;
	private final List<LoeMaterialImage> storedImages;
	private final Map<String, LoeMaterialImage> pendingImages = new LinkedHashMap<>();
	private final MaterialImageProcessor imageProcessor = new MaterialImageProcessor();
	private final SecureRandom random = new SecureRandom();
	private MaterialMarkdownEditor.InsertionPoint droppedInsertionPoint;
	private boolean dedicatedPage;
	private boolean savedDedicatedPage;
	private String savedTitle;
	private String savedDescriptionMarkdown;

	LoeMaterialSection(final LoeMaterial material, final List<LoeMaterial> siblings,
			final LoeSectionComponents components, final Handler handler, final Collection<LoeMaterialImage> images,
			final boolean locked) {
		this(material, siblings, components, handler, components.summaryTitleField(material.title()),
				locked ? null : components.materialMarkdownEditor(material.descriptionMarkdown(),
						"Quellentext oder anderes Material", images),
				images, locked);
	}

	private LoeMaterialSection(final LoeMaterial material, final List<LoeMaterial> siblings,
			final LoeSectionComponents components, final Handler handler, final TextField title,
			final MaterialMarkdownEditor description, final Collection<LoeMaterialImage> images, final boolean locked) {
		super(material, "tt-eh-category", components.summary("Material", title), null, List.of());
		this.title = title;
		this.description = description;
		this.handler = handler;
		this.storedImages = new ArrayList<>(images == null ? List.of() : images);
		this.savedTitle = material.title();
		this.savedDedicatedPage = material.dedicatedPage();
		this.dedicatedPage = material.dedicatedPage();
		final Button pageToggle = new Button(new SvgIcon(), event -> {
			dedicatedPage = !dedicatedPage;
			updatePageToggle(event.getSource());
			components.updateDirty();
		});
		pageToggle.addClassName("tt-material-page-toggle");
		pageToggle.getElement().setAttribute("data-action", "toggle-dedicated-page");
		updatePageToggle(pageToggle);
		if (locked) {
			components.lockCorrectionModeAction(pageToggle);
		}
		this.savedDescriptionMarkdown = normalized(material.descriptionMarkdown());
		title.setReadOnly(locked);
		components.trackDirty(title);
		if (description != null) {
			components.trackDirty(description);
		}
		final Button delete = components.deleteButton("Material löschen?", () -> handler.delete(material));
		if (locked) {
			components.lockCorrectionModeAction(delete);
		}
		final Component descriptionBlock;
		final Upload dropUpload;
		if (locked) {
			descriptionBlock = components.materialMarkdownViewBlock("Inhalt", material.descriptionMarkdown(), images);
			dropUpload = null;
		} else {
			dropUpload = createDropUpload();
			description.addImageRequestListener(point -> openImageDialog(point, null));
			description.addFootnoteRequestListener(point -> openFootnoteDialog(point));
			description.addFileDropListener(drop -> handleFileDrop(drop, dropUpload));
			descriptionBlock = components.markdownBlock("Inhalt", description);
		}
		final Component actions = editorBlockWithMoveButtons(components, siblings, handler, List.of(pageToggle), List.of(delete),
				locked);
		if (dropUpload == null) {
			addToBody(actions, descriptionBlock);
		} else {
			addToBody(dropUpload, actions, descriptionBlock);
		}
	}

	private void updatePageToggle(final Button button) {
		final String label = dedicatedPage ? "Im laufenden Text platzieren" : "Auf eigener Seite platzieren";
		((SvgIcon) button.getIcon()).setSrc(UiUrls.contextRelative(
				dedicatedPage ? "/icons/page-break.svg" : "/icons/page-flow.svg"));
		button.setText(label);
		/* Retain the icon theme after state changes, as with the category eye toggle. */
		button.addThemeVariants(ButtonVariant.LUMO_ICON);
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.getElement().setAttribute("aria-pressed", Boolean.toString(dedicatedPage));
	}

	@Override
	protected boolean isSectionDirty() {
		return savedDedicatedPage != dedicatedPage || !Objects.equals(savedTitle, title.getValue())
				|| description != null
						&& !Objects.equals(savedDescriptionMarkdown, normalized(description.getValue()));
	}

	@Override
	protected boolean saveSection() {
		if (description == null) {
			return true;
		}
		if (title.getValue() == null || title.getValue().isBlank()) {
			Notification.show("Titel ist erforderlich.");
			return false;
		}
		final String descriptionMarkdown = normalized(description.getValue());
		final LoeMaterial saved = handler.save(item(), title.getValue(), descriptionMarkdown, pendingImages.values(), dedicatedPage);
		savedDedicatedPage = saved.dedicatedPage();
		description.setValue(saved.descriptionMarkdown());
		savedTitle = title.getValue();
		savedDescriptionMarkdown = saved.descriptionMarkdown();
		updateStoredImages(saved.descriptionMarkdown());
		pendingImages.clear();
		return true;
	}

	private void updateStoredImages(final String savedMarkdown) {
		final Set<String> referencedIds = LoeMaterialImageReferences.ids(savedMarkdown);
		final Map<String, LoeMaterialImage> canonicalImages = new LinkedHashMap<>();
		storedImages.stream().filter(image -> referencedIds.contains(image.id()))
				.forEach(image -> canonicalImages.put(image.id(), image));
		pendingImages.values().stream()
				.map(image -> handler.findImageByContentHash(image.contentHash()).orElse(image))
				.filter(image -> referencedIds.contains(image.id()))
				.forEach(image -> canonicalImages.put(image.id(), image));
		storedImages.clear();
		storedImages.addAll(canonicalImages.values());
		description.setImages(storedImages);
	}

	private Upload createDropUpload() {
		final Upload[] uploadHolder = new Upload[1];
		final Upload upload = new Upload(MaterialImageDialog.inMemoryHandler((metadata, bytes) -> {
			try {
				final LoeMaterialImage image = prepareImage(bytes);
				openImageDialog(droppedInsertionPoint == null
						? new MaterialMarkdownEditor.InsertionPoint(description.getValue().length(),
								description.getValue().length(), "")
						: droppedInsertionPoint, image);
			} catch (final IllegalArgumentException exception) {
				Notification.show(exception.getMessage());
			} finally {
				uploadClear(uploadHolder[0]);
			}
		}));
		uploadHolder[0] = upload;
		upload.setAcceptedFileTypes("image/png", "image/jpeg");
		upload.setMaxFiles(1);
		upload.setMaxFileSize(MaterialImageProcessor.MAX_UPLOAD_BYTES);
		upload.getStyle().set("display", "none");
		return upload;
	}

	private void handleFileDrop(final MaterialMarkdownEditor.FileDrop drop, final Upload upload) {
		if (drop.fileCount() != 1 || (!drop.mediaType().isBlank() && !isSupportedMediaType(drop.mediaType()))) {
			Notification.show("Bitte wähle eine PNG- oder JPEG-Datei.");
			return;
		}
		droppedInsertionPoint = drop.insertionPoint();
		description.uploadDroppedFileWith(upload);
	}

	private void openImageDialog(final MaterialMarkdownEditor.InsertionPoint insertionPoint,
			final LoeMaterialImage prefilledImage) {
		final Optional<ExistingImage> existingImage = prefilledImage == null
				? LoeMaterialImageReferences.findAt(description.getValue(), insertionPoint.start(), insertionPoint.end())
						.flatMap(occurrence -> findImageById(occurrence.id())
								.map(image -> new ExistingImage(occurrence, image)))
				: Optional.empty();
		final MaterialMarkdownEditor.InsertionPoint replacementPoint = existingImage
				.map(existing -> new MaterialMarkdownEditor.InsertionPoint(existing.occurrence().start(),
						existing.occurrence().end(), existing.occurrence().description()))
				.orElse(insertionPoint);
		final MaterialImageDialog.Action action = existingImage.isPresent() ? MaterialImageDialog.Action.EDIT
				: MaterialImageDialog.Action.INSERT;
		final String initialDescription = existingImage.map(existing -> existing.occurrence().description())
				.orElse(insertionPoint.selectedText());
		final Integer initialWidth = existingImage.map(existing -> existing.occurrence().width()).orElse(null);
		final LoeMaterialImageReferences.Alignment initialAlignment = existingImage
				.map(existing -> existing.occurrence().alignment()).orElse(LoeMaterialImageReferences.Alignment.CENTER);
		final LoeMaterialImage dialogImage = existingImage.map(ExistingImage::image).orElse(prefilledImage);
		new MaterialImageDialog(action, initialDescription, initialWidth, initialAlignment, dialogImage,
				this::prepareImage, result -> {
			final LoeMaterialImage image = result.image();
			if (storedImages.stream().noneMatch(stored -> stored.id().equals(image.id()))) {
				pendingImages.putIfAbsent(image.id(), image);
			}
			description.setImages(allImages());
			description.insertImage(replacementPoint, declaration(result));
		}).open();
	}

	private void openFootnoteDialog(final MarkdownEditor.InsertionPoint insertionPoint) {
		new FootnoteDialog(text -> description.insertFootnote(insertionPoint, text)).open();
	}

	private Optional<LoeMaterialImage> findImageById(final String id) {
		return allImages().stream().filter(image -> image.id().equals(id)).findFirst();
	}

	private LoeMaterialImage prepareImage(final byte[] bytes) {
		final LoeMaterialImage processed = imageProcessor.process(nextImageId(), bytes);
		return pendingImages.values().stream().filter(image -> image.contentHash().equals(processed.contentHash()))
				.findFirst().or(() -> handler.findImageByContentHash(processed.contentHash())).orElse(processed);
	}

	private String nextImageId() {
		final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
		for (int attempt = 0; attempt < 100; attempt++) {
			final StringBuilder id = new StringBuilder(8);
			for (int index = 0; index < 8; index++) {
				id.append(alphabet.charAt(random.nextInt(alphabet.length())));
			}
			if (!handler.imageIdExists(id.toString()) && !pendingImages.containsKey(id.toString())) {
				return id.toString();
			}
		}
		throw new IllegalStateException("Für das Bild konnte keine freie Kennung erzeugt werden.");
	}

	private List<LoeMaterialImage> allImages() {
		final List<LoeMaterialImage> images = new ArrayList<>(storedImages);
		images.addAll(pendingImages.values());
		return images;
	}

	private static String declaration(final MaterialImageDialog.Result result) {
		final String alternativeText = result.description().replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
				.replace('\n', ' ').replace('\r', ' ');
		return "![" + alternativeText + "](" + LoeMaterialImageReferences.destination(result.image().id(),
				result.width(), result.alignment()) + ")";
	}

	private static boolean isSupportedMediaType(final String mediaType) {
		return "image/png".equals(mediaType) || "image/jpeg".equals(mediaType);
	}

	private static void uploadClear(final Upload upload) {
		upload.clearFileList();
	}

	private static String normalized(final String value) {
		return value == null ? "" : value;
	}

	private record ExistingImage(LoeMaterialImageReferences.Occurrence occurrence, LoeMaterialImage image) {
	}

	interface Handler extends LoeSectionHandler<LoeMaterial> {

		LoeMaterial save(LoeMaterial material, String title, String descriptionMarkdown,
				Collection<LoeMaterialImage> pendingImages, boolean dedicatedPage);

		Optional<LoeMaterialImage> findImageByContentHash(String contentHash);

		boolean imageIdExists(String id);
	}
}

