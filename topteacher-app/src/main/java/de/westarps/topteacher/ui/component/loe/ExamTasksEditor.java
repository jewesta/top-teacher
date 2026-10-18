package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Pupil;
import de.westarps.topteacher.model.loe.LoeCategory;
import de.westarps.topteacher.model.loe.LoePart;
import de.westarps.topteacher.model.loe.LoeTask;
import de.westarps.topteacher.ui.UiUrls;
import de.westarps.topteacher.ui.component.AbstractDesigner;
import de.westarps.topteacher.ui.component.DesignerViewport;
import de.westarps.topteacher.ui.component.FullscreenButton;
import de.westarps.vaadin.markdown.MarkdownEditor;

public class ExamTasksEditor extends AbstractDesigner {

	private final LevelOfExpectationsRepository repository;
	private final FullscreenButton fullscreenButton;
	private final LoeSaveController saveController = new LoeSaveController();
	private final LoeSectionComponents components = new LoeSectionComponents(saveController);
	private final LoeCollapseState collapseState = new LoeCollapseState(components);
	private final DesignerViewport viewport = new DesignerViewport(content());
	private final Span breadcrumb = new Span();
	private final LoePartSection.Handler partHandler = new LoePartSection.Handler() {

		@Override
		public void saveTitle(final LoePart part, final String title) {
			ExamTasksEditor.this.saveTitle(part, title);
		}

		@Override
		public void addCategory(final LoePart part) {
			ExamTasksEditor.this.addCategory(part);
		}

		@Override
		public void move(final LoePart part, final int offset) {
			ExamTasksEditor.this.move(part, offset);
		}

		@Override
		public void delete(final LoePart part) {
			ExamTasksEditor.this.delete(part);
		}
	};
	private final LoeCategorySection.Handler categoryHandler = new LoeCategorySection.Handler() {

		@Override
		public void saveTitle(final LoeCategory category, final String title) {
			ExamTasksEditor.this.save(category, title, category.descriptionMarkdown(), category.printTasksOnExamSheet());
		}

		@Override
		public void save(final LoeCategory category, final String title, final String descriptionMarkdown,
				final boolean printTasksOnExamSheet) {
			ExamTasksEditor.this.save(category, title, descriptionMarkdown, printTasksOnExamSheet);
		}

		@Override
		public void addTask(final LoeCategory category) {
			ExamTasksEditor.this.addTask(category);
		}

		@Override
		public void move(final LoeCategory category, final int offset) {
			ExamTasksEditor.this.move(category, offset);
		}

		@Override
		public void delete(final LoeCategory category) {
			ExamTasksEditor.this.delete(category);
		}
	};
	private final ExamTaskSection.Handler taskHandler = new ExamTaskSection.Handler() {

		@Override
		public void save(final LoeTask task, final String title, final String descriptionMarkdown,
				final int maxPoints) {
			ExamTasksEditor.this.save(task, title, descriptionMarkdown, maxPoints);
		}

		@Override
		public void move(final LoeTask task, final int offset) {
			ExamTasksEditor.this.move(task, offset);
		}

		@Override
		public void delete(final LoeTask task) {
			ExamTasksEditor.this.delete(task);
		}
	};

	private Exam exam;
	private List<LoePart> parts = List.of();
	private List<LoeCategory> categories = List.of();
	private List<LoeTask> tasks = List.of();
	private List<LoePartSection> partSections = List.of();
	private List<ExamTaskSection> taskSections = List.of();
	private LoePointBadge examPointsBadge;
	private MarkdownEditor footerEditor;
	private String savedFooterMarkdown = "";
	private boolean locked;
	private IntFunction<List<Pupil>> pupilSupplier = id -> List.of();
	private BooleanSupplier exportReady = () -> true;
	private long loadedRevision;
	private Runnable changeHandler = () -> {
	};

	public ExamTasksEditor(final LevelOfExpectationsRepository repository) {
		super("tt-eh-editor");
		addClassName("tt-exam-tasks-editor");
		this.repository = repository;
		fullscreenButton = new FullscreenButton(this);
		components.setPointsChangeHandler(this::refreshBadges);
		saveController.setDirtySupplier(this::isDirty);
		saveController.setSaveAction(this::saveDirtySections);
		saveController.setDiscardAction(this::refresh);
	}

