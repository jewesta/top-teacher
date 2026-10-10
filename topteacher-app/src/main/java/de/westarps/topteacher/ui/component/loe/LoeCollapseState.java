package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;

final class LoeCollapseState {

	private final LoeSectionComponents components;
	private final Set<String> collapsedDetails = new HashSet<>();
	private final Map<String, Details> detailsByKey = new HashMap<>();
	private final List<SectionToggleButton> sectionToggleButtons = new ArrayList<>();
	private final List<GroupToggleButton> groupToggleButtons = new ArrayList<>();

	LoeCollapseState(final LoeSectionComponents components) {
		this.components = components;
	}

	void clear() {
		collapsedDetails.clear();
	}

	void clearRenderedComponents() {
		detailsByKey.clear();
		sectionToggleButtons.clear();
		groupToggleButtons.clear();
	}

	void configure(final Details details, final String key, final String parentKey,
			final List<String> descendantKeys, final String sectionType, final String sectionTitle) {
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
		addHeaderToggleButtons(details, key, descendantKeys, sectionName(sectionType, sectionTitle));
		updateToggleButtons();
	}

	Button toggleAllButton(final List<String> detailKeys) {
		return groupToggleButton(detailKeys, "Alle Abschnitte einklappen",
				"Alle Abschnitte ausklappen", "toggle-all");
	}

	private void addHeaderToggleButtons(final Details details, final String key,
			final List<String> descendantKeys, final String sectionName) {
		if (!(details.getSummary() instanceof HorizontalLayout summary)) {
			throw new IllegalStateException("A tree section summary must be a HorizontalLayout.");
		}
		final Button sectionToggle = components.iconButton(collapseLabel(sectionName), VaadinIcon.ANGLE_DOWN,
				event -> details.setOpened(!details.isOpened()));
		sectionToggle.addClassName("tt-eh-header-toggle");
		sectionToggle.getElement().setAttribute("data-action", "toggle-section");
		sectionToggleButtons.add(new SectionToggleButton(sectionToggle, details, sectionName));
		summary.addComponentAtIndex(0, sectionToggle);

		if (descendantKeys.isEmpty()) {
			return;
		}
		final List<String> subtreeKeys = Stream.concat(Stream.of(key), descendantKeys.stream()).distinct().toList();
		final Button subtreeToggle = groupToggleButton(subtreeKeys, collapseSubtreeLabel(sectionName),
				expandSubtreeLabel(sectionName), "toggle-subtree");
		subtreeToggle.addClassName("tt-eh-header-toggle");
		summary.addComponentAtIndex(1, subtreeToggle);
	}

	private Button groupToggleButton(final List<String> detailKeys, final String collapseLabel,
			final String expandLabel, final String action) {
		final Button button = components.iconButton(collapseLabel, VaadinIcon.ANGLE_DOUBLE_DOWN,
				event -> toggle(detailKeys));
		button.getElement().setAttribute("data-action", action);
		groupToggleButtons.add(new GroupToggleButton(button, List.copyOf(detailKeys), collapseLabel, expandLabel));
		updateGroupToggleButton(button, detailKeys, collapseLabel, expandLabel);
		return button;
	}

	private void toggle(final List<String> keys) {
		if (allExpanded(keys)) {
			collapse(keys);
		} else {
			expand(keys);
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

	void expand(final List<String> keys) {
		keys.forEach(key -> {
			collapsedDetails.remove(key);
			final Details details = detailsByKey.get(key);
			if (details != null) {
				details.setOpened(true);
			}
		});
		updateToggleButtons();
	}

	private boolean allExpanded(final List<String> keys) {
		return !keys.isEmpty() && keys.stream().allMatch(this::isIndividuallyExpanded);
	}

	private boolean isIndividuallyExpanded(final String key) {
		final Details details = detailsByKey.get(key);
		return details == null ? !collapsedDetails.contains(key) : details.isOpened();
	}

	private void updateToggleButtons() {
		sectionToggleButtons.forEach(this::updateSectionToggleButton);
		groupToggleButtons.forEach(toggleButton -> updateGroupToggleButton(toggleButton.button(),
				toggleButton.detailKeys(), toggleButton.collapseLabel(), toggleButton.expandLabel()));
	}

	private void updateSectionToggleButton(final SectionToggleButton toggleButton) {
		final boolean opened = toggleButton.details().isOpened();
		final String label = opened ? collapseLabel(toggleButton.sectionName())
				: expandLabel(toggleButton.sectionName());
		toggleButton.button().setIcon((opened ? VaadinIcon.ANGLE_DOWN : VaadinIcon.ANGLE_RIGHT).create());
		toggleButton.button().setAriaLabel(label);
		toggleButton.button().setTooltipText(label);
		toggleButton.button().getElement().setAttribute("aria-expanded", Boolean.toString(opened));
	}

	private void updateGroupToggleButton(final Button button, final List<String> detailKeys,
			final String collapseLabel, final String expandLabel) {
		final boolean collapsesAll = allExpanded(detailKeys);
		final String label = collapsesAll ? collapseLabel : expandLabel;
		button.setIcon((collapsesAll ? VaadinIcon.ANGLE_DOUBLE_DOWN : VaadinIcon.ANGLE_DOUBLE_RIGHT).create());
		button.setAriaLabel(label);
		button.setTooltipText(label);
		button.setEnabled(!detailKeys.isEmpty());
		button.getElement().setAttribute("aria-expanded", Boolean.toString(collapsesAll));
	}

	private static String sectionName(final String sectionType, final String sectionTitle) {
		return sectionTitle == null || sectionTitle.isBlank()
				? sectionType
				: sectionType + " \u201e" + sectionTitle + "\u201c";
	}

	private static String collapseLabel(final String sectionName) {
		return sectionName + " einklappen";
	}

	private static String expandLabel(final String sectionName) {
		return sectionName + " ausklappen";
	}

	private static String collapseSubtreeLabel(final String sectionName) {
		return sectionName + " samt Unterpunkten einklappen";
	}

	private static String expandSubtreeLabel(final String sectionName) {
		return sectionName + " samt Unterpunkten ausklappen";
	}

	private record SectionToggleButton(Button button, Details details, String sectionName) {
	}

	private record GroupToggleButton(Button button, List<String> detailKeys, String collapseLabel,
			String expandLabel) {
	}
}
