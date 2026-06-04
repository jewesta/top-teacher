package de.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CourseTests {

	@Test
	void omitsFullYearInDisplayName() {
		final Course course = new Course(1, SchoolClass.CLS_5A, Subject.ENGLISH, new SchoolYear(2026),
				CoursePeriod.FULL_YEAR, Lifecycle.ACTIVE);

		assertThat(course.getDisplayName()).isEqualTo("Englisch 5a, '26/'27");
	}

	@Test
	void includesHalfYearInDisplayName() {
		final Course course = new Course(1, SchoolClass.CLS_5A, Subject.ENGLISH, new SchoolYear(2026),
				CoursePeriod.FIRST_HALF, Lifecycle.ACTIVE);

		assertThat(course.getDisplayName()).isEqualTo("Englisch 5a, '26/'27, 1. Hj.");
	}
}
