package de.westarps.topteacher.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import de.westarps.topteacher.backend.repo.GradingScaleRepository;
import de.westarps.topteacher.model.GradeLevel;
import de.westarps.topteacher.model.GradingScale;
import de.westarps.topteacher.model.GradingScaleRange;
import de.westarps.topteacher.model.Lifecycle;

@SpringBootTest
class GradingScaleRepositoryTests {

	@Autowired
	private GradingScaleRepository gradingScaleRepository;

	@Test
	void savesAndFindsGradingScalesWithRanges() {
		final GradingScale saved = gradingScaleRepository
				.save(new GradingScale(null, "Repository Scale 100", 100, Lifecycle.ACTIVE));
		final GradingScaleRange topRange = gradingScaleRepository
				.saveRange(new GradingScaleRange(null, saved.id(), GradeLevel.SEHR_GUT_PLUS, 95, 100));
		final GradingScaleRange bottomRange = gradingScaleRepository
				.saveRange(new GradingScaleRange(null, saved.id(), GradeLevel.UNGENUEGEND, 0, 19));

		assertThat(saved.id()).isNotNull();
		assertThat(saved.getDisplayName()).isEqualTo("Repository Scale 100 (100 Punkte)");
		assertThat(gradingScaleRepository.findById(saved.id())).contains(saved);
		assertThat(gradingScaleRepository.findActive()).contains(saved);
		assertThat(gradingScaleRepository.findRangesByGradingScaleId(saved.id()))
				.containsExactly(topRange, bottomRange);
	}
}
