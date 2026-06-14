package de.westarps.topteacher.ui.component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.data.provider.ListDataProvider;

import de.westarps.topteacher.model.Pupil;

public class PupilAssignmentGrid extends VerticalLayout {

	private static final Comparator<AssignmentRow> ASSIGNMENT_ROW_ORDER = Comparator
			.comparing((final AssignmentRow row) -> row.pupil().surname(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.pupil().name(), String.CASE_INSENSITIVE_ORDER)
			.thenComparing(row -> row.pupil().id());

	private final QuickFilterField search = new QuickFilterField();
	private final HorizontalLayout toolbar = new HorizontalLayout(search);
	private final Grid<AssignmentRow> grid = new Grid<>(AssignmentRow.class, false);

	private ListDataProvider<AssignmentRow> dataProvider = DataProvider.ofCollection(List.of());
	private Consumer<Pupil> assignAction = pupil -> {
	};
	private Consumer<Pupil> removeAction = pupil -> {
	};

	public PupilAssignmentGrid(final String searchPlaceholder) {
		search.setPlaceholder(searchPlaceholder);
		search.addValueChangeListener(event -> applyFilter());

		toolbar.addClassNames("tt-course-assignment-toolbar", "tt-pupil-assignment-toolbar");
		toolbar.setAlignItems(Alignment.END);
		toolbar.setPadding(false);
		toolbar.setSpacing(true);
		toolbar.setWidthFull();

		grid.addColumn(row -> row.pupil().id()).setHeader("ID").setAutoWidth(true).setFlexGrow(0);
		grid.addColumn(row -> row.pupil().surname()).setHeader("Nachname").setAutoWidth(true);
		grid.addColumn(row -> row.pupil().name()).setHeader("Vorname").setAutoWidth(true);
		grid.addComponentColumn(this::createAssignmentCheckbox).setHeader(createAssignmentHeader()).setAutoWidth(true)
				.setFlexGrow(0).setFrozenToEnd(true).setTextAlign(ColumnTextAlign.CENTER);
		grid.addClassName("tt-assignment-grid");
		grid.setItems(dataProvider);
		grid.setSelectionMode(Grid.SelectionMode.NONE);
		grid.setSizeFull();

		addClassNames("tt-course-assignments", "tt-pupil-assignments");
		setPadding(false);
		setSpacing(false);
		setSizeFull();
		add(toolbar, grid);
		expand(grid);
	}

	public void addToolbarComponent(final Component component) {
		toolbar.add(component);
	}

	public void setAssignAction(final Consumer<Pupil> assignAction) {
		this.assignAction = assignAction == null ? pupil -> {
		} : assignAction;
	}

	public void setRemoveAction(final Consumer<Pupil> removeAction) {
		this.removeAction = removeAction == null ? pupil -> {
		} : removeAction;
	}

	public void setRows(final List<Pupil> assignedPupils, final List<Pupil> assignablePupils) {
		setRows(assignedPupils, assignablePupils, Map.of());
	}

	public void setRows(final List<Pupil> assignedPupils, final List<Pupil> assignablePupils,
			final Map<Integer, String> lockedReasonsByPupilId) {
		setRows(Stream
				.concat(assignedPupils.stream().map(pupil -> new AssignmentRow(pupil, true)),
						assignablePupils.stream().map(pupil -> new AssignmentRow(pupil, false)))
				.map(row -> row.withLockedReason(lockedReasonsByPupilId.get(row.pupil().id())))
				.sorted(ASSIGNMENT_ROW_ORDER).toList());
	}

	public void clearSearch() {
		search.clear();
	}

	private Component createAssignmentHeader() {
		final Icon icon = VaadinIcon.INFO_CIRCLE_O.create();
		icon.addClassName("tt-assignment-header-icon");
		icon.getElement().setAttribute("aria-label", "Zugeordnet");
		Tooltip.forComponent(icon).withText("Zugeordnet");
		return icon;
	}

	private Component createAssignmentCheckbox(final AssignmentRow row) {
		final Checkbox checkbox = new Checkbox(row.assigned());
		checkbox.setAriaLabel(row.assigned() ? "Schüler:in entfernen" : "Schüler:in zuordnen");
		checkbox.getElement().setAttribute("title", row.assigned() ? "Schüler:in entfernen" : "Schüler:in zuordnen");
		if (row.locked()) {
			checkbox.setEnabled(false);
			checkbox.getElement().removeAttribute("title");
			return createLockedCheckboxWrapper(checkbox, row.lockedReason());
		}
		checkbox.addValueChangeListener(event -> {
			if (!event.isFromClient()) {
				return;
			}

			if (event.getValue()) {
				assignAction.accept(row.pupil());
			} else {
				removeAction.accept(row.pupil());
			}
		});
		return checkbox;
	}

	private Component createLockedCheckboxWrapper(final Checkbox checkbox, final String lockedReason) {
		final HorizontalLayout wrapper = new HorizontalLayout(checkbox);
		wrapper.addClassName("tt-assignment-checkbox-lock");
		wrapper.setAlignItems(Alignment.CENTER);
		wrapper.setJustifyContentMode(JustifyContentMode.CENTER);
		wrapper.setMargin(false);
		wrapper.setPadding(false);
		wrapper.setSpacing(false);
		Tooltip.forComponent(wrapper).withText(lockedReason);
		return wrapper;
	}

	private void setRows(final List<AssignmentRow> rows) {
		dataProvider = DataProvider.ofCollection(rows);
		grid.setItems(dataProvider);
		applyFilter();
	}

	private void applyFilter() {
		final String searchValue = search.getValue().trim().toLowerCase();
		dataProvider.setFilter(row -> matchesFilter(row, searchValue));
	}

	private boolean matchesFilter(final AssignmentRow row, final String searchValue) {
		if (searchValue.isBlank()) {
			return row.assigned();
		}

		final String pupilText = String
				.join(" ", String.valueOf(row.pupil().id()), row.pupil().surname(), row.pupil().name()).toLowerCase();
		return pupilText.contains(searchValue);
	}

	private record AssignmentRow(Pupil pupil, boolean assigned, String lockedReason) {

		private AssignmentRow(final Pupil pupil, final boolean assigned) {
			this(pupil, assigned, null);
		}

		private boolean locked() {
			return lockedReason != null && !lockedReason.isBlank();
		}

		private AssignmentRow withLockedReason(final String lockedReason) {
			return new AssignmentRow(pupil, assigned, lockedReason);
		}
	}
}
