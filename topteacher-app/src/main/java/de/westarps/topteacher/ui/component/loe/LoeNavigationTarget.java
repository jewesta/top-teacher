package de.westarps.topteacher.ui.component.loe;

public record LoeNavigationTarget(int partId, int categoryId, int taskId, int requirementId) {

	public LoeNavigationTarget {
		requirePositive(partId, "partId");
		requirePositive(categoryId, "categoryId");
		requirePositive(taskId, "taskId");
		requirePositive(requirementId, "requirementId");
	}

	String partAnchor() {
		return anchor("part", partId);
	}

	String categoryAnchor() {
		return anchor("category", categoryId);
	}

	String taskAnchor() {
		return anchor("task", taskId);
	}

	String requirementAnchor() {
		return anchor("requirement", requirementId);
	}

	static String anchor(final String type, final int id) {
		return type + ":" + id;
	}

	private static void requirePositive(final int id, final String name) {
		if (id <= 0) {
			throw new IllegalArgumentException(name + " must be positive");
		}
	}
}
