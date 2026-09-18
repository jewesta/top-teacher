package de.westarps.topteacher.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * Applies a fully resolved course roster in one database transaction.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CourseRosterWriter {

	private final CourseRepository courses;
	private final PupilRepository pupils;

	public CourseRosterWriter(final CourseRepository courses, final PupilRepository pupils) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.pupils = Objects.requireNonNull(pupils, "pupils");
	}

	@Transactional
	public CreatedCourseRoster create(final Course course, final List<ResolvedPupil> resolvedPupils) {
		Objects.requireNonNull(course, "course");
		Objects.requireNonNull(resolvedPupils, "resolvedPupils");
		courses.findByNaturalKey(course.schoolClass(), course.subject().id(), course.schoolYear(),
				course.coursePeriod()).ifPresent(existing -> {
					throw new IllegalStateException(
							"Course already exists: " + existing.id() + " (" + existing.getDisplayName() + ")");
				});

		final Course createdCourse = courses.save(course);
		final LinkedHashMap<Integer, Pupil> assignedPupils = new LinkedHashMap<>();
		for (final ResolvedPupil resolvedPupil : resolvedPupils) {
			if (resolvedPupil.existingPupilId() != null
					&& assignedPupils.containsKey(resolvedPupil.existingPupilId())) {
				continue;
			}
			final Pupil pupil = resolvePupil(resolvedPupil);
			courses.assignPupil(createdCourse.id(), pupil.id());
			assignedPupils.put(pupil.id(), pupil);
		}
		return new CreatedCourseRoster(createdCourse, List.copyOf(assignedPupils.values()));
	}

	private Pupil resolvePupil(final ResolvedPupil resolvedPupil) {
		if (resolvedPupil.existingPupilId() == null) {
			return pupils.save(new Pupil(null, resolvedPupil.name(), resolvedPupil.surname(), Lifecycle.ACTIVE));
		}

		final Pupil existing = pupils.findById(resolvedPupil.existingPupilId()).orElseThrow(
				() -> new IllegalArgumentException("Pupil does not exist: " + resolvedPupil.existingPupilId()));
		if (!existing.name().equals(resolvedPupil.name()) || !existing.surname().equals(resolvedPupil.surname())) {
			throw new IllegalArgumentException("Pupil " + existing.id() + " no longer has the expected exact name: "
					+ resolvedPupil.name() + " " + resolvedPupil.surname());
		}
		if (existing.lifecycle() == Lifecycle.ACTIVE) {
			return existing;
		}
		return pupils.save(new Pupil(existing.id(), existing.name(), existing.surname(), Lifecycle.ACTIVE));
	}

	public record ResolvedPupil(Integer existingPupilId, String name, String surname) {

		public ResolvedPupil {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(surname, "surname");
		}
	}

	public record CreatedCourseRoster(Course course, List<Pupil> pupils) {

		public CreatedCourseRoster {
			Objects.requireNonNull(course, "course");
			pupils = List.copyOf(Objects.requireNonNull(pupils, "pupils"));
		}
	}
}
