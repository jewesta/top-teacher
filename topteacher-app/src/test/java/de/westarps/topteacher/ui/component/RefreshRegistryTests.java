package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class RefreshRegistryTests {

	private enum Change {
		LEVEL_OF_EXPECTATIONS, RESULTS
	}

	private enum Target {
		STATUS, LEVEL_OF_EXPECTATIONS, RESULTS, EVALUATION
	}

	@Test
	void refreshesEagerAndActiveTargetsWhileMarkingInactiveTargetsStale() {
		final AtomicReference<Target> activeTarget = new AtomicReference<>(Target.RESULTS);
		final AtomicInteger statusRefreshes = new AtomicInteger();
		final AtomicInteger levelOfExpectationsRefreshes = new AtomicInteger();
		final AtomicInteger evaluationRefreshes = new AtomicInteger();
		final RefreshRegistry<Change, Target> registry = new RefreshRegistry<>(Change.class, Target.class,
				activeTarget::get);
		registry.registerEagerTarget(Target.STATUS, statusRefreshes::incrementAndGet);
		registry.registerTarget(Target.LEVEL_OF_EXPECTATIONS, levelOfExpectationsRefreshes::incrementAndGet);
		registry.registerTarget(Target.EVALUATION, evaluationRefreshes::incrementAndGet);
		registry.registerDependency(Change.RESULTS, Target.STATUS);
		registry.registerDependency(Change.RESULTS, Target.LEVEL_OF_EXPECTATIONS);
		registry.registerDependency(Change.RESULTS, Target.EVALUATION);

		registry.publish(Change.RESULTS);

		assertThat(statusRefreshes).hasValue(1);
		assertThat(levelOfExpectationsRefreshes).hasValue(0);
		assertThat(evaluationRefreshes).hasValue(0);
		assertThat(registry.isStale(Target.STATUS)).isFalse();
		assertThat(registry.isStale(Target.LEVEL_OF_EXPECTATIONS)).isTrue();
		assertThat(registry.isStale(Target.EVALUATION)).isTrue();

		activeTarget.set(Target.EVALUATION);
		registry.refreshIfStale(Target.EVALUATION);

		assertThat(evaluationRefreshes).hasValue(1);
		assertThat(registry.isStale(Target.EVALUATION)).isFalse();
		assertThat(registry.isStale(Target.LEVEL_OF_EXPECTATIONS)).isTrue();
	}

	@Test
	void refreshesDependentTargetImmediatelyWhenItIsActive() {
		final AtomicReference<Target> activeTarget = new AtomicReference<>(Target.RESULTS);
		final AtomicInteger resultsRefreshes = new AtomicInteger();
		final RefreshRegistry<Change, Target> registry = new RefreshRegistry<>(Change.class, Target.class,
				activeTarget::get);
		registry.registerTarget(Target.RESULTS, resultsRefreshes::incrementAndGet);
		registry.registerDependency(Change.LEVEL_OF_EXPECTATIONS, Target.RESULTS);

		registry.publish(Change.LEVEL_OF_EXPECTATIONS);

		assertThat(resultsRefreshes).hasValue(1);
		assertThat(registry.isStale(Target.RESULTS)).isFalse();
	}
}
