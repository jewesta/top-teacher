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
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.model.Course;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * Applies exam-pupil assignment changes through the backend integrity rules in
 * one transaction.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class ExamPupilWriter {

	private final CourseRepository courses;
	private final ExamRepository exams;

	public ExamPupilWriter(final CourseRepository courses, final ExamRepository exams) {
		this.courses = Objects.requireNonNull(courses, "courses");
		this.exams = Objects.requireNonNull(exams, "exams");
	}

	@Transactional
	public AssignmentResult assign(final int examId, final List<Integer> pupilIds) {
		Objects.requireNonNull(pupilIds, "pupilIds");
		final Exam exam = examInActiveCourse(examId);
		final LinkedHashMap<Integer, Pupil> activeCoursePupils = new LinkedHashMap<>();
		courses.findPupils(exam.courseId()).stream().filter(pupil -> pupil.lifecycle() == Lifecycle.ACTIVE)
				.forEach(pupil -> activeCoursePupils.put(pupil.id(), pupil));
		final List<Integer> invalidPupilIds = pupilIds.stream()
				.filter(pupilId -> !activeCoursePupils.containsKey(pupilId)).toList();
		if (!invalidPupilIds.isEmpty()) {
			throw new IllegalArgumentException(
					"Pupils must be active and assigned to course " + exam.courseId() + ": " + invalidPupilIds);
		}

		final LinkedHashMap<Integer, Pupil> assignedById = new LinkedHashMap<>();
		exams.findPupils(examId).forEach(pupil -> assignedById.put(pupil.id(), pupil));
		final List<Pupil> assignedPupils = new ArrayList<>();
		final List<Pupil> alreadyAssignedPupils = new ArrayList<>();
		for (final Integer pupilId : new LinkedHashSet<>(pupilIds)) {
			final Pupil pupil = activeCoursePupils.get(pupilId);
			if (assignedById.containsKey(pupilId)) {
				alreadyAssignedPupils.add(pupil);
				continue;
			}
			exams.assignPupil(examId, pupilId);
			assignedPupils.add(pupil);
		}
		return new AssignmentResult(exam, assignedPupils, alreadyAssignedPupils);
	}

	@Transactional
	public RemovalResult remove(final int examId, final List<Integer> pupilIds) {
		Objects.requireNonNull(pupilIds, "pupilIds");
		final Exam exam = examInActiveCourse(examId);
		final LinkedHashMap<Integer, Pupil> assignedById = new LinkedHashMap<>();
		exams.findPupils(examId).forEach(pupil -> assignedById.put(pupil.id(), pupil));
		final List<Pupil> removedPupils = new ArrayList<>();
		final List<Integer> notAssignedPupilIds = new ArrayList<>();
		for (final Integer pupilId : new LinkedHashSet<>(pupilIds)) {
			final Pupil pupil = assignedById.get(pupilId);
			if (pupil == null) {
				notAssignedPupilIds.add(pupilId);
				continue;
			}
			exams.removePupil(examId, pupilId);
			removedPupils.add(pupil);
		}
		return new RemovalResult(exam, removedPupils, notAssignedPupilIds);
	}

	private Exam examInActiveCourse(final int examId) {
		final Exam exam = exams.findById(examId)
				.orElseThrow(() -> new IllegalArgumentException("Exam does not exist: " + examId));
		final Course course = courses.findById(exam.courseId())
				.orElseThrow(() -> new IllegalArgumentException("Course does not exist: " + exam.courseId()));
		if (course.lifecycle() != Lifecycle.ACTIVE) {
			throw new IllegalArgumentException("Archived course exam can not change pupil assignments: " + examId);
		}
		return exam;
	}

	public record AssignmentResult(Exam exam, List<Pupil> assignedPupils, List<Pupil> alreadyAssignedPupils) {

		public AssignmentResult {
			Objects.requireNonNull(exam, "exam");
			assignedPupils = List.copyOf(Objects.requireNonNull(assignedPupils, "assignedPupils"));
			alreadyAssignedPupils = List.copyOf(Objects.requireNonNull(alreadyAssignedPupils, "alreadyAssignedPupils"));
		}
	}

	public record RemovalResult(Exam exam, List<Pupil> removedPupils, List<Integer> notAssignedPupilIds) {

		public RemovalResult {
			Objects.requireNonNull(exam, "exam");
			removedPupils = List.copyOf(Objects.requireNonNull(removedPupils, "removedPupils"));
			notAssignedPupilIds = List.copyOf(Objects.requireNonNull(notAssignedPupilIds, "notAssignedPupilIds"));
		}
	}
}
