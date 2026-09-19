package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.westarps.topteacher.backend.repo.PupilRepository;
import de.westarps.topteacher.mcp.CourseRosterWriter.ResolvedPupil;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Pupil;

@ExtendWith(MockitoExtension.class)
class PupilWriterTests {

	@Mock
	private PupilRepository pupils;

	@Test
	void createsStandalonePupilsAsActive() {
		final Pupil saved = new Pupil(12, "Ada", "Lovelace", Lifecycle.ACTIVE);
		when(pupils.save(new Pupil(null, "Ada", "Lovelace", Lifecycle.ACTIVE))).thenReturn(saved);

		final List<Pupil> result = new PupilWriter(pupils).create(List.of(new ResolvedPupil(null, "Ada", "Lovelace")));

		assertThat(result).containsExactly(saved);
	}

	@Test
	void nameUpdatePreservesArchivedLifecycle() {
		final Pupil archived = new Pupil(12, "Ada", "Byrn", Lifecycle.INACTIVE);
		final Pupil corrected = new Pupil(12, "Ada", "Byron", Lifecycle.INACTIVE);
		when(pupils.findById(12)).thenReturn(Optional.of(archived));
		when(pupils.save(corrected)).thenReturn(corrected);

		assertThat(new PupilWriter(pupils).updateName(12, "Ada", "Byron", false)).isEqualTo(corrected);
		verify(pupils, never()).findActiveByExactName("Ada", "Byron");
	}

	@Test
	void rejectsAnUnconfirmedActiveExactNameCollision() {
		final Pupil current = new Pupil(12, "Ada", "Byrn", Lifecycle.ACTIVE);
		when(pupils.findById(12)).thenReturn(Optional.of(current));
		when(pupils.findActiveByExactName("Ada", "Byron"))
				.thenReturn(List.of(new Pupil(13, "Ada", "Byron", Lifecycle.ACTIVE)));

		assertThatThrownBy(() -> new PupilWriter(pupils).updateName(12, "Ada", "Byron", false))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("active pupil");
		verify(pupils, never()).save(new Pupil(12, "Ada", "Byron", Lifecycle.ACTIVE));
	}
}
