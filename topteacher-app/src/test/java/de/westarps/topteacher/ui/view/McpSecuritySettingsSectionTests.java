package de.westarps.topteacher.ui.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;

import de.westarps.topteacher.backend.mcp.McpAccessTokenConflictException;
import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;
import de.westarps.topteacher.ui.component.ClipboardCopyButton;
import de.westarps.topteacher.ui.component.SettingsLineItem;

class McpSecuritySettingsSectionTests {

	@Test
	void defaultsDraftsToPseudonymizedAndRejectsInvalidNameAndExpiry() {
		final McpAccessTokenService tokens = emptyTokens();
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);

		button(section, "Token hinzufügen").click();

		assertThat(section.label()).isEqualTo("MCP > Sicherheit");
		assertThat(accessLevel(section).getValue()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
		assertThat(button(section, "Anlegen...").isEnabled()).isFalse();
		textField(section, "Bezeichnung").setValue("KI-Client");
		assertThat(button(section, "Anlegen...").isEnabled()).isTrue();
		components(section, DatePicker.class).getFirst().setValue(LocalDate.now().minusDays(1));
		assertThat(button(section, "Anlegen...").isEnabled()).isFalse();
		components(section, DatePicker.class).getFirst().clear();
		textField(section, "Bezeichnung").setValue("Client\nName");
		assertThat(button(section, "Anlegen...").isEnabled()).isFalse();
		verify(tokens, never()).create(any(), any(), any());
	}

	@Test
	void showsCreatedCredentialOnceAndKeepsOtherUnsavedRows() {
		final McpAccessTokenService tokens = emptyTokens();
		final McpAccessToken created = token("a", "Chat", McpAccessLevel.PSEUDONYMIZED, null, null);
		when(tokens.create("Chat", McpAccessLevel.PSEUDONYMIZED, null))
				.thenReturn(new McpAccessTokenService.CreatedToken(created, "new-secret"));
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);
		button(section, "Token hinzufügen").click();
		button(section, "Token hinzufügen").click();
		final List<TextField> names = textFields(section, "Bezeichnung");
		names.get(0).setValue(" Chat ");
		names.get(1).setValue("anderer Entwurf");

		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			button(section, "Anlegen...").click();

			verify(tokens).create(eq("Chat"), eq(McpAccessLevel.PSEUDONYMIZED), isNull());
			final Dialog dialog = components(ui, Dialog.class).getFirst();
			assertThat(dialog.isOpened()).isTrue();
			assertThat(textField(dialog, "Token für Chat").getValue()).isEqualTo("new-secret");
			assertThat(components(dialog, ClipboardCopyButton.class)).hasSize(1);
			assertThat(textFields(section, "Bezeichnung")).extracting(TextField::getValue)
					.containsExactly("Chat", "anderer Entwurf");
			assertThat(iconButton(section, "Speichern").isEnabled()).isFalse();

			button(dialog, "Token übernommen").click();

			assertThat(components(dialog, TextField.class)).isEmpty();
			assertThat(components(dialog, ClipboardCopyButton.class)).isEmpty();
			ComponentUtil.fireEvent(dialog, new Dialog.ClosedEvent(dialog, true));
			assertThat(components(ui, Dialog.class)).isEmpty();
			assertThat(components(section, TextField.class)).noneMatch(field -> field.getValue().equals("new-secret"));
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void savesOnlyChangedRowsAndPreservesPreciseExpiryWhenEditingMetadata() {
		final Instant expiry = Instant.now().plusSeconds(86400 * 30 + 123);
		final McpAccessToken original = token("a", "Chat", McpAccessLevel.FULL, expiry, null);
		final McpAccessToken updated = token("a", "Chat neu", McpAccessLevel.PSEUDONYMIZED, expiry, null);
		final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
		when(tokens.tokens()).thenReturn(List.of(original), List.of(updated));
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);

		assertThat(iconButton(section, "Speichern").isEnabled()).isFalse();
		textField(section, "Bezeichnung").setValue("Chat neu");
		accessLevel(section).setValue(McpAccessLevel.PSEUDONYMIZED);
		assertThat(iconButton(section, "Speichern").isEnabled()).isTrue();

		withUi(() -> iconButton(section, "Speichern").click());