	public void setExam(final Exam exam) {
		if (isDirty() && isDifferentExam(exam)) {
			Notification.show("Speichere oder verwirf zuerst die ungespeicherten Aufgaben.");
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

	public void configureTaskSheetExport(final IntFunction<List<Pupil>> pupilSupplier, final BooleanSupplier exportReady) {
		this.pupilSupplier = pupilSupplier;
		this.exportReady = exportReady;
	}

	private MenuBar taskSheetDownload() {
		final MenuBar menu = new MenuBar();
		menu.addClassName("tt-pdf-menu");
		menu.addThemeVariants(MenuBarVariant.LUMO_SMALL);
		final var item = menu.addItem("");
		item.addComponentAsFirst(VaadinIcon.DOWNLOAD.create());
		item.setAriaLabel("Aufgabenblatt herunterladen");
		menu.setTooltipText(item, "Aufgabenblatt herunterladen");
		item.getSubMenu().addItem("Anonym", event -> downloadTaskSheet("task-sheet.pdf"));
		item.getSubMenu().addItem("Personalisiert...", event -> openPersonalizedDownload());
		return menu;
	}

	private boolean canExportTaskSheet() {
		if (exam == null || isDirty() || !exportReady.getAsBoolean()) {
			Notification.show("Speichere oder verwirf zuerst die ungespeicherten Aufgaben und Materialien.");
			return false;
		}
		return true;
	}

	private void openPersonalizedDownload() {
		if (!canExportTaskSheet()) {
			return;
		}
		final var pupils = pupilSupplier.apply(exam.id());
		if (pupils.isEmpty()) {
			Notification.show("Ordne der Klausur zuerst Schüler:innen zu.");
			return;
		}
		final Dialog dialog = new Dialog();
		dialog.setHeaderTitle("Personalisiertes Aufgabenblatt");
		final Checkbox all = new Checkbox("Alle zugeordneten Schüler:innen", true);
		final ComboBox<Pupil> pupil = new ComboBox<>("Schüler:in");
		pupil.setItemLabelGenerator(value -> value.name() + " " + value.surname());
		pupil.setItems(pupils);
		pupil.setValue(pupils.getFirst());
		pupil.setEnabled(false);
		pupil.setWidthFull();
		all.addValueChangeListener(event -> pupil.setEnabled(!event.getValue()));
		dialog.add(new VerticalLayout(all, pupil));
		dialog.getFooter().add(new Button("Abbrechen", event -> dialog.close()), new Button("Herunterladen", event -> {
			if (!all.getValue() && pupil.getValue() == null) {
				pupil.setInvalid(true);
				pupil.setErrorMessage("Bitte wähle eine Schülerin oder einen Schüler.");
				return;
			}
			downloadTaskSheet(all.getValue() ? "task-sheets.pdf" : "pupils/" + pupil.getValue().id() + "/task-sheet.pdf");
			dialog.close();
		}));
		dialog.addClosedListener(event -> dialog.getElement().removeFromParent());
		if (UI.getCurrent() != null) {
			UI.getCurrent().add(dialog);
		}
		dialog.open();
	}

	private void downloadTaskSheet(final String path) {
		if (!canExportTaskSheet()) {
			return;
		}
		getUI().ifPresent(ui -> ui.getPage().executeJs("""
			const anchor = document.createElement('a');
			anchor.href = $0;
			anchor.download = '';
			document.body.appendChild(anchor);
			anchor.click();
			anchor.remove();
			""", UiUrls.contextRelative("/export/exams/" + exam.id() + "/" + path)));
	}

	public boolean hasUnsavedChanges() {
		return isDirty();
	}

	private void refresh() {
		saveController.clearButtons();
		collapseState.clearRenderedComponents();
		partSections = List.of();
		taskSections = new ArrayList<>();
		examPointsBadge = null;
		footerEditor = null;
		savedFooterMarkdown = "";
		resetDesigner();
		if (exam == null) {
			showDesignerMessage(new Span("Bitte wähle eine Klausur aus."));
			return;
		}
		repository.withDesignRead(exam.id(), () -> {
			locked = repository.hasResultsForExam(exam.id());
			loadItems();
			if (!locked && ensureRequiredChildren()) {
				loadItems();
			}
			loadedRevision = repository.designRevision(exam.id());
			return null;
		});
		if (parts.isEmpty()) {
			content().add(emptyState("Noch keine Aufgaben angelegt."));
		} else {
			partSections = parts.stream().map(this::createPartSection).toList();
			partSections.forEach(content()::add);
		}
		content().add(createFooterBlock());
		configureToolbar();
		showDesigner();
		saveController.update();
	}

	private void loadItems() {
		parts = repository.findPartsByExamId(exam.id());
		categories = repository.findCategoriesByExamId(exam.id());
		tasks = repository.findTasksByExamId(exam.id());
		final String footerMarkdown = repository.findExamFooterMarkdown(exam.id());
		savedFooterMarkdown = footerMarkdown == null ? "" : footerMarkdown;
	}

	private Component createFooterBlock() {
		final Component block;
		if (locked) {
			block = components.markdownViewBlock("Abschlusstext", savedFooterMarkdown);
		} else {
			footerEditor = components.markdownEditor(savedFooterMarkdown,
					"Dieser Text erscheint einmal am Ende der Klausur, außerhalb der Klausurteile.");
			components.trackDirty(footerEditor);
			block = components.markdownBlock("Abschlusstext", footerEditor);
		}
		block.addClassName("tt-exam-footer");
		DesignerViewport.mark(block, "exam-footer", "Abschlusstext");
		return block;
	}

	private boolean ensureRequiredChildren() {
		boolean changed = false;
		for (final LoePart part : parts) {
			if (categoriesFor(part).isEmpty()) {
				addDefaultCategory(part);
				changed = true;
			}
		}
		for (final LoeCategory category : categories) {
			if (tasksFor(category).isEmpty()) {
				addDefaultTask(category);
				changed = true;
			}
		}
		return changed;
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
		examPointsBadge = components.pointBadge("Gesamt", () -> new LoePoints(totalTaskPoints(), 0));
		breadcrumb.addClassName("tt-designer-breadcrumb");
		toolbar().add(addPart, save, discard, collapseState.toggleAllButton(allDetailKeys()), fullscreenButton,
				components.discardConfirmation(), taskSheetDownload(), reload);
		toolbarSummary().add(breadcrumb, examPointsBadge);
		toolbarSummary().expand(breadcrumb);
		viewport.bindBreadcrumb(breadcrumb, "Aufgaben", "Aufgaben");
	}

	private LoePartSection createPartSection(final LoePart part) {
		final List<LoeCategorySection> categorySections = categoriesFor(part).stream().map(this::createCategorySection)
				.toList();
		final LoePartSection section = new LoePartSection(part, parts, categorySections, components,
				partHandler, () -> percentageForPart(part), () -> new LoePoints(pointsForPart(part), 0), locked);
		collapseState.configure(section.getContent(), detailKey("part", part.id()), null,
				partDescendantDetailKeys(part), "Klausurteil", part.title());
		DesignerViewport.mark(section, detailKey("part", part.id()), part.title());
		return section;
	}

	private LoeCategorySection createCategorySection(final LoeCategory category) {
		final List<ExamTaskSection> taskSections = tasksFor(category).stream().map(this::createTaskSection).toList();
		final LoeCategorySection section = new LoeCategorySection(category, categoriesFor(partFor(category)),
				taskSections, components, categoryHandler,
				() -> new LoePoints(pointsForCategory(category), 0), locked);
		collapseState.configure(section.getContent(), detailKey("category", category.id()),
				detailKey("part", category.partId()), categoryDescendantDetailKeys(category),
				"Leistungskategorie", category.title());
		DesignerViewport.mark(section, detailKey("category", category.id()), category.title());
		return section;
	}

	private ExamTaskSection createTaskSection(final LoeTask task) {
		final ExamTaskSection section = new ExamTaskSection(task, tasksFor(categoryFor(task)), components, taskHandler,
				locked);
		taskSections.add(section);
		collapseState.configure(section.getContent(), detailKey("task", task.id()),
				detailKey("category", task.categoryId()), List.of(), "Aufgabe", task.title());
		DesignerViewport.mark(section, detailKey("task", task.id()), task.title());
		return section;
	}

	private LoeCategory addDefaultCategory(final LoePart part) {
		final LoeCategory category = repository.saveCategory(new LoeCategory(null, part.id(), "Leistungskategorie", "",
				repository.nextCategorySortOrder(part.id())));
		addDefaultTask(category);
		return category;
	}

	private LoeTask addDefaultTask(final LoeCategory category) {
		final int sortOrder = repository.nextTaskSortOrder(category.id());
		return repository.saveTask(new LoeTask(null, category.id(), "Aufgabe " + (sortOrder + 1), "", 0, sortOrder));
	}

	private void saveTitle(final LoePart part, final String title) {
		final LoePart updated = new LoePart(part.id(), part.examId(), title, part.sortOrder());
		repository.savePart(updated);
		parts = parts.stream().map(current -> current.id().equals(updated.id()) ? updated : current).toList();
		viewport.updateBreadcrumbSegment(detailKey("part", part.id()), title);
	}

	private void save(final LoeCategory category, final String title, final String descriptionMarkdown,
			final boolean printTasksOnExamSheet) {
		final LoeCategory updated = new LoeCategory(category.id(), category.partId(), title, descriptionMarkdown,
				category.sortOrder(), printTasksOnExamSheet);
		repository.saveCategory(updated);
		categories = categories.stream().map(current -> current.id().equals(updated.id()) ? updated : current).toList();
		viewport.updateBreadcrumbSegment(detailKey("category", category.id()), title);
	}

	private void save(final LoeTask task, final String title, final String descriptionMarkdown, final int maxPoints) {
		final LoeTask updated = new LoeTask(task.id(), task.categoryId(), title, descriptionMarkdown, maxPoints,
				task.sortOrder());
		repository.saveTask(updated);
		tasks = tasks.stream().map(current -> current.id().equals(updated.id()) ? updated : current).toList();
		viewport.updateBreadcrumbSegment(detailKey("task", task.id()), title);
	}

	private void addPart() {
		runStructureChange(() -> {
			final int sortOrder = repository.nextPartSortOrder(exam.id());
			final LoePart part = repository
					.savePart(new LoePart(null, exam.id(), "Klausurteil " + partLetter(sortOrder), sortOrder));
			addDefaultCategory(part);
			refreshAndScrollTo(detailKey("part", part.id()));
			notifyChanged();
		});
	}

	private void addCategory(final LoePart part) {
		runStructureChange(() -> {
			final LoeCategory category = addDefaultCategory(part);
			refreshAndScrollTo(detailKey("category", category.id()));
			notifyChanged();
		});
	}

	private void addTask(final LoeCategory category) {
		runStructureChange(() -> {
			final LoeTask task = addDefaultTask(category);
			refreshAndScrollTo(detailKey("task", task.id()));
			notifyChanged();
		});
	}

	private void move(final LoePart part, final int offset) {
		runStructureChange(() -> {
			repository.movePart(part, offset);
			refresh();
			notifyChanged();
		});
	}

	private void move(final LoeCategory category, final int offset) {
		runStructureChange(() -> {
			repository.moveCategory(category, categoriesFor(partFor(category)), offset);
			refresh();
			notifyChanged();
		});
	}

	private void move(final LoeTask task, final int offset) {
		runStructureChange(() -> {
			repository.moveTask(task, tasksFor(categoryFor(task)), offset);
			refresh();
			notifyChanged();
		});
	}

	private void delete(final LoePart part) {
		deleteSafely(() -> repository.deletePart(part.id()));
	}

	private void delete(final LoeCategory category) {
		deleteSafely(() -> repository.deleteCategory(category.id()));
	}

	private void delete(final LoeTask task) {
		deleteSafely(() -> repository.deleteTask(task.id()));
	}

	private void deleteSafely(final Runnable action) {
		runStructureChange(() -> {
			action.run();
			refresh();
			notifyChanged();
		});
	}

	private void runStructureChange(final Runnable action) {
		if (isDirty()) {
			Notification.show("Speichere oder verwirf zuerst die ungespeicherten Aufgaben.");
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

	private List<LoeCategory> categoriesFor(final LoePart part) {
		return categories.stream().filter(category -> category.partId().equals(part.id()))
				.sorted(Comparator.comparingInt(LoeCategory::sortOrder).thenComparing(LoeCategory::id)).toList();
	}

	private List<LoeTask> tasksFor(final LoeCategory category) {
		return tasks.stream().filter(task -> task.categoryId().equals(category.id()))
				.sorted(Comparator.comparingInt(LoeTask::sortOrder).thenComparing(LoeTask::id)).toList();
	}

	private LoePart partFor(final LoeCategory category) {
		return parts.stream().filter(part -> part.id().equals(category.partId())).findFirst().orElseThrow();
	}

	private LoeCategory categoryFor(final LoeTask task) {
		return categories.stream().filter(category -> category.id().equals(task.categoryId())).findFirst().orElseThrow();
	}

	private int totalTaskPoints() {
		return tasks.stream().mapToInt(this::pendingMaxPoints).sum();
	}

	private int pointsForPart(final LoePart part) {
		return categoriesFor(part).stream().flatMap(category -> tasksFor(category).stream())
				.mapToInt(this::pendingMaxPoints).sum();
	}

	private int pointsForCategory(final LoeCategory category) {
		return tasksFor(category).stream().mapToInt(this::pendingMaxPoints).sum();
	}

	private int pendingMaxPoints(final LoeTask task) {
		return taskSections.stream().filter(section -> section.task().id().equals(task.id()))
				.mapToInt(ExamTaskSection::pendingMaxPoints).findFirst().orElse(task.maxPoints());
	}

	private int percentageForPart(final LoePart part) {
		return totalTaskPoints() == 0 ? 0 : Math.round(pointsForPart(part) * 100.0f / totalTaskPoints());
	}

	private List<String> allDetailKeys() {
		final List<String> keys = new ArrayList<>();
		parts.forEach(part -> keys.add(detailKey("part", part.id())));
		categories.forEach(category -> keys.add(detailKey("category", category.id())));
		tasks.forEach(task -> keys.add(detailKey("task", task.id())));
		return keys;
	}

	private List<String> partDescendantDetailKeys(final LoePart part) {
		final List<String> keys = new ArrayList<>();
		for (final LoeCategory category : categoriesFor(part)) {
			keys.add(detailKey("category", category.id()));
			tasksFor(category).forEach(task -> keys.add(detailKey("task", task.id())));
		}
		return keys;
	}

	private List<String> categoryDescendantDetailKeys(final LoeCategory category) {
		return tasksFor(category).stream().map(task -> detailKey("task", task.id())).toList();
	}

	private String detailKey(final String type, final Integer id) {
		return LoeNavigationTarget.anchor(type, id);
	}

	private void saveDirtySections() {
		try {
			repository.withDesignWrite(exam.id(), loadedRevision, () -> {
				for (final LoePartSection partSection : partSections) {
					if (!partSection.save()) {
						loadedRevision = repository.designRevision(exam.id());
						return null;
					}
				}
				if (isFooterDirty()) {
					repository.saveExamFooterMarkdown(exam.id(), footerEditor.getValue());
					savedFooterMarkdown = footerEditor.getValue();
				}
				loadedRevision = repository.designRevision(exam.id());
				return null;
			});
			refreshBadges();
			notifyChanged();
		} catch (final IllegalStateException exception) {
			Notification.show(exception.getMessage());
		}
	}

	private boolean isDirty() {
		return isFooterDirty() || partSections.stream().anyMatch(LoePartSection::isDirty);
	}

	private boolean isFooterDirty() {
		return footerEditor != null && !savedFooterMarkdown.equals(footerEditor.getValue());
	}

	private void refreshBadges() {
		if (examPointsBadge != null) {
			examPointsBadge.refreshBadges();
		}
		partSections.forEach(LoePartSection::refreshBadges);
	}

	private void refreshAndScrollTo(final String key) {
		refresh();
		viewport.scrollTo(key);
	}

	private void notifyChanged() {
		changeHandler.run();
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

