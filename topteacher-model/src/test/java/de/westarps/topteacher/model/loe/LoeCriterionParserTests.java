package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoeCriterionParserTests {

	@Test
	void extractsCriteriaFromTaggedMarkdownLinks() {
		assertThat(LoeCriterionParser.parse(7,
				"Der/die Schüler:in nutzt die [korrekte Zeitform](eh:1) und **[präzise Wortwahl](eh:2)**."))
						.containsExactly(new LoeCriterion(null, 7, "1", "korrekte Zeitform", 0, true),
								new LoeCriterion(null, 7, "2", "präzise Wortwahl", 1, true));
	}

	@Test
	void keepsFirstOccurrenceWhenCriterionKeysAreDuplicated() {
		assertThat(LoeCriterionParser.parse(7, "[erste Fassung](eh:1) und [zweite Fassung](eh:1)"))
				.containsExactly(new LoeCriterion(null, 7, "1", "erste Fassung", 0, true));
	}

	@Test
	void parsesDefaultCommaAndDotPointValuesIntoHalfPointUnits() {
		assertThat(LoeCriterionParser.parse(7,
				"[Standard](eh:1), [halb](eh:2/0,5), [anderthalb](eh:3/1.5)"))
						.containsExactly(new LoeCriterion(null, 7, "1", "Standard", 2, 0, true),
								new LoeCriterion(null, 7, "2", "halb", 1, 1, true),
								new LoeCriterion(null, 7, "3", "anderthalb", 3, 2, true));
	}

	@Test
	void reportsMalformedPointValuesAndReservesTheirKeys() {
		final LoeCriterionParseResult result = LoeCriterionParser.analyze(7,
				"[offen](eh:1/?) und [doppelt](eh:1)");

		assertThat(result.criteria()).isEmpty();
		assertThat(result.issues()).extracting(LoeCriterionIssue::kind)
				.containsExactly(LoeCriterionIssue.Kind.INVALID_POINTS, LoeCriterionIssue.Kind.DUPLICATE_KEY);
		assertThat(result.tagCount()).isEqualTo(2);
	}
}
