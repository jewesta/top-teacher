package de.westarps.topteacher.ui.view;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.annotation.Order;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.spring.annotation.SpringComponent;
import com.vaadin.flow.spring.annotation.UIScope;

import de.westarps.topteacher.backend.mcp.McpAccessTokenConflictException;
import de.westarps.topteacher.backend.mcp.McpAccessTokenService;
import de.westarps.topteacher.model.McpAccessLevel;
import de.westarps.topteacher.model.McpAccessToken;
import de.westarps.topteacher.ui.component.ActionToolbar;
import de.westarps.topteacher.ui.component.Buttons;
import de.westarps.topteacher.ui.component.ClipboardCopyButton;
import de.westarps.topteacher.ui.component.SettingsLineItem;
import de.westarps.topteacher.ui.component.TopTeacherDialogs;

@Order(15)
@UIScope
@SpringComponent
public class McpSecuritySettingsSection extends VerticalLayout implements SystemSettingsSection {

	private static final ZoneId DISPLAY_ZONE = ZoneId.systemDefault();
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
			.withZone(DISPLAY_ZONE);
	private static final String ACCESS_DESCRIPTION = "Pseudonymisiert: Schülernamen aus TopTeacher erscheinen ausschließlich als Initialen und ID. Die KI kann keine Namen suchen und keine Schüler anlegen, importieren oder umbenennen. Alle übrigen MCP-Aktionen bleiben verfügbar. Vollzugriff zeigt auch Schülernamen.";
	private static final String CONTENT_PROTOCOL = "Entferne Schülernamen selbst aus Kommentaren, Materialien, Bildern und Uploads, bevor Du sie mit der KI teilst. Die Pseudonymisierung schützt die Schülernamensfelder in TopTeacher; frei eingegebene Inhalte und Dateien werden nicht automatisch anonymisiert.";

	private final McpAccessTokenService tokens;
	private final List<TokenRow> rows = new ArrayList<>();
	private final VerticalLayout tokenList = new VerticalLayout();
	private final Span emptyState = new Span("Noch keine MCP-Zugriffstokens eingerichtet.");

	public McpSecuritySettingsSection(final McpAccessTokenService tokens) {
		this.tokens = tokens;
		configureContent();
		for (final McpAccessToken token : tokens.tokens()) {
			addRow(token);
		}
		updateEmptyState();
	}

	@Override
	public String label() {
		return "MCP > Sicherheit";
	}

	@Override
	public Component content() {
		refreshTokenMetadata();
		return this;
	}

	private void refreshTokenMetadata() {
		final List<McpAccessToken> currentTokens = tokens.tokens();
		for (final TokenRow row : rows) {
			if (row.token == null) {
				continue;
			}
			currentTokens.stream().filter(current -> current.id().equals(row.token.id())).findFirst()
					.ifPresent(current -> {
						if (current.revokedAt() != null || row.snapshot().equals(row.loadedSnapshot)) {
							row.loadToken(current);
							row.updateButtonState();
						} else {
							row.token = current;
							row.metadata.setText(row.metadataText());
						}
					});
		}
		for (final McpAccessToken current : currentTokens) {
			if (rows.stream().noneMatch(row -> row.token != null && row.token.id().equals(current.id()))) {
				addRow(current);
			}
		}
	}

	private void configureContent() {
		addClassNames("tt-settings-content", "tt-mcp-security-settings-content");
		setPadding(false);
		setSpacing(true);
		setWidthFull();

		final Button addButton = Buttons.add("Token hinzufügen", event -> addRow(null));
		final ActionToolbar actions = new ActionToolbar(addButton);
		actions.addClassName("tt-settings-actions");
		final Span accessDescription = new Span(ACCESS_DESCRIPTION);
		accessDescription.addClassName("tt-settings-description");
		final Span contentProtocol = new Span(CONTENT_PROTOCOL);
		contentProtocol.addClassName("tt-settings-description");
		final Span credentialDescription = new Span(
				"Lege für jeden Client oder Zweck einen eigenen Token an. Der vollständige Token wird nur einmal beim Anlegen angezeigt. Ein Widerruf gilt sofort, auch für bestehende MCP-Verbindungen.");
		credentialDescription.addClassName("tt-settings-description");

		tokenList.addClassName("tt-mcp-token-list");
		tokenList.setPadding(false);
		tokenList.setSpacing(true);
		tokenList.setWidthFull();
		emptyState.addClassName("tt-settings-description");
		add(actions, credentialDescription, accessDescription, contentProtocol, tokenList);
	}

	private void addRow(final McpAccessToken token) {
		final TokenRow row = new TokenRow(token);
		rows.add(row);
		tokenList.add(row);
		updateEmptyState();
	}

