package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseTests {

	@Test
	void omitsFullYearInDisplayName() {
		final Course course = new Course(1, SchoolClass.CLS_5A, subject("Englisch"), new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE, 1);

		assertThat(course.getDisplayName()).isEqualTo("Englisch 5a, '26/'27");
	}

	@Test
	void includesHalfYearInDisplayName() {
		final Course course = new Course(1, SchoolClass.CLS_5A, subject("Englisch"), new SchoolYear(2026),
				CoursePeriod.FIRST_HALF, Lifecycle.ACTIVE, 1);

		assertThat(course.getDisplayName()).isEqualTo("Englisch 5a, '26/'27, 1. Hj.");
	}

	private static Subject subject(final String name) {
		return new Subject(1, name, Lifecycle.ACTIVE);
	}
}
