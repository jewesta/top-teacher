package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.loe.LoeMaterial;
import de.westarps.topteacher.model.loe.LoeMaterialImage;
import de.westarps.topteacher.model.loe.LoeMaterialImageReferences;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.DesignerViewport;
import de.westarps.topteacher.ui.component.FullscreenButton;

public class ExamMaterialEditor extends AbstractDesigner {

	private final LevelOfExpectationsRepository repository;
	private final FullscreenButton fullscreenButton;
	private final LoeSaveController saveController = new LoeSaveController();
	private final LoeSectionComponents components = new LoeSectionComponents(saveController);
	private final LoeCollapseState collapseState = new LoeCollapseState(components);
	private final DesignerViewport viewport = new DesignerViewport(content());
	private final Span breadcrumb = new Span();
	private final LoeMaterialSection.Handler materialHandler = new LoeMaterialSection.Handler() {

		@Override
		public LoeMaterial save(final LoeMaterial material, final String title, final String descriptionMarkdown,
				final Collection<LoeMaterialImage> pendingImages) {
			return ExamMaterialEditor.this.save(material, title, descriptionMarkdown, pendingImages);
		}

		@Override
		public Optional<LoeMaterialImage> findImageByContentHash(final String contentHash) {
			return repository.findMaterialImageByContentHash(contentHash);
		}

		@Override
		public boolean imageIdExists(final String id) {
			return repository.materialImageIdExists(id);
		}

		@Override
		public void move(final LoeMaterial material, final int offset) {
			ExamMaterialEditor.this.move(material, offset);
		}

		@Override
		public void delete(final LoeMaterial material) {
			ExamMaterialEditor.this.delete(material);
		}
	};

	private Exam exam;
	private List<LoePart> parts = List.of();
	private List<LoeMaterial> materials = List.of();
	private List<LoeMaterialImage> materialImages = List.of();
	private List<LoeReadOnlySection<LoePart>> partSections = List.of();
	private boolean locked;
	private long loadedRevision;
	private Runnable changeHandler = () -> {
	};
	private Runnable structureChangeHandler = () -> {
	};

	public ExamMaterialEditor(final LevelOfExpectationsRepository repository) {
		super("tt-eh-editor");
		this.repository = repository;
		fullscreenButton = new FullscreenButton(this);
		saveController.setDirtySupplier(this::isDirty);
		saveController.setSaveAction(this::saveDirtySections);
		saveController.setDiscardAction(this::refresh);
	}

	public void setExam(final Exam exam) {
		if (isDirty() && isDifferentExam(exam)) {
			Notification.show("Speichere oder verwirf zuerst die ungespeicherten Materialien.");
			return;
		}
		if (this.exam == null || exam == null || !this.exam.id().equals(exam.id())) {
			collapseState.clear();
		}
		this.exam = exam;
		refresh();
	}

	private boolean isDifferentExam(final Exam exam) {
		return this.exam == null || exam == null || !this.exam.id().equals(exam.id());
	}

	public void setChangeHandler(final Runnable changeHandler) {
		this.changeHandler = changeHandler == null ? () -> {
		} : changeHandler;
	}

	public void setStructureChangeHandler(final Runnable structureChangeHandler) {
		this.structureChangeHandler = structureChangeHandler == null ? () -> {
		} : structureChangeHandler;
	}

	public boolean hasUnsavedChanges() {
		return isDirty();
	}

	private void refresh() {
		saveController.clearButtons();
		collapseState.clearRenderedComponents();
		partSections = List.of();
		materialImages = List.of();
		resetDesigner();
		if (exam == null) {
			showDesignerMessage(new Span("Bitte wähle eine Klausur aus."));
			return;
		}
		repository.withDesignRead(exam.id(), () -> {
			locked = repository.hasResultsForExam(exam.id());
			parts = repository.findPartsByExamId(exam.id());
			materials = repository.findMaterialsByExamId(exam.id());
			final List<LoeMaterialImage> loadedImages = repository.findMaterialImagesByIds(materials.stream()
					.flatMap(material -> LoeMaterialImageReferences.ids(material.descriptionMarkdown()).stream()).toList());
			materialImages = loadedImages == null ? List.of() : loadedImages;
			loadedRevision = repository.designRevision(exam.id());
			return null;
		});
		if (parts.isEmpty()) {
			content().add(emptyState("Noch keine Klausurteile angelegt."));
		} else {
			partSections = parts.stream().map(this::createPartSection).toList();
			partSections.forEach(content()::add);
		}
		configureToolbar();
		showDesigner();
		saveController.update();
	}

	private void configureToolbar() {
		final Button save = components.saveButton();
		final Button discard = components.discardButton();
		final Button reload = saveController.registerClean(
				components.iconButton("Neu laden", VaadinIcon.REFRESH, event -> viewport.refreshPreservingPosition(this::refresh)));
		final Button addPart = components.commandButton("Klausurteil hinzufügen", VaadinIcon.PLUS,
				event -> addPart());
		if (locked) {
			components.lockCorrectionModeAction(addPart);
		}
		breadcrumb.addClassName("tt-designer-breadcrumb");
		toolbar().add(addPart, save, discard, collapseState.toggleAllButton(allDetailKeys()), fullscreenButton,
				components.discardConfirmation(), reload);
		toolbarSummary().add(breadcrumb);
		toolbarSummary().expand(breadcrumb);
		viewport.bindBreadcrumb(breadcrumb, "Material", "Material");
	}