	private void removeDraft(final TokenRow row) {
		rows.remove(row);
		tokenList.remove(row);
		updateEmptyState();
	}

	private void updateEmptyState() {
		if (rows.isEmpty()) {
			if (emptyState.getParent().isEmpty()) {
				tokenList.add(emptyState);
			}
		} else {
			emptyState.getElement().removeFromParent();
		}
	}

	private void showCredential(final McpAccessTokenService.CreatedToken created) {
		final Dialog dialog = TopTeacherDialogs.create("MCP-Zugriffstoken angelegt");
		dialog.addClassName("tt-mcp-token-credential-dialog");
		dialog.setCloseOnEsc(false);
		dialog.setCloseOnOutsideClick(false);
		final Paragraph explanation = new Paragraph(
				"Kopiere diesen Token jetzt in Deinen MCP-Client. Sobald Du diesen Dialog schließt, kannst Du ihn in TopTeacher nicht erneut anzeigen. Bewahre ihn vertraulich auf.");
		final TextField credential = new TextField("Token für " + created.metadata().name());
		credential.addClassName("tt-mcp-token-credential");
		credential.setReadOnly(true);
		credential.setValue(created.credential());
		credential.setWidthFull();
		final ClipboardCopyButton copy = new ClipboardCopyButton(created.credential(), "Token kopieren",
				"Token kopiert.", "Kopieren nicht möglich. Markiere den Token und kopiere ihn selbst.");
		final HorizontalLayout credentialLine = new HorizontalLayout(credential, copy);
		credentialLine.setAlignItems(Alignment.CENTER);
		credentialLine.setWidthFull();
		credentialLine.setFlexGrow(1, credential);
		final Button close = new Button("Token übernommen", event -> dialog.close());
		dialog.add(explanation, credentialLine, TopTeacherDialogs.buttonBar(null, close));
		dialog.addOpenedChangeListener(event -> {
			if (!event.isOpened()) {
				/* Discard the sole UI copy instead of retaining a hidden credential dialog. */
				credential.clear();
				dialog.removeAll();
			}
		});
		dialog.addClosedListener(event -> dialog.getElement().removeFromParent());
		final UI ui = UI.getCurrent();
		if (ui != null) {
			ui.add(dialog);
		}
		dialog.open();
	}

	private final class TokenRow extends VerticalLayout {

		private final Binder<TokenFormData> binder = new Binder<>();
		private final TextField name = new TextField("Bezeichnung");
		private final Select<McpAccessLevel> accessLevel = new Select<>("Zugriff");
		private final DatePicker expiry = new DatePicker("Gültig bis (optional)");
		private final Span metadata = new Span();
		private final Button saveButton = Buttons.save(event -> save());
		private final Button removeButton;
		private final HorizontalLayout conflictNotice = new HorizontalLayout();
		private McpAccessToken token;
		/* Keep the edit baseline when background usage metadata refreshes a dirty row. */
		private McpAccessToken expectedToken;
		private TokenSnapshot loadedSnapshot;

