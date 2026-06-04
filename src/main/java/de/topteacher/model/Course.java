package de.topteacher.model;

import java.util.Objects;

public record Course(Integer id, SchoolClass schoolClass, Subject subject, Term term, Lifecycle lifecycle) {

	public Course {
		schoolClass = Objects.requireNonNull(schoolClass, "schoolClass must not be null");
		subject = Objects.requireNonNull(subject, "subject must not be null");
		term = Objects.requireNonNull(term, "term must not be null");
		lifecycle = Objects.requireNonNull(lifecycle, "lifecycle must not be null");
	}

	public String getDisplayName() {
		return subject.getDisplayName() + " " + schoolClass.getDisplayName() + ", " + term.getDisplayName();
	}
}
