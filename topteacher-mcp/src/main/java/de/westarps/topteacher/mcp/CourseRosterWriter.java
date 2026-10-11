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
		PupilNamePseudonymizer.requireIdentityWrite();
		Objects.requireNonNull(course, "course");
		Objects.requireNonNull(resolvedPupils, "resolvedPupils");
		final LinkedHashMap<Integer, Pupil> existingPupils = validateExistingPupils(resolvedPupils);
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
			final Pupil pupil = resolvePupil(resolvedPupil, existingPupils);
			courses.assignPupil(createdCourse.id(), pupil.id());
			assignedPupils.put(pupil.id(), pupil);
		}
		return new CreatedCourseRoster(createdCourse, List.copyOf(assignedPupils.values()));
	}

	@Transactional
	public CreatedCourseRoster createWithExisting(final Course course, final List<Integer> pupilIds) {
		PupilNamePseudonymizer.isPseudonymized();
		Objects.requireNonNull(course, "course");
		final List<Integer> validatedIds = ExamWriteMcpTools.pupilIds(pupilIds, true);
		final LinkedHashMap<Integer, Pupil> existingPupils = new LinkedHashMap<>();
		for (final int pupilId : validatedIds) {
			final Pupil pupil = pupils.findById(pupilId)
					.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
			if (pupil.lifecycle() != Lifecycle.ACTIVE) {
				throw new IllegalArgumentException("Archived pupil can not be newly assigned: " + pupilId);
			}
			existingPupils.put(pupilId, pupil);
		}
		courses.findByNaturalKey(course.schoolClass(), course.subject().id(), course.schoolYear(),
				course.coursePeriod()).ifPresent(existing -> {
					throw new IllegalStateException(
							"Course already exists: " + existing.id() + " (" + existing.getDisplayName() + ")");
				});
		final Course created = courses.save(course);
		for (final int pupilId : existingPupils.keySet()) {
			courses.assignPupil(created.id(), pupilId);
		}
		return new CreatedCourseRoster(created, List.copyOf(existingPupils.values()));
	}

	private LinkedHashMap<Integer, Pupil> validateExistingPupils(final List<ResolvedPupil> resolvedPupils) {
		final LinkedHashMap<Integer, Pupil> existingPupils = new LinkedHashMap<>();
		for (final ResolvedPupil resolvedPupil : resolvedPupils) {
			if (resolvedPupil.existingPupilId() == null
					|| existingPupils.containsKey(resolvedPupil.existingPupilId())) {
				continue;
			}
			final Pupil existing = pupils.findById(resolvedPupil.existingPupilId()).orElseThrow(
					() -> new IllegalArgumentException("Pupil does not exist: " + resolvedPupil.existingPupilId()));
			if (!existing.name().equals(resolvedPupil.name()) || !existing.surname().equals(resolvedPupil.surname())) {
				throw new IllegalArgumentException("Pupil " + existing.id() + " no longer has the expected exact name: "
						+ PupilNamePseudonymizer.displayName(resolvedPupil.name(), resolvedPupil.surname()));
			}
			if (existing.lifecycle() != Lifecycle.ACTIVE) {
				throw new IllegalArgumentException("Archived pupil can not be newly assigned: " + existing.id());
			}
			existingPupils.put(existing.id(), existing);
		}
		return existingPupils;
	}

	private Pupil resolvePupil(final ResolvedPupil resolvedPupil, final LinkedHashMap<Integer, Pupil> existingPupils) {
		if (resolvedPupil.existingPupilId() == null) {
			return pupils.save(new Pupil(null, resolvedPupil.name(), resolvedPupil.surname(), Lifecycle.ACTIVE));
		}
		return existingPupils.get(resolvedPupil.existingPupilId());
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
