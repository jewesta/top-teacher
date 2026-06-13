package de.westarps.topteacher.ui.component;

import java.util.Objects;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;

public class ClipboardCopyButton extends Composite<Button> {

	private static final String COPY_SUCCESS_EVENT = "tt-copy-success";
	private static final String COPY_FAILURE_EVENT = "tt-copy-failure";
	private static final String CLIPBOARD_COPY_SCRIPT = """
			this.onclick = async () => {
			   try {
			      await navigator.clipboard.writeText($0);
			      this.dispatchEvent(new CustomEvent($1));
			   } catch (error) {
			      this.dispatchEvent(new CustomEvent($2));
			   }
			};
			""";

	private final String textToCopy;

	public ClipboardCopyButton(final String textToCopy, final String label, final String successMessage,
			final String failureMessage) {
		this.textToCopy = Objects.requireNonNull(textToCopy, "textToCopy must not be null");
		configureButton(label, successMessage, failureMessage);
	}

	@Override
	protected Button initContent() {
		return new Button(VaadinIcon.COPY.create());
	}

	private void configureButton(final String label, final String successMessage, final String failureMessage) {
		final Button button = getContent();
		button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_ICON);
		button.setTooltipText(label);
		button.getElement().setAttribute("aria-label", label);
		button.addAttachListener(event -> installClipboardListener());
		/*
		 * These are custom DOM events dispatched by the browser-side clipboard handler.
		 * Vaadin has no typed component event for them, so we listen at element level
		 * to bridge the async result back into server-side notifications.
		 */
		button.getElement().addEventListener(COPY_SUCCESS_EVENT, event -> Notification.show(successMessage));
		button.getElement().addEventListener(COPY_FAILURE_EVENT, event -> Notification.show(failureMessage));
	}

	private void installClipboardListener() {
		/*
		 * Clipboard writes must run directly inside the browser click handler; a server
		 * round trip can lose the user activation and make the browser deny
		 * navigator.clipboard without prompting.
		 */
		getContent().getElement().executeJs(CLIPBOARD_COPY_SCRIPT, textToCopy, COPY_SUCCESS_EVENT, COPY_FAILURE_EVENT);
	}
}
