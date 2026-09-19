package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * Removes pupil assignments from an active course through the repository's
 * existing integrity checks in one transaction.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CoursePupilRemovalWriter {

	private final CourseRepository courses;

	public CoursePupilRemovalWriter(final CourseRepository courses) {
		this.courses = Objects.requireNonNull(courses, "courses");
	}

	@Transactional
	public RemovalResult remove(final int courseId, final List<Integer> pupilIds) {
		Objects.requireNonNull(pupilIds, "pupilIds");
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course can not have pupil assignments removed: " + courseId);
		}

		final LinkedHashMap<Integer, Pupil> assignedPupils = new LinkedHashMap<>();
		courses.findPupils(courseId).forEach(pupil -> assignedPupils.put(pupil.id(), pupil));
		final List<Pupil> removedPupils = new ArrayList<>();
		final List<Integer> notAssignedPupilIds = new ArrayList<>();
		for (final Integer pupilId : new LinkedHashSet<>(pupilIds)) {
			final Pupil pupil = assignedPupils.get(pupilId);
			if (pupil == null) {
				notAssignedPupilIds.add(pupilId);
				continue;
			}
			courses.removePupil(courseId, pupilId);
			removedPupils.add(pupil);
		}
		return new RemovalResult(course, removedPupils, notAssignedPupilIds);
	}

	public record RemovalResult(Course course, List<Pupil> removedPupils, List<Integer> notAssignedPupilIds) {

		public RemovalResult {
			Objects.requireNonNull(course, "course");
			removedPupils = List.copyOf(Objects.requireNonNull(removedPupils, "removedPupils"));
			notAssignedPupilIds = List.copyOf(Objects.requireNonNull(notAssignedPupilIds, "notAssignedPupilIds"));
		}
	}
}
