package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LoeCriterionParserTests {

	@Test
	void extractsCriteriaFromTaggedMarkdownLinks() {
		assertThat(LoeCriterionParser.parse(7,
				"Der/die Schüler:in nutzt die [korrekte Zeitform](crit:1) und **[präzise Wortwahl](crit:2)**."))
						.containsExactly(new LoeCriterion(null, 7, "1", "korrekte Zeitform", 0, true),
								new LoeCriterion(null, 7, "2", "präzise Wortwahl", 1, true));
	}

	@Test
	void keepsFirstOccurrenceWhenCriterionKeysAreDuplicated() {
		assertThat(LoeCriterionParser.parse(7, "[erste Fassung](crit:1) und [zweite Fassung](crit:1)"))
				.containsExactly(new LoeCriterion(null, 7, "1", "erste Fassung", 0, true));
	}

	@Test
	void parsesDefaultCommaAndDotPointValuesIntoHalfPointUnits() {
		assertThat(LoeCriterionParser.parse(7,
				"[Standard](crit:1), [halb](crit:2?points=0,5), [anderthalb](crit:3?points=1.5)"))
						.containsExactly(new LoeCriterion(null, 7, "1", "Standard", 2, 0, true),
								new LoeCriterion(null, 7, "2", "halb", 1, 1, true),
								new LoeCriterion(null, 7, "3", "anderthalb", 3, 2, true));
	}

	@Test
	void reportsMalformedPointValuesAndReservesTheirKeys() {
		final LoeCriterionParseResult result = LoeCriterionParser.analyze(7,
				"[offen](crit:1?points=?) und [doppelt](crit:1)");

		assertThat(result.criteria()).isEmpty();
		assertThat(result.issues()).extracting(LoeCriterionIssue::kind)
				.containsExactly(LoeCriterionIssue.Kind.INVALID_POINTS, LoeCriterionIssue.Kind.DUPLICATE_KEY);
		assertThat(result.tagCount()).isEqualTo(2);
	}

	@Test
	void acceptsExplicitDefaultsAndUrlEncodedQueryValues() {
		assertThat(LoeCriterionParser.parse(7,
				"[eins](crit:1?points=1) [halb](crit:2?points=0%2C5) [maximal](crit:3?points=999)"))
				.containsExactly(new LoeCriterion(null, 7, "1", "eins", 2, 0, true),
						new LoeCriterion(null, 7, "2", "halb", 1, 1, true),
						new LoeCriterion(null, 7, "3", "maximal", 1998, 2, true));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "?", "0", "-1", "0,25", "1000", "1/2", "1=2" })
	void rejectsInvalidPointValuesWithoutLosingTheCriterion(final String value) {
		final LoeCriterionParseResult result = LoeCriterionParser.analyze(7, "[Aspekt](crit:1?points=" + value + ")");

		assertThat(result.criteria()).isEmpty();
		assertThat(result.issues())
				.containsExactly(new LoeCriterionIssue(LoeCriterionIssue.Kind.INVALID_POINTS, "Aspekt"));
		assertThat(result.tagCount()).isOne();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "points", "width=75", "points=2&points=3", "points=2&width=75",
			"points=2&", "points=2#fragment", "points=%", "points=%GG", "Points=2" })
	void rejectsInvalidParametersAndReservesTheCriterionKey(final String query) {
		final LoeCriterionParseResult result = LoeCriterionParser.analyze(7,
				"[Aspekt](crit:1?" + query + ") [doppelt](crit:1)");

		assertThat(result.criteria()).isEmpty();
		assertThat(result.issues()).extracting(LoeCriterionIssue::kind)
				.containsExactly(LoeCriterionIssue.Kind.INVALID_PARAMETERS, LoeCriterionIssue.Kind.DUPLICATE_KEY);
		assertThat(result.tagCount()).isEqualTo(2);
	}

	@Test
	void doesNotTreatImagesOtherSchemesOrLegacyDeclarationsAsCriteria() {
		final LoeCriterionParseResult result = LoeCriterionParser.analyze(7,
				"![Bild](crit:1?points=2) ![Diagramm](img:4n8k2p7c?width=75) [Link](https://example.com) [alt](eh:1/2)");

		assertThat(result.criteria()).isEmpty();
		assertThat(result.issues()).isEmpty();
		assertThat(result.tagCount()).isZero();
	}
}
