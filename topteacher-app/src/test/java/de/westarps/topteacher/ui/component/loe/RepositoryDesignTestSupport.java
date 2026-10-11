package de.westarps.topteacher.ui.component.loe;

import java.util.function.Supplier;

import org.mockito.Mockito;

import de.westarps.topteacher.backend.authoring.ExamDesignConflictException;
import de.westarps.topteacher.backend.repo.LevelOfExpectationsRepository;

public final class RepositoryDesignTestSupport {

	private RepositoryDesignTestSupport() {
	}

	public static LevelOfExpectationsRepository mockRepository() {
		return Mockito.mock(LevelOfExpectationsRepository.class, invocation -> switch (invocation.getMethod().getName()) {
			case "withDesignRead" -> ((Supplier<?>) invocation.getArgument(1)).get();
			case "withDesignWrite" -> ((Supplier<?>) invocation.getArgument(2)).get();
			default -> Mockito.RETURNS_DEFAULTS.answer(invocation);
		});
	}

	public static void rejectDesignWrites(final LevelOfExpectationsRepository repository, final long expectedRevision,
			final long currentRevision) {
		Mockito.doThrow(new ExamDesignConflictException(expectedRevision, currentRevision)).when(repository)
				.withDesignWrite(org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.eq(expectedRevision),
						org.mockito.ArgumentMatchers.any());
	}
}
