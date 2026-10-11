package de.westarps.topteacher.mcp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.util.function.Supplier;

import de.westarps.topteacher.backend.authoring.ExamCreationExecutor;
import de.westarps.topteacher.backend.authoring.ExamDesignGuard;
import de.westarps.topteacher.backend.authoring.ExamMutationExecutor;
import de.westarps.topteacher.backend.repo.CourseRepository;
import de.westarps.topteacher.backend.repo.ExamRepository;
import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;

/** Isolates MCP metadata-adapter tests from the executors' separately tested transactions. */
final class ExamWriteTestSupport {

	private ExamWriteTestSupport() {
	}

	static ExamWriteMcpTools tools(final CourseRepository courses, final ExamRepository exams,
			final GradingScaleRepository scales, final LevelOfExpectationsRepository design) {
		final ExamCreationExecutor creations = mock(ExamCreationExecutor.class);
		final ExamMutationExecutor mutations = mock(ExamMutationExecutor.class);
		final ExamDesignGuard guard = mock(ExamDesignGuard.class);
		lenient().doAnswer(invocation -> new ExamCreationExecutor.Creation(
				((Supplier<Integer>) invocation.getArgument(4)).get(), 0, false))
				.when(creations).execute(anyInt(), anyString(), anyString(), anyString(), any());
		lenient().doAnswer(invocation -> new ExamMutationExecutor.MutationExecution<>(0, false,
				((Supplier<?>) invocation.getArgument(5)).get()))
				.when(mutations).execute(anyInt(), anyLong(), anyString(), anyString(), anyString(), any(), any(), any());
		lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get())
				.when(guard).read(anyInt(), any());
		lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get())
				.when(guard).write(anyInt(), any(), any());
		return new ExamWriteMcpTools(courses, exams, scales, design, creations, mutations, guard);
	}
}
