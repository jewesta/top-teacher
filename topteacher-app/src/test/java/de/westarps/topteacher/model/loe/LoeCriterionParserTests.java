package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoeCriterionParserTests {

	@Test
	void extractsCriteriaFromTaggedMarkdownLinks() {
		assertThat(LoeCriterionParser.parse(7,
				"Die Schülerin nutzt die [korrekte Zeitform](eh:1) und **[präzise Wortwahl](eh:2)**."))
				.containsExactly(new LoeCriterion(null, 7, "1", "korrekte Zeitform", 0, true),
						new LoeCriterion(null, 7, "2", "präzise Wortwahl", 1, true));
	}

	@Test
	void keepsFirstOccurrenceWhenCriterionKeysAreDuplicated() {
		assertThat(LoeCriterionParser.parse(7, "[erste Fassung](eh:1) und [zweite Fassung](eh:1)"))
				.containsExactly(new LoeCriterion(null, 7, "1", "erste Fassung", 0, true));
	}
}
