package de.topteacher.model;

import java.util.Objects;

public record Course(Integer id, SchoolClass schoolClass, Subject subject, SchoolYear schoolYear,
		CoursePeriod coursePeriod, Lifecycle lifecycle) {

	public Course {
		schoolClass = Objects.requireNonNull(schoolClass, "schoolClass must not be null");
		subject = Objects.requireNonNull(subject, "subject must not be null");
		schoolYear = Objects.requireNonNull(schoolYear, "schoolYear must not be null");
		coursePeriod = Objects.requireNonNull(coursePeriod, "coursePeriod must not be null");
		lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
	}

	public String getDisplayName() {
		return subject.getDisplayName() + " " + schoolClass.getDisplayName() + ", " + schoolYear.getDisplayName() + ", "
				+ coursePeriod.getDisplayName();
	}
}