		verify(tokens).update(original, "Chat neu", McpAccessLevel.PSEUDONYMIZED, expiry);
		assertThat(iconButton(section, "Speichern").isEnabled()).isFalse();
		assertThat(components(section, Span.class)).anyMatch(span -> span.getText().contains("Zuletzt verwendet: Noch nie"));
	}

	@Test
	void selectedExpiryIncludesTheEntireDay() {
		final McpAccessTokenService tokens = emptyTokens();
		final LocalDate day = LocalDate.now().plusDays(3);
		final Instant expiry = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
		when(tokens.create("Chat", McpAccessLevel.PSEUDONYMIZED, expiry))
				.thenReturn(new McpAccessTokenService.CreatedToken(
						token("a", "Chat", McpAccessLevel.PSEUDONYMIZED, expiry, null), "secret"));
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);
		button(section, "Token hinzufügen").click();
		textField(section, "Bezeichnung").setValue("Chat");
		components(section, DatePicker.class).getFirst().setValue(day);

		withUi(() -> button(section, "Anlegen...").click());

		verify(tokens).create("Chat", McpAccessLevel.PSEUDONYMIZED, expiry);
		assertThat(components(section, DatePicker.class).getFirst().getValue()).isEqualTo(day);
	}

	@Test
	void revokesImmediatelyAfterConfirmationAndDoesNotSaveOtherDrafts() {
		final McpAccessToken original = token("a", "Chat", McpAccessLevel.FULL, null, null);
		final McpAccessToken revoked = token("a", "Chat", McpAccessLevel.FULL, null, Instant.now());
		final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
		when(tokens.tokens()).thenReturn(List.of(original), List.of(revoked));
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);
		button(section, "Token hinzufügen").click();
		textFields(section, "Bezeichnung").get(1).setValue("Entwurf");
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			iconButton(section, "Token widerrufen...").click();
			verify(tokens, never()).revoke(any());
			final ConfirmDialog confirmation = components(ui, ConfirmDialog.class).getFirst();
			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			verify(tokens).revoke("a");
			verify(tokens, never()).create(any(), any(), any());
			assertThat(textFields(section, "Bezeichnung").get(0).isReadOnly()).isTrue();
			assertThat(textFields(section, "Bezeichnung").get(1).getValue()).isEqualTo("Entwurf");
			assertThat(components(section, Span.class)).anyMatch(span -> span.getText().contains("Widerrufen:"));
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void refreshesUsageMetadataOnRevisitWithoutOverwritingUnsavedEdits() {
		final McpAccessToken original = token("a", "Chat", McpAccessLevel.FULL, null, null);
		final McpAccessToken used = new McpAccessToken(original.id(), original.name(), original.accessLevel(),
				original.createdAt(), Instant.now(), null, null);
		final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
		when(tokens.tokens()).thenReturn(List.of(original), List.of(used));
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);
		textField(section, "Bezeichnung").setValue("ungespeicherte Änderung");
		button(section, "Token hinzufügen").click();
		textFields(section, "Bezeichnung").get(1).setValue("neuer Entwurf");

		section.content();

		assertThat(textFields(section, "Bezeichnung")).extracting(TextField::getValue)
				.containsExactly("ungespeicherte Änderung", "neuer Entwurf");
		assertThat(components(section, Span.class)).anyMatch(span -> span.getText().contains("Zuletzt verwendet:")
				&& !span.getText().contains("Noch nie"));
		assertThat(iconButton(section, "Speichern").isEnabled()).isTrue();
	}

	@Test
	void staleDirtyEditorCannotRestoreFullAccessAfterAnotherSessionDowngradesIt() {
		final Instant expiry = Instant.now().plusSeconds(86400 * 30 + 123).plusNanos(1);
		final McpAccessToken original = token("a", "Chat", McpAccessLevel.FULL, expiry, null);
		final McpAccessToken downgraded = token("a", "Chat", McpAccessLevel.PSEUDONYMIZED, expiry, null);
		final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
		when(tokens.tokens()).thenReturn(List.of(original), List.of(downgraded), List.of(downgraded));
		doThrow(new McpAccessTokenConflictException()).when(tokens)
				.update(original, "Meine Änderung", McpAccessLevel.FULL, expiry);
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);
		textField(section, "Bezeichnung").setValue("Meine Änderung");

		section.content();

		assertThat(accessLevel(section).getValue()).isEqualTo(McpAccessLevel.FULL);
		final UI ui = new UI();
		UI.setCurrent(ui);
		try {
			iconButton(section, "Speichern").click();

			verify(tokens).update(original, "Meine Änderung", McpAccessLevel.FULL, expiry);
			verify(tokens, never()).update(downgraded, "Meine Änderung", McpAccessLevel.FULL, expiry);
			assertThat(textField(section, "Bezeichnung").getValue()).isEqualTo("Meine Änderung");
			assertThat(accessLevel(section).getValue()).isEqualTo(McpAccessLevel.FULL);
			assertThat(iconButton(section, "Speichern").isEnabled()).isFalse();
			assertThat(components(section, Span.class)).anyMatch(span -> span.getText()
					.contains("Deine Änderungen bleiben erhalten. Lade den aktuellen Stand und prüfe die Zugriffsstufe"));

			button(section, "Aktuellen Stand laden...").click();

			assertThat(textField(section, "Bezeichnung").getValue()).isEqualTo("Meine Änderung");
			final ConfirmDialog confirmation = components(ui, ConfirmDialog.class).getFirst();
			assertThat(confirmation.getElement().getProperty("header")).contains("Deine Änderungen verwerfen");
			ComponentUtil.fireEvent(confirmation, new ConfirmDialog.ConfirmEvent(confirmation, false));

			assertThat(textField(section, "Bezeichnung").getValue()).isEqualTo("Chat");
			assertThat(accessLevel(section).getValue()).isEqualTo(McpAccessLevel.PSEUDONYMIZED);
			assertThat(iconButton(section, "Speichern").isEnabled()).isFalse();
		} finally {
			UI.setCurrent(null);
		}
	}

	@Test
	void expiredTokenMustGetANewExpiryOrNoExpiryBeforeMetadataCanBeSaved() {
		final McpAccessTokenService tokens = mock(McpAccessTokenService.class);
		when(tokens.tokens()).thenReturn(List.of(token("a", "Chat", McpAccessLevel.FULL,
				Instant.now().minusSeconds(86400), null)));
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(tokens);
		textField(section, "Bezeichnung").setValue("Chat neu");

		assertThat(iconButton(section, "Speichern").isEnabled()).isFalse();
		assertThat(components(section, DatePicker.class).getFirst().getErrorMessage())
				.isEqualTo("Bitte wähle heute oder einen späteren Tag.");
		components(section, DatePicker.class).getFirst().clear();

		assertThat(iconButton(section, "Speichern").isEnabled()).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	void retainsTokenDraftWhenNavigatingToAnotherSettingsSection() {
		final McpSecuritySettingsSection section = new McpSecuritySettingsSection(emptyTokens());
		final SystemSettingsSection other = new SystemSettingsSection() {
			private final Div content = new Div();

			@Override
			public String label() {
				return "MCP > Dateibehandlung";
			}

			@Override
			public Component content() {
				return content;
			}
		};
		final SystemSettingsTab systemSettings = new SystemSettingsTab(List.of(section, other));
		button(section, "Token hinzufügen").click();
		textField(section, "Bezeichnung").setValue("Entwurf");
		final Grid<SystemSettingsSection> navigation = (Grid<SystemSettingsSection>) components(systemSettings, Grid.class)
				.getFirst();

		navigation.select(other);
		navigation.select(section);

		assertThat(textField(section, "Bezeichnung").getValue()).isEqualTo("Entwurf");
		assertThat(components(section, SettingsLineItem.class)).hasSize(1);
	}

	private static McpAccessTokenService emptyTokens() {
		final McpAccessTokenService service = mock(McpAccessTokenService.class);
		when(service.tokens()).thenReturn(List.of());
		return service;
	}

	private static McpAccessToken token(final String id, final String name, final McpAccessLevel level,
			final Instant expiry, final Instant revoked) {
		return new McpAccessToken(id, name, level, Instant.parse("2026-01-01T10:00:00Z"), null, expiry, revoked);
	}

	private static void withUi(final Runnable action) {
		UI.setCurrent(new UI());
		try {
			action.run();
		} finally {
			UI.setCurrent(null);
		}
	}

	@SuppressWarnings("unchecked")
	private static Select<McpAccessLevel> accessLevel(final Component root) {
		return (Select<McpAccessLevel>) components(root, Select.class).getFirst();
	}

	private static TextField textField(final Component root, final String label) {
		return textFields(root, label).getFirst();
	}

	private static List<TextField> textFields(final Component root, final String label) {
		return components(root, TextField.class).stream().filter(field -> label.equals(field.getLabel())).toList();
	}

	private static Button button(final Component root, final String text) {
		return components(root, Button.class).stream().filter(button -> text.equals(button.getText())).findFirst()
				.orElseThrow();
	}

	private static Button iconButton(final Component root, final String label) {
		return components(root, Button.class).stream().filter(button -> button.getAriaLabel().orElse("").equals(label))
				.findFirst().orElseThrow();
	}

	private static <T extends Component> List<T> components(final Component root, final Class<T> type) {
		return Stream.concat(Stream.of(root), root.getChildren().flatMap(child -> components(child, type).stream()))
				.filter(type::isInstance).map(type::cast).toList();
	}
}