	private LoeReadOnlySection<LoePart> createPartSection(final LoePart part) {
		final List<LoeMaterialSection> materialSections = materialsFor(part).stream().map(this::createMaterialSection)
				.toList();
		final Button addMaterial = components.commandButton("Material hinzufügen", VaadinIcon.PLUS,
				event -> addMaterial(part));
		if (locked) {
			components.lockCorrectionModeAction(addMaterial);
		}
		final Component actions = components.editorBlock(addMaterial);
		final LoeReadOnlySection<LoePart> section = new LoeReadOnlySection<>(part, "tt-eh-part",
				components.summary("Klausurteil", components.summaryTitle(part.title())), null, List.of(actions),
				materialSections);
		collapseState.configure(section.getContent(), detailKey("part", part.id()), null, materialDetailKeys(part),
				"Klausurteil", part.title());
		DesignerViewport.mark(section, detailKey("part", part.id()), part.title());
		return section;
	}

	private void addPart() {
		runStructureChange(() -> {
			final int sortOrder = repository.nextPartSortOrder(exam.id());
			final LoePart part = repository
					.savePart(new LoePart(null, exam.id(), "Klausurteil " + partLetter(sortOrder), sortOrder));
			refresh();
			viewport.scrollTo(detailKey("part", part.id()));
			notifyStructureChanged();
		});
	}

	private LoeMaterialSection createMaterialSection(final LoeMaterial material) {
		final LoeMaterialSection section = new LoeMaterialSection(material, materialsFor(partFor(material)), components,
				materialHandler, imagesFor(material), locked);
		collapseState.configure(section.getContent(), detailKey("material", material.id()),
				detailKey("part", material.partId()), List.of(), "Material", material.title());
		DesignerViewport.mark(section, detailKey("material", material.id()), material.title());
		return section;
	}

	private void addMaterial(final LoePart part) {
		runStructureChange(() -> addMaterialNow(part));
	}

	private void addMaterialNow(final LoePart part) {
		final int sortOrder = repository.nextMaterialSortOrder(part.id());
		final LoeMaterial material = repository.saveMaterial(
				new LoeMaterial(null, part.id(), "Quelle " + (sortOrder + 1), "", sortOrder));
		refresh();
		viewport.scrollTo(detailKey("material", material.id()));
		notifyChanged();
	}

	private LoeMaterial save(final LoeMaterial material, final String title, final String descriptionMarkdown,
			final Collection<LoeMaterialImage> pendingImages) {
		final LoeMaterial updated = new LoeMaterial(material.id(), material.partId(), title, descriptionMarkdown,
				material.sortOrder());
		final LoeMaterial saved = repository.saveMaterialWithImages(updated, List.copyOf(pendingImages));
		materials = materials.stream().map(current -> current.id().equals(saved.id()) ? saved : current).toList();
		final List<LoeMaterialImage> loadedImages = repository.findMaterialImagesByIds(materials.stream()
				.flatMap(current -> LoeMaterialImageReferences.ids(current.descriptionMarkdown()).stream()).toList());
		materialImages = loadedImages == null ? List.of() : loadedImages;
		viewport.updateBreadcrumbSegment(detailKey("material", material.id()), title);
		return saved;
	}

	private void move(final LoeMaterial material, final int offset) {
		runStructureChange(() -> {
			repository.moveMaterial(material, offset);
			refresh();
			notifyChanged();
		});
	}

	private void delete(final LoeMaterial material) {
		runStructureChange(() -> {
			repository.deleteMaterial(material.id());
			refresh();
			notifyChanged();
		});
	}

	private void runStructureChange(final Runnable action) {
		if (isDirty()) {
			Notification.show("Speichere oder verwirf zuerst das ungespeicherte Material.");
			return;
		}
		try {
			repository.withDesignWrite(exam.id(), loadedRevision, () -> {
				action.run();
				return null;
			});
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
		}
	}

	private List<LoeMaterial> materialsFor(final LoePart part) {
		return materials.stream().filter(material -> material.partId().equals(part.id()))
				.sorted(Comparator.comparingInt(LoeMaterial::sortOrder).thenComparing(LoeMaterial::id)).toList();
	}

	private LoePart partFor(final LoeMaterial material) {
		return parts.stream().filter(part -> part.id().equals(material.partId())).findFirst().orElseThrow();
	}

	private List<LoeMaterialImage> imagesFor(final LoeMaterial material) {
		final var ids = LoeMaterialImageReferences.ids(material.descriptionMarkdown());
		return materialImages.stream().filter(image -> ids.contains(image.id())).toList();
	}

	private List<String> allDetailKeys() {
		final List<String> keys = new ArrayList<>();
		parts.forEach(part -> keys.add(detailKey("part", part.id())));
		materials.forEach(material -> keys.add(detailKey("material", material.id())));
		return keys;
	}

	private List<String> materialDetailKeys(final LoePart part) {
		return materialsFor(part).stream().map(material -> detailKey("material", material.id())).toList();
	}

	private String detailKey(final String type, final Integer id) {
		return LoeNavigationTarget.anchor(type, id);
	}

	private boolean isDirty() {
		return partSections.stream().anyMatch(LoeReadOnlySection::isDirty);
	}

	private void saveDirtySections() {
		try {
			repository.withDesignWrite(exam.id(), loadedRevision, () -> {
				for (final LoeReadOnlySection<LoePart> section : partSections) {
					if (!section.save()) {
						loadedRevision = repository.designRevision(exam.id());
						return null;
					}
				}
				loadedRevision = repository.designRevision(exam.id());
				return null;
			});
			notifyChanged();
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
		}
	}

	private void notifyChanged() {
		changeHandler.run();
	}

	private void notifyStructureChanged() {
		structureChangeHandler.run();
	}

	private static Component emptyState(final String text) {
		final Span state = new Span(text);
		state.addClassName("tt-empty-state");
		return state;
	}

	private static String partLetter(final int index) {
		return String.valueOf((char) ('A' + Math.min(index, 25)));
	}
}