		private TokenRow(final McpAccessToken token) {
			this.token = token;
			addClassName("tt-mcp-token-row");
			setPadding(false);
			setSpacing(true);
			setWidthFull();

			name.setMaxLength(McpAccessToken.MAXIMUM_NAME_LENGTH);
			name.setClearButtonVisible(true);
			name.setValueChangeMode(ValueChangeMode.EAGER);
			name.setWidthFull();
			accessLevel.setItems(McpAccessLevel.PSEUDONYMIZED, McpAccessLevel.FULL);
			accessLevel.setItemLabelGenerator(level -> switch (level) {
			case PSEUDONYMIZED -> "Pseudonymisiert";
			case FULL -> "Vollzugriff";
			});
			accessLevel.setEmptySelectionAllowed(false);
			accessLevel.setWidthFull();
			expiry.setI18n(germanDatePickerI18n());
			expiry.setClearButtonVisible(true);
			expiry.setHelperText("Gilt bis zum Ende dieses Tages.");
			expiry.setWidthFull();

			binder.forField(name).withConverter(McpSecuritySettingsSection::strip, value -> value)
					.asRequired("Bitte gib eine Bezeichnung an.")
					.withValidator(value -> value.length() <= McpAccessToken.MAXIMUM_NAME_LENGTH,
							"Die Bezeichnung ist zu lang.")
					.withValidator(value -> value.codePoints().noneMatch(Character::isISOControl),
							"Die Bezeichnung darf keine Steuerzeichen enthalten.")
					.bind(TokenFormData::getName, TokenFormData::setName);
			binder.forField(accessLevel).asRequired("Bitte wähle eine Zugriffsstufe.")
					.bind(TokenFormData::getAccessLevel, TokenFormData::setAccessLevel);
			binder.forField(expiry)
					.withValidator(value -> value == null || !value.isBefore(LocalDate.now(DISPLAY_ZONE)),
							"Bitte wähle heute oder einen späteren Tag.")
					.bind(TokenFormData::getExpiry, TokenFormData::setExpiry);

			removeButton = Buttons.icon("Entwurf entfernen", VaadinIcon.TRASH, event -> {
				if (this.token == null) {
					removeDraft(this);
				} else {
					confirmRevocation();
				}
			});
			final SettingsLineItem line = new SettingsLineItem();
			line.addFields(name, accessLevel, expiry);
			line.addActions(saveButton, removeButton);
			metadata.addClassName("tt-mcp-token-metadata");
			final Span conflictExplanation = new Span(
					"Dieser Token wurde inzwischen in einer anderen Sitzung geändert. Deine Änderungen bleiben erhalten. Lade den aktuellen Stand und prüfe die Zugriffsstufe, bevor Du erneut speicherst.");
			final Button reloadButton = Buttons.dialogOpener("Aktuellen Stand laden", VaadinIcon.REFRESH,
					event -> confirmReload());
			conflictNotice.setPadding(false);
			conflictNotice.setAlignItems(Alignment.CENTER);
			conflictNotice.setWidthFull();
			conflictNotice.add(conflictExplanation, reloadButton);
			conflictNotice.setFlexGrow(1, conflictExplanation);
			conflictNotice.setVisible(false);
			add(line, metadata, conflictNotice);

			loadToken(token);
			binder.addValueChangeListener(event -> updateButtonState());
			updateButtonState();
		}

		private void loadToken(final McpAccessToken currentToken) {
			token = currentToken;
			expectedToken = currentToken;
			conflictNotice.setVisible(false);
			binder.setBean(new TokenFormData(token == null ? "" : token.name(),
					token == null ? McpAccessLevel.PSEUDONYMIZED : token.accessLevel(),
					token == null ? null : expiryDate(token.expiresAt())));
			loadedSnapshot = snapshot();
			saveButton.setText(token == null ? "Anlegen..." : "Speichern");
			saveButton.setIcon((token == null ? VaadinIcon.PLUS : VaadinIcon.CHECK).create());
			Buttons.setIconLabel(removeButton, token == null ? "Entwurf entfernen" : "Token widerrufen...");
			final boolean revoked = token != null && token.revokedAt() != null;
			name.setReadOnly(revoked);
			accessLevel.setReadOnly(revoked);
			expiry.setReadOnly(revoked);
			saveButton.setVisible(!revoked);
			removeButton.setVisible(!revoked);
			setClassName("tt-mcp-token-row-revoked", revoked);
			metadata.setText(metadataText());
		}

		private String metadataText() {
			if (token == null) {
				return "Neuer Token · noch nicht angelegt";
			}
			final String status = token.revokedAt() != null ? " · Widerrufen: " + timestamp(token.revokedAt())
					: token.expiresAt() != null && !token.expiresAt().isAfter(Instant.now()) ? " · Abgelaufen" : "";
			return "Erstellt: " + timestamp(token.createdAt()) + " · Zuletzt verwendet: "
					+ (token.lastUsedAt() == null ? "Noch nie" : timestamp(token.lastUsedAt())) + status;
		}

		private TokenSnapshot snapshot() {
			return new TokenSnapshot(strip(name.getValue()), accessLevel.getValue(), expiry.getValue());
		}

		private void updateButtonState() {
			final boolean editable = token == null || token.revokedAt() == null;
			final boolean changed = token == null || !snapshot().equals(loadedSnapshot);
			saveButton.setEnabled(editable && changed && !conflictNotice.isVisible() && binder.validate().isOk());
		}

		private Instant selectedExpiry() {
			if (expectedToken != null
					&& java.util.Objects.equals(expiry.getValue(), expiryDate(expectedToken.expiresAt()))) {
				return expectedToken.expiresAt();
			}
			return expiry.getValue() == null ? null
					: expiry.getValue().plusDays(1).atStartOfDay(DISPLAY_ZONE).toInstant();
		}

