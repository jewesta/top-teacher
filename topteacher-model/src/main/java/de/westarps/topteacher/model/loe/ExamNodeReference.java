package de.westarps.topteacher.model.loe;

import org.jspecify.annotations.Nullable;

/** A persisted node ID or a key created earlier in the same authoring batch. */
public record ExamNodeReference(@Nullable Integer id, @Nullable String clientKey) {
}
