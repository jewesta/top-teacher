package de.westarps.topteacher.ui.component;

import java.util.Objects;

import com.vaadin.flow.component.ClientCallable;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.icon.VaadinIcon;

public class FullscreenButton extends Button {

	private static final String FULLSCREEN_TARGET_CLASS_NAME = "tt-fullscreen-target";
	private static final String ENTER_LABEL = "Vollbild";
	private static final String EXIT_LABEL = "Vollbild verlassen";

	private final Component target;
	private boolean fullscreenActive;

	public FullscreenButton(final Component target) {
		this.target = Objects.requireNonNull(target, "target must not be null");
		target.addClassName(FULLSCREEN_TARGET_CLASS_NAME);

		addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY_INLINE);
		addClickListener(event -> toggleFullscreen());
		addAttachListener(event -> installFullscreenListener());
		addDetachListener(event -> removeFullscreenListener());
		updateButton();
	}

	private void toggleFullscreen() {
		getElement().executeJs("""
				const target = $0;
				const fullscreenElement = document.fullscreenElement || document.webkitFullscreenElement;
				if (fullscreenElement === target) {
					const exitFullscreen = document.exitFullscreen || document.webkitExitFullscreen;
					if (exitFullscreen) {
						exitFullscreen.call(document);
					}
					return;
				}
				const requestFullscreen = target.requestFullscreen || target.webkitRequestFullscreen;
				if (requestFullscreen) {
					requestFullscreen.call(target);
				}
				""", target.getElement());
	}

	private void installFullscreenListener() {
		getElement().executeJs("""
				const button = this;
				const target = $0;
				if (button.__ttFullscreenListener) {
					return;
				}
				button.__ttFullscreenListener = () => {
					const fullscreenElement = document.fullscreenElement || document.webkitFullscreenElement;
					button.$server.setFullscreenActive(fullscreenElement === target);
				};
				document.addEventListener('fullscreenchange', button.__ttFullscreenListener);
				document.addEventListener('webkitfullscreenchange', button.__ttFullscreenListener);
				""", target.getElement());
	}

	private void removeFullscreenListener() {
		getElement().executeJs("""
				if (!this.__ttFullscreenListener) {
					return;
				}
				document.removeEventListener('fullscreenchange', this.__ttFullscreenListener);
				document.removeEventListener('webkitfullscreenchange', this.__ttFullscreenListener);
				delete this.__ttFullscreenListener;
				""");
	}

	@ClientCallable
	public void setFullscreenActive(final boolean fullscreenActive) {
		this.fullscreenActive = fullscreenActive;
		updateButton();
	}

	private void updateButton() {
		final String label = fullscreenActive ? EXIT_LABEL : ENTER_LABEL;
		setIcon((fullscreenActive ? VaadinIcon.COMPRESS_SQUARE : VaadinIcon.EXPAND_FULL).create());
		setAriaLabel(label);
		setTooltipText(label);
	}
}
