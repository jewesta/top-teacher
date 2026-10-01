package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.function.SerializableConsumer;
import com.vaadin.flow.shared.Registration;

import de.westarps.vaadin.markdown.MarkdownViewer;
import tools.jackson.databind.JsonNode;

@SuppressWarnings("serial")
@JsModule("./tt-criterion-markdown.tsx")
final class CriterionMarkdownViewer extends MarkdownViewer {

	private final Map<String, AwardState> criterionAwards = new HashMap<>();
	private final Set<String> highlightedCriterionKeys = new LinkedHashSet<>();

	CriterionMarkdownViewer(final String content) {
		super(content);
		setExtensionIds(List.of(CriterionMarkdownEditor.EXTENSION_ID));
		updateExtensionState();
	}

	Set<String> getCheckedCriterionKeys() {
		return criterionAwards.entrySet().stream()
				.filter(entry -> entry.getValue().awardedUnits() == entry.getValue().pointUnits())
				.map(Map.Entry::getKey).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	void setCriterionAwards(final Collection<AwardState> awards) {
		criterionAwards.clear();
		if (awards != null) {
			awards.stream().filter(Objects::nonNull).forEach(award -> criterionAwards.put(award.key(), award));
		}
		updateExtensionState();
	}

	void setHighlightedCriterionKeys(final Collection<String> keys) {
		highlightedCriterionKeys.clear();
		if (keys != null) {
			keys.stream().filter(Objects::nonNull).filter(key -> !key.isBlank())
					.forEach(highlightedCriterionKeys::add);
		}
		updateExtensionState();
	}

	Registration addCriterionAwardChangeListener(final SerializableConsumer<AwardChange> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"criterion-award-changed".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			final String key = data.path("event.detail.key").asString("");
			if (!key.isBlank()) {
				listener.accept(new AwardChange(key, data.path("event.detail.pointUnits").asInt(0)));
			}
		}).addEventData("event.detail.name").addEventData("event.detail.key")
				.addEventData("event.detail.pointUnits");
	}

	Registration addCriterionHighlightChangeListener(final SerializableConsumer<HighlightChange> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"criterion-highlight-changed".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			final String key = data.path("event.detail.key").asString("");
			if (!key.isBlank()) {
				listener.accept(new HighlightChange(key, data.path("event.detail.active").asBoolean(false)));
			}
		}).addEventData("event.detail.name").addEventData("event.detail.key")
				.addEventData("event.detail.active");
	}

	private void updateExtensionState() {
		final Map<String, Map<String, Object>> awards = new HashMap<>();
		criterionAwards.forEach((key, award) -> awards.put(key, Map.of("label", award.label(), "pointUnits",
				award.pointUnits(), "awardedUnits", award.awardedUnits(), "maxAwardableUnits",
				award.maxAwardableUnits())));
		setExtensionState(Map.of("criterionCheckboxes", true, "criterionAwards", awards,
				"highlightedCriterionKeys", List.copyOf(highlightedCriterionKeys)));
	}

	record AwardState(String key, String label, int pointUnits, int awardedUnits, int maxAwardableUnits) {
	}

	record AwardChange(String key, int pointUnits) {
	}

	record HighlightChange(String key, boolean active) {
	}
}
