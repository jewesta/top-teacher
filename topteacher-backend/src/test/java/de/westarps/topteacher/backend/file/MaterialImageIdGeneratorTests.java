package de.westarps.topteacher.backend.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class MaterialImageIdGeneratorTests {

	private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

	@Test
	void skipsAnIdentifierAlreadyReferencedByTheTargetMaterial() {
		final AtomicInteger index = new AtomicInteger();
		final String generated = MaterialImageImportService.randomImageId(Set.of("oldimage"),
				ignored -> ALPHABET.indexOf("oldimagenewimage".charAt(index.getAndIncrement())));

		assertThat(generated).isEqualTo("newimage");
		assertThat(index).hasValue(16);
	}

	@Test
	void boundsAttemptsWhenTheGeneratorOnlyProducesAnExistingIdentifier() {
		final AtomicInteger attempts = new AtomicInteger();
		assertThatThrownBy(() -> MaterialImageImportService.randomImageId(Set.of("aaaaaaaa"), ignored -> {
			attempts.incrementAndGet();
			return 0;
		})).isInstanceOf(IllegalStateException.class).hasMessageContaining("unused");
		assertThat(attempts).hasValue(800);
	}
}
