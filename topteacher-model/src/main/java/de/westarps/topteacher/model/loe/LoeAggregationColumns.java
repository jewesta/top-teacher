package de.westarps.topteacher.model.loe;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LoeAggregationColumns {

	private LoeAggregationColumns() {
	}

	public static List<Column> from(final List<LoePart> parts, final List<LoeCategory> categories,
			final List<LoeTask> tasks, final List<LoeRequirement> requirements) {
		final List<Column> columns = new ArrayList<>();
		sortedParts(parts).forEach(part -> {
			final List<LoeRequirement> partRequirements = categoriesFor(part, categories).stream()
					.flatMap(category -> tasksFor(category, tasks).stream())
					.flatMap(task -> requirementsFor(task, requirements).stream()).toList();
			columns.add(new Column(part.title(), partRequirements));

			categoriesFor(part, categories).forEach(category -> {
				final List<LoeRequirement> categoryRequirements = tasksFor(category, tasks).stream()
						.flatMap(task -> requirementsFor(task, requirements).stream()).toList();
				columns.add(new Column(category.title(), categoryRequirements));

				tasksFor(category, tasks).forEach(task -> columns.add(new Column(task.title(),
						requirementsFor(task, requirements))));
			});
		});
		return collapseRedundantColumns(columns, requirements);
	}

	private static List<Column> collapseRedundantColumns(final List<Column> columns,
			final List<LoeRequirement> allRequirements) {
		final List<Column> collapsedColumns = new ArrayList<>();
		columns.forEach(column -> {
			if (sameRequirements(column.requirements(), allRequirements)) {
				return;
			}
			if (!collapsedColumns.isEmpty()
					&& sameRequirements(collapsedColumns.getLast().requirements(), column.requirements())) {
				return;
			}
			collapsedColumns.add(column);
		});
		return collapsedColumns;
	}

	private static List<LoePart> sortedParts(final List<LoePart> parts) {
		return parts.stream().sorted(Comparator.comparingInt(LoePart::sortOrder).thenComparing(LoePart::id))
				.toList();
	}

	private static List<LoeCategory> categoriesFor(final LoePart part, final List<LoeCategory> categories) {
		return categories.stream().filter(category -> category.partId().equals(part.id()))
				.sorted(Comparator.comparingInt(LoeCategory::sortOrder).thenComparing(LoeCategory::id)).toList();
	}

	private static List<LoeTask> tasksFor(final LoeCategory category, final List<LoeTask> tasks) {
		return tasks.stream().filter(task -> task.categoryId().equals(category.id()))
				.sorted(Comparator.comparingInt(LoeTask::sortOrder).thenComparing(LoeTask::id)).toList();
	}

	private static List<LoeRequirement> requirementsFor(final LoeTask task,
			final List<LoeRequirement> requirements) {
		return requirements.stream().filter(requirement -> requirement.taskId().equals(task.id()))
				.sorted(Comparator.comparingInt(LoeRequirement::sortOrder).thenComparing(LoeRequirement::id)).toList();
	}

	private static boolean sameRequirements(final List<LoeRequirement> left, final List<LoeRequirement> right) {
		return requirementIds(left).equals(requirementIds(right));
	}

	private static List<Integer> requirementIds(final List<LoeRequirement> requirements) {
		return requirements.stream().map(LoeRequirement::id).sorted().toList();
	}

	public record Column(String title, List<LoeRequirement> requirements) {

		public Column {
			requirements = List.copyOf(requirements);
		}
	}
}
