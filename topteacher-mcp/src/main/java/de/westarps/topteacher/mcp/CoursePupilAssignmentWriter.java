package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * Adds a fully resolved pupil roster to an existing active course in one
 * database transaction.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class CoursePupilAssignmentWriter {

	private final CourseRepository courses;
	private final PupilRepository pupils;

	public CoursePupilAssignmentWriter(final CourseRepository courses, final PupilRepository pupils) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.pupils = Objects.requireNonNull(pupils, "pupils");
	}

	@Transactional
	public AssignmentResult assign(final int courseId, final List<ResolvedPupil> resolvedPupils) {
		PupilNamePseudonymizer.requireIdentityWrite();
		Objects.requireNonNull(resolvedPupils, "resolvedPupils");
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course can not receive new pupil assignments: " + courseId);
		}

		final LinkedHashMap<Integer, Pupil> reusedPupils = validateReusedPupils(resolvedPupils);
		final LinkedHashMap<Integer, Pupil> currentPupils = new LinkedHashMap<>();
		courses.findPupils(courseId).forEach(pupil -> currentPupils.put(pupil.id(), pupil));
		final LinkedHashMap<Integer, Pupil> assignedPupils = new LinkedHashMap<>();
		final LinkedHashMap<Integer, Pupil> alreadyAssignedPupils = new LinkedHashMap<>();

		for (final ResolvedPupil resolvedPupil : resolvedPupils) {
			final Pupil pupil = resolvePupil(resolvedPupil, reusedPupils);
			if (currentPupils.containsKey(pupil.id())) {
				alreadyAssignedPupils.put(pupil.id(), pupil);
				continue;
			}
			courses.assignPupil(courseId, pupil.id());
			currentPupils.put(pupil.id(), pupil);
			assignedPupils.put(pupil.id(), pupil);
		}
		return new AssignmentResult(course, List.copyOf(assignedPupils.values()),
				List.copyOf(alreadyAssignedPupils.values()));
	}

	@Transactional
	public AssignmentResult assignExisting(final int courseId, final List<Integer> pupilIds) {
		PupilNamePseudonymizer.isPseudonymized();
		final List<Integer> validatedIds = ExamWriteMcpTools.pupilIds(pupilIds, false);
		final Course course = courses.findById(courseId)
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + courseId));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course can not receive new pupil assignments: " + courseId);
		}
		final LinkedHashMap<Integer, Pupil> requestedPupils = new LinkedHashMap<>();
		for (final int pupilId : validatedIds) {
			final Pupil pupil = pupils.findById(pupilId)
					.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
			if (pupil.lifecycle() != Lifecycle.ACTIVE) {
				throw new IllegalArgumentException("Archived pupil can not be newly assigned: " + pupilId);
			}
			requestedPupils.put(pupilId, pupil);
		}
		final Set<Integer> currentIds = courses.findPupils(courseId).stream().map(Pupil::id)
				.collect(Collectors.toSet());
		final ArrayList<Pupil> assigned = new ArrayList<>();
		final ArrayList<Pupil> alreadyAssigned = new ArrayList<>();
		for (final Pupil pupil : requestedPupils.values()) {
			if (currentIds.contains(pupil.id())) {
				alreadyAssigned.add(pupil);
			} else {
				courses.assignPupil(courseId, pupil.id());
				assigned.add(pupil);
			}
		}
		return new AssignmentResult(course, assigned, alreadyAssigned);
	}

	private LinkedHashMap<Integer, Pupil> validateReusedPupils(final List<ResolvedPupil> resolvedPupils) {
		final LinkedHashMap<Integer, Pupil> reusedPupils = new LinkedHashMap<>();
		for (final ResolvedPupil resolvedPupil : resolvedPupils) {
			if (resolvedPupil.existingPupilId() == null || reusedPupils.containsKey(resolvedPupil.existingPupilId())) {
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
			reusedPupils.put(existing.id(), existing);
		}
		return reusedPupils;
	}

	private Pupil resolvePupil(final ResolvedPupil resolvedPupil, final LinkedHashMap<Integer, Pupil> reusedPupils) {
		if (resolvedPupil.existingPupilId() == null) {
			return pupils.save(new Pupil(null, resolvedPupil.name(), resolvedPupil.surname(), Lifecycle.ACTIVE));
		}
		return reusedPupils.get(resolvedPupil.existingPupilId());
	}

	public record AssignmentResult(Course course, List<Pupil> assignedPupils, List<Pupil> alreadyAssignedPupils) {

		public AssignmentResult {
			Objects.requireNonNull(course, "course");
			assignedPupils = List.copyOf(Objects.requireNonNull(assignedPupils, "assignedPupils"));
			alreadyAssignedPupils = List.copyOf(Objects.requireNonNull(alreadyAssignedPupils, "alreadyAssignedPupils"));
		}
	}
}
