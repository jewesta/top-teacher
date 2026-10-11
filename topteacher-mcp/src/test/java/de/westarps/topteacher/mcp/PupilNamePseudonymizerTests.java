package de.westarps.topteacher.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.Lifecycle;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;
import tools.jackson.databind.json.JsonMapper;

class PupilNamePseudonymizerTests {

	private final Pupil pupil = new Pupil(42, "Hans", "Müller", Lifecycle.ACTIVE);

	@Test
	void missingPermissionContextFailsClosedForEveryNameProjection() {
		assertThatThrownBy(() -> PupilNamePseudonymizer.firstName(pupil)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> PupilNamePseudonymizer.surname(pupil)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> PupilNamePseudonymizer.displayName(pupil)).isInstanceOf(SecurityException.class);
		assertThatThrownBy(() -> PupilNamePseudonymizer.identity(pupil)).isInstanceOf(SecurityException.class);
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

	@Test
	void identityDtosDoNotMislabelInitialsAsNamesAndOmitRealNameFields() {
		final JsonMapper mapper = JsonMapper.builder().build();
		try (var full = McpAccessContext.open(McpAccessLevel.FULL)) {
			final var fullView = ExamMcpTools.pupilView(pupil);
			assertThat(fullView.displayName()).isEqualTo("Hans Müller");
			assertThat(fullView.identityRepresentation()).isEqualTo("FULL_NAME");
			assertThat(fullView.name()).isEqualTo("Hans");
			try (var restricted = McpAccessContext.open(McpAccessLevel.PSEUDONYMIZED)) {
				final var view = ExamMcpTools.pupilView(pupil);
				assertThat(view.displayName()).isEqualTo("MH");
				assertThat(view.identityRepresentation()).isEqualTo("INITIALS");
				assertThat(view.name()).isNull();
				assertThat(view.surname()).isNull();
				assertThat(mapper.writeValueAsString(view)).contains("\"displayName\":\"MH\"", "\"identityRepresentation\":\"INITIALS\"")
						.doesNotContain("\"name\"", "\"surname\"", "Hans", "Müller");
			}
		}
	}
}
