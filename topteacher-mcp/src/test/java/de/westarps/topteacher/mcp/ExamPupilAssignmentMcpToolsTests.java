package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.mcp.ExamPupilAssignmentMcpTools.ExamPupilAssignmentStatus;
import de.westarps.topteacher.mcp.ExamPupilWriter.AssignmentResult;
import de.westarps.topteacher.model.Exam;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

@ExtendWith(MockitoExtension.class)
class ExamPupilAssignmentMcpToolsTests {

	@Mock
	private ExamPupilWriter writer;

	@Test
	void reportsNewAndAlreadyExistingAssignments() {
		final Pupil added = new Pupil(7, "Ada", "Lovelace", Lifecycle.ACTIVE);
		final Pupil existing = new Pupil(8, "Grace", "Hopper", Lifecycle.ACTIVE);
		when(writer.assign(11, List.of(7, 8)))
				.thenReturn(new AssignmentResult(exam(), List.of(added), List.of(existing)));

		final var result = new ExamPupilAssignmentMcpTools(writer).assignPupilsToExam(11, List.of(7, 8, 7));

		assertThat(result.status()).isEqualTo(ExamPupilAssignmentStatus.ASSIGNED);
		assertThat(result.assignedPupils()).extracting(ExamMcpTools.PupilView::id).containsExactly(7);
		assertThat(result.alreadyAssignedPupils()).extracting(ExamMcpTools.PupilView::id).containsExactly(8);
	}

	@Test
	void repeatedAssignmentIsUnchanged() {
		final Pupil existing = new Pupil(8, "Grace", "Hopper", Lifecycle.ACTIVE);
		when(writer.assign(11, List.of(8))).thenReturn(new AssignmentResult(exam(), List.of(), List.of(existing)));

		final var result = new ExamPupilAssignmentMcpTools(writer).assignPupilsToExam(11, List.of(8));

		assertThat(result.status()).isEqualTo(ExamPupilAssignmentStatus.UNCHANGED);
	}

	@Test
	void requiresAtLeastOnePupilId() {
		assertThatThrownBy(() -> new ExamPupilAssignmentMcpTools(writer).assignPupilsToExam(11, List.of()))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must not be empty");
	}

	private static Exam exam() {
		return new Exam(11, 3, "Exam", LocalDate.of(2026, 10, 12), null, 9);
	}
}