		private void save() {
			if (!saveButton.isEnabled() || !binder.validate().isOk()) {
				return;
			}
			try {
				if (token == null) {
					final McpAccessTokenService.CreatedToken created = tokens.create(strip(name.getValue()),
							accessLevel.getValue(), selectedExpiry());
					loadToken(created.metadata());
					updateButtonState();
					showCredential(created);
				} else {
					tokens.update(expectedToken, strip(name.getValue()), accessLevel.getValue(), selectedExpiry());
					final McpAccessToken updated = tokens.tokens().stream()
							.filter(current -> current.id().equals(token.id())).findFirst().orElseThrow();
					loadToken(updated);
					updateButtonState();
					Notification.show("MCP-Zugriffstoken gespeichert.");
				}
			} catch (final McpAccessTokenConflictException exception) {
				conflictNotice.setVisible(true);
				Notification.show(
						"Der Token wurde inzwischen geändert. Deine Änderungen bleiben erhalten. Lade den aktuellen Stand, bevor Du erneut speicherst.");
				updateButtonState();
			} catch (final IllegalArgumentException exception) {
				Notification.show("Token konnte nicht gespeichert werden. Prüfe Bezeichnung, Gültigkeit und Status.");
				updateButtonState();
			}
		}

		private void confirmReload() {
			final ConfirmDialog dialog = TopTeacherDialogs.discardConfirmation(
					"Aktuellen Token-Stand laden und Deine Änderungen verwerfen?", () -> {
						final var currentToken = tokens.tokens().stream()
								.filter(current -> current.id().equals(token.id())).findFirst();
						if (currentToken.isPresent()) {
							loadToken(currentToken.get());
							updateButtonState();
						} else {
							Notification.show("Dieser Token ist nicht mehr verfügbar. Deine Änderungen bleiben erhalten.");
						}
					});
			dialog.addClosedListener(event -> dialog.getElement().removeFromParent());
			final UI ui = UI.getCurrent();
			if (ui != null) {
				ui.add(dialog);
			}
			dialog.open();
		}

		private void confirmRevocation() {
			final ConfirmDialog dialog = new ConfirmDialog();
			dialog.setHeader("MCP-Zugriffstoken widerrufen?");
			dialog.setText("Der Token „" + token.name()
					+ "“ verliert sofort seinen Zugriff. Bestehende MCP-Verbindungen können ihn nicht weiter verwenden.");
			dialog.setCancelable(true);
			dialog.setCancelText("Abbrechen");
			dialog.setConfirmText("Widerrufen");
			dialog.setConfirmButtonTheme("error primary");
			dialog.addConfirmListener(event -> {
				tokens.revoke(token.id());
				final McpAccessToken revoked = tokens.tokens().stream()
						.filter(current -> current.id().equals(token.id())).findFirst().orElseThrow();
				loadToken(revoked);
				updateButtonState();
				dialog.close();
				Notification.show("MCP-Zugriffstoken widerrufen.");
			});
			dialog.addClosedListener(event -> dialog.getElement().removeFromParent());
			final UI ui = UI.getCurrent();
			if (ui != null) {
				ui.add(dialog);
			}
			dialog.open();
		}
	}

	private static String strip(final String value) {
		return value == null ? "" : value.strip();
	}

	private static String timestamp(final Instant value) {
		return TIMESTAMP_FORMAT.format(value);
	}

	private static LocalDate expiryDate(final Instant value) {
		return value == null ? null : value.minusNanos(1).atZone(DISPLAY_ZONE).toLocalDate();
	}

	private static DatePickerI18n germanDatePickerI18n() {
		return new DatePickerI18n()
				.setMonthNames(List.of("Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August",
						"September", "Oktober", "November", "Dezember"))
				.setWeekdays(List.of("Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"))
				.setWeekdaysShort(List.of("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")).setFirstDayOfWeek(1)
				.setDateFormat("dd.MM.yyyy").setToday("Heute").setCancel("Abbrechen")
				.setBadInputErrorMessage("Bitte gib ein gültiges Datum ein.");
	}

	private record TokenSnapshot(String name, McpAccessLevel accessLevel, LocalDate expiry) {
	}

	private static final class TokenFormData {

		private String name;
		private McpAccessLevel accessLevel;
		private LocalDate expiry;

		private TokenFormData(final String name, final McpAccessLevel accessLevel, final LocalDate expiry) {
			this.name = name;
			this.accessLevel = accessLevel;
			this.expiry = expiry;
		}

		private String getName() {
			return name;
		}

		private void setName(final String name) {
			this.name = name;
		}

		private McpAccessLevel getAccessLevel() {
			return accessLevel;
		}

		private void setAccessLevel(final McpAccessLevel accessLevel) {
			this.accessLevel = accessLevel;
		}

		private LocalDate getExpiry() {
			return expiry;
		}

		private void setExpiry(final LocalDate expiry) {
			this.expiry = expiry;
		}
	}
}
