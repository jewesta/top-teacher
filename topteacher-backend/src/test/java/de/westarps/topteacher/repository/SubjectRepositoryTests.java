package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.SubjectRepository;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.Subject;

@SpringBootTest
class SubjectRepositoryTests {

	@Autowired
	private SubjectRepository subjectRepository;

	@Test
	void savesUpdatesAndArchivesSubjects() {
		final Subject saved = subjectRepository.save(new Subject(null, "Darstellendes Spiel", Lifecycle.ACTIVE));

		assertThat(saved.id()).isNotNull();
		assertThat(subjectRepository.findById(saved.id())).contains(saved);

		final Subject updated = new Subject(saved.id(), "Bühnenspiel", Lifecycle.ACTIVE);
		subjectRepository.save(updated);

		assertThat(subjectRepository.findById(saved.id())).contains(updated);

		subjectRepository.archive(saved.id());

		assertThat(subjectRepository.findById(saved.id()))
				.hasValueSatisfying(subject -> assertThat(subject.lifecycle()).isEqualTo(Lifecycle.INACTIVE));
		assertThat(subjectRepository.findActive()).extracting(Subject::id).doesNotContain(saved.id());
	}
}
