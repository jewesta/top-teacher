package de.westarps.topteacher.ui.component.eh;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.icon.VaadinIcon;

final class EhCollapseState {

	private final EhSectionComponents components;
	private final Set<String> collapsedDetails = new HashSet<>();
	private final Map<String, Details> detailsByKey = new HashMap<>();
	private final List<CollapseToggleButton> toggleButtons = new ArrayList<>();

	EhCollapseState(final EhSectionComponents components) {
		this.components = components;
	}

	void clear() {
		collapsedDetails.clear();
	}

	void clearRenderedComponents() {
		detailsByKey.clear();
		toggleButtons.clear();
	}

	void configure(final Details details, final String key) {
		detailsByKey.put(key, details);
		details.setOpened(!collapsedDetails.contains(key));
		details.addOpenedChangeListener(event -> {
			if (event.isOpened()) {
				collapsedDetails.remove(key);
			} else {
				collapsedDetails.add(key);
			}
			updateToggleButtons();
		});
	}

	Button toggleButton(final List<String> detailKeys) {
		final Button button = components.iconButton("Alles darunter einklappen", VaadinIcon.ANGLE_DOUBLE_DOWN,
				event -> toggle(detailKeys));
		button.getElement().setAttribute("data-action", "collapse-below");
		toggleButtons.add(new CollapseToggleButton(button, detailKeys));
		updateToggleButton(button, detailKeys);
		return button;
	}

	private void toggle(final List<String> keys) {
		if (allCollapsed(keys)) {
			expand(keys);
		} else {
			collapse(keys);
		}
	}

	private void collapse(final List<String> keys) {
		keys.forEach(key -> {
			collapsedDetails.add(key);
			final Details details = detailsByKey.get(key);
			if (details != null) {
				details.setOpened(false);
			}
		});
		updateToggleButtons();
	}

	private void expand(final List<String> keys) {
		keys.forEach(key -> {
			collapsedDetails.remove(key);
			final Details details = detailsByKey.get(key);
			if (details != null) {
				details.setOpened(true);
			}
		});
		updateToggleButtons();
	}

	private boolean allCollapsed(final List<String> keys) {
		return !keys.isEmpty() && keys.stream().allMatch(collapsedDetails::contains);
	}

	private void updateToggleButtons() {
		toggleButtons.forEach(toggleButton -> updateToggleButton(toggleButton.button(), toggleButton.detailKeys()));
	}

	private void updateToggleButton(final Button button, final List<String> detailKeys) {
		final boolean expandsAll = allCollapsed(detailKeys);
		final String label = expandsAll ? "Alles darunter ausklappen" : "Alles darunter einklappen";
		button.setIcon((expandsAll ? VaadinIcon.ANGLE_DOUBLE_RIGHT : VaadinIcon.ANGLE_DOUBLE_DOWN).create());
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.setEnabled(!detailKeys.isEmpty());
	}

	private record CollapseToggleButton(Button button, List<String> detailKeys) {
	}
}
