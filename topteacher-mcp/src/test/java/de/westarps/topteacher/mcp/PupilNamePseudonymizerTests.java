package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;

class PupilNamePseudonymizerTests {

	private final Pupil pupil = new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE);

	@Test
	void missingPermissionContextFailsClosedForEveryNameProjection() {
		assertThatThrownBy(() -> PupilNamePseudonymizer.firstName(pupil)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> PupilNamePseudonymizer.surname(pupil)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> PupilNamePseudonymizer.displayName(pupil)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(PupilNamePseudonymizer::requireIdentityWrite).isInstanceOf(SecurityException.class);
	}

	@Test
	void scopesRetainFullIdentityOrSurnameFirstInitialsAndRestoreTheirPreviousPermission() {
		try (var full = McpAccessContext.open(McpAccessLevel.FULL)) {
			assertThat(PupilNamePseudonymizer.firstName(pupil)).isEqualTo("Hans");
			assertThat(PupilNamePseudonymizer.surname(pupil)).isEqualTo("Müller");
			assertThat(PupilNamePseudonymizer.displayName(pupil)).isEqualTo("Hans Müller");
			try (var restricted = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
				assertThat(PupilNamePseudonymizer.firstName(pupil)).isEqualTo("MH");
				assertThat(PupilNamePseudonymizer.surname(pupil)).isEmpty();
				assertThat(PupilNamePseudonymizer.displayName(pupil)).isEqualTo("MH");
				assertThatThrownBy(PupilNamePseudonymizer::requireIdentityWrite)
						.isInstanceOf(SecurityException.class);
			}
			assertThat(PupilNamePseudonymizer.displayName(pupil)).isEqualTo("Hans Müller");
		}
		assertThatThrownBy(() -> PupilNamePseudonymizer.displayName(pupil)).isInstanceOf(SecurityException.class);
	}
}
