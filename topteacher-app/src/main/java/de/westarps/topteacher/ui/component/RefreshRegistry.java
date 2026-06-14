package de.westarps.topteacher.ui.component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class RefreshRegistry<S extends Enum<S>, T extends Enum<T>> {

	private final Class<T> targetType;
	private final Supplier<T> activeTargetSupplier;
	private final Map<S, EnumSet<T>> dependencies;
	private final Map<T, Runnable> refreshActions;
	private final EnumSet<T> eagerTargets;
	private final EnumSet<T> staleTargets;

	public RefreshRegistry(final Class<S> sourceType, final Class<T> targetType,
			final Supplier<T> activeTargetSupplier) {
		this.targetType = Objects.requireNonNull(targetType, "Target type can not be null");
		this.activeTargetSupplier = Objects.requireNonNull(activeTargetSupplier,
				"Active target supplier can not be null");
		this.dependencies = new EnumMap<>(Objects.requireNonNull(sourceType, "Source type can not be null"));
		this.refreshActions = new EnumMap<>(targetType);
		this.eagerTargets = EnumSet.noneOf(targetType);
		this.staleTargets = EnumSet.noneOf(targetType);
	}

	public void registerTarget(final T target, final Runnable refreshAction) {
		refreshActions.put(Objects.requireNonNull(target, "Target can not be null"),
				Objects.requireNonNull(refreshAction, "Refresh action can not be null"));
	}

	public void registerEagerTarget(final T target, final Runnable refreshAction) {
		registerTarget(target, refreshAction);
		eagerTargets.add(target);
	}

	public void registerDependency(final S source, final T target) {
		dependencies.computeIfAbsent(Objects.requireNonNull(source, "Source can not be null"),
				key -> EnumSet.noneOf(targetType)).add(Objects.requireNonNull(target, "Target can not be null"));
	}

	public void publish(final S source) {
		final EnumSet<T> affectedTargets = dependencies.getOrDefault(Objects.requireNonNull(source,
				"Source can not be null"), EnumSet.noneOf(targetType));
		for (final T target : affectedTargets) {
			staleTargets.add(target);
			if (eagerTargets.contains(target) || target == activeTargetSupplier.get()) {
				refreshIfStale(target);
			}
		}
	}

	public void refreshIfStale(final T target) {
		if (staleTargets.contains(Objects.requireNonNull(target, "Target can not be null"))) {
			refresh(target);
		}
	}

	public void refresh(final T target) {
		final T nonNullTarget = Objects.requireNonNull(target, "Target can not be null");
		final Runnable refreshAction = refreshActions.get(nonNullTarget);
		if (refreshAction == null) {
			throw new IllegalStateException("No refresh action registered for target: " + target);
		}
		refreshAction.run();
		staleTargets.remove(nonNullTarget);
	}

	public void clear() {
		staleTargets.clear();
	}

	boolean isStale(final T target) {
		return staleTargets.contains(Objects.requireNonNull(target, "Target can not be null"));
	}
}
