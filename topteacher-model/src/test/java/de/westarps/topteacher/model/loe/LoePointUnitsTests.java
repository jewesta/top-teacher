package de.westarps.topteacher.model.loe;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoePointUnitsTests {

	@Test
	void parsesCommaAndDotHalfPointValuesWithoutFloatingPointArithmetic() {
		assertThat(LoePointUnits.parsePositive("0,5")).hasValue(1);
		assertThat(LoePointUnits.parsePositive("1.5")).hasValue(3);
		assertThat(LoePointUnits.parsePositive("4")).hasValue(8);
		assertThat(LoePointUnits.parsePositive("5,0")).hasValue(10);
		assertThat(LoePointUnits.parsePositive("999")).hasValue(1998);
	}

	@Test
	void rejectsZeroAndValuesOutsideHalfPointSteps() {
		assertThat(LoePointUnits.parsePositive("0")).isEmpty();
		assertThat(LoePointUnits.parsePositive("1,25")).isEmpty();
		assertThat(LoePointUnits.parsePositive("?")).isEmpty();
		assertThat(LoePointUnits.parsePositive("999,5")).isEmpty();
		assertThat(LoePointUnits.parsePositive("1000")).isEmpty();
	}

	@Test
	void formatsGermanPointValuesWithoutTrailingZero() {
		assertThat(LoePointUnits.formatGerman(1)).isEqualTo("0,5");
		assertThat(LoePointUnits.formatGerman(2)).isEqualTo("1");
		assertThat(LoePointUnits.formatGerman(3)).isEqualTo("1,5");
	}

	@Test
	void roundsHalfPointsUpOnlyAtTheRequirementBoundary() {
		assertThat(LoePointUnits.roundedWholePoints(0)).isZero();
		assertThat(LoePointUnits.roundedWholePoints(1)).isEqualTo(1);
		assertThat(LoePointUnits.roundedWholePoints(2)).isEqualTo(1);
		assertThat(LoePointUnits.roundedWholePoints(3)).isEqualTo(2);
	}
}
