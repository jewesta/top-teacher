package de.westarps.topteacher.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EhCriterionParserTests {

	@Test
	void extractsCriteriaFromEhMarkdownLinks() {
		assertThat(EhCriterionParser.parse(7,
				"Die Schülerin nutzt die [korrekte Zeitform](eh:1) und **[präzise Wortwahl](eh:2)**."))
				.containsExactly(new EhCriterion(null, 7, "1", "korrekte Zeitform", 0, true),
						new EhCriterion(null, 7, "2", "präzise Wortwahl", 1, true));
	}

	@Test
	void keepsFirstOccurrenceWhenCriterionKeysAreDuplicated() {
		assertThat(EhCriterionParser.parse(7, "[erste Fassung](eh:1) und [zweite Fassung](eh:1)"))
				.containsExactly(new EhCriterion(null, 7, "1", "erste Fassung", 0, true));
	}
}
