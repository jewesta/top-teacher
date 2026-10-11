package de.westarps.topteacher.mcp;

import java.util.Locale;
import java.util.Objects;

import de.westarps.topteacher.mcp.security.McpAccessContext;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.Pupil;

/**
 * The single boundary for pupil names leaving TopTeacher through MCP. User-authored
 * content is deliberately outside this boundary. Missing access context fails closed.
 */
public final class PupilNamePseudonymizer {

	private PupilNamePseudonymizer() {
	}

	public static boolean isPseudonymized() {
		return McpAccessContext.accessLevel() == McpAccessLevel.PSEUDONYMIZED;
	}

	public static void requireIdentityWrite() {
		if (isPseudonymized()) {
			throw new SecurityException("Pseudonymized MCP access cannot create, import, rename, or match pupils by real names. Use existing pupil IDs for assignments.");
		}
	}

	public static String firstName(final Pupil pupil) {
		Objects.requireNonNull(pupil, "pupil");
		return firstName(pupil.name(), pupil.surname());
	}

	public static String surname(final Pupil pupil) {
		Objects.requireNonNull(pupil, "pupil");
		return surname(pupil.name(), pupil.surname());
	}

	public static String firstName(final String name, final String surname) {
		return isPseudonymized() ? initials(name, surname) : name;
	}

	public static String surname(final String name, final String surname) {
		return isPseudonymized() ? "" : surname;
	}

	public static String displayName(final Pupil pupil) {
		Objects.requireNonNull(pupil, "pupil");
		return displayName(pupil.name(), pupil.surname());
	}

	public static String displayName(final String name, final String surname) {
		return isPseudonymized() ? initials(name, surname) : name + " " + surname;
	}

	private static String initials(final String name, final String surname) {
		return (initial(surname) + initial(name)).toUpperCase(Locale.GERMAN);
	}

	private static String initial(final String value) {
		final String text = Objects.requireNonNull(value, "name").strip();
		return text.isEmpty() ? "" : new String(Character.toChars(text.codePointAt(0)));
	}
}
