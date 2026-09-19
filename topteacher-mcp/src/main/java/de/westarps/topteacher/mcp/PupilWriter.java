package de.westarps.topteacher.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

/**
 * Applies validated standalone pupil mutations in database transactions.
 */
@Component
@ConditionalOnProperty(name = "tt.mcp.enabled", havingValue = "true")
public class PupilWriter {

	private final PupilRepository pupils;

	public PupilWriter(final PupilRepository pupils) {
		this.pupils = Objects.requireNonNull(pupils, "pupils");
	}

	@Transactional
	public List<Pupil> create(final List<ResolvedPupil> resolvedPupils) {
		Objects.requireNonNull(resolvedPupils, "resolvedPupils");
		final List<Pupil> created = new ArrayList<>();
		for (final ResolvedPupil resolvedPupil : resolvedPupils) {
			if (resolvedPupil.existingPupilId() != null) {
				throw new IllegalArgumentException("Standalone pupil creation can not reuse an existing pupil");
			}
			created.add(pupils.save(new Pupil(null, resolvedPupil.name(), resolvedPupil.surname(), Lifecycle.ACTIVE)));
		}
		return List.copyOf(created);
	}

	@Transactional
	public Pupil updateName(final int pupilId, final String name, final String surname,
			final boolean duplicateNameConfirmed) {
		final Pupil existing = pupils.findById(pupilId)
				.orElseThrow(() -> new IllegalArgumentException("Pupil does not exist: " + pupilId));
		if (existing.lifecycle() == Lifecycle.ACTIVE && !duplicateNameConfirmed && pupils
				.findActiveByExactName(name, surname).stream().anyMatch(candidate -> !candidate.id().equals(pupilId))) {
			throw new IllegalStateException(
					"An active pupil with the requested exact name appeared. Retry after resolving the conflict.");
		}
		return pupils.save(new Pupil(existing.id(), name, surname, existing.lifecycle()));
	}
}
