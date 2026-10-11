package de.westarps.topteacher.model.loe;

/** Content-free task metadata for navigating an exam. */
public record LoeTaskOutline(int id, int categoryId, String title, int maxPoints, int sortOrder) {
}
