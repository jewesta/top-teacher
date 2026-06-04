package de.topteacher.backend.pupil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PupilRepositoryTests {

	@Autowired
	private PupilRepository pupilRepository;

	@Test
	void savesUpdatesAndArchivesPupils() {
		final Pupil saved = pupilRepository.save(new Pupil(null, "Ada", "Lovelace", PupilLifecycle.ACTIVE));

		assertThat(saved.id()).isNotNull();
		assertThat(pupilRepository.findById(saved.id())).contains(saved);

		final Pupil updated = new Pupil(saved.id(), "Ada", "Byron", PupilLifecycle.ACTIVE);
		pupilRepository.save(updated);

		assertThat(pupilRepository.findById(saved.id())).contains(updated);

		pupilRepository.archive(saved.id());

		assertThat(pupilRepository.findById(saved.id()))
				.hasValueSatisfying(pupil -> assertThat(pupil.lifecycle()).isEqualTo(PupilLifecycle.INACTIVE));
	}
}
