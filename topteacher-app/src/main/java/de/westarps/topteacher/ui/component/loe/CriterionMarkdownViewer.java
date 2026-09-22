package de.westarps.topteacher.ui.component.loe;

import java.util.Collection;
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

	private final Set<String> checkedCriterionKeys = new LinkedHashSet<>();

	CriterionMarkdownViewer(final String content) {
		super(content);
		setExtensionIds(List.of(CriterionMarkdownEditor.EXTENSION_ID));
		updateExtensionState();
	}

	Set<String> getCheckedCriterionKeys() {
		return Set.copyOf(checkedCriterionKeys);
	}

	void setCheckedCriterionKeys(final Collection<String> keys) {
		checkedCriterionKeys.clear();
		if (keys != null) {
			keys.stream().filter(Objects::nonNull).filter(key -> !key.isBlank()).forEach(checkedCriterionKeys::add);
		}
		updateExtensionState();
	}

	Registration addCriterionCheckedChangeListener(final SerializableConsumer<CheckedChange> listener) {
		Objects.requireNonNull(listener, "listener must not be null");
		return getElement().addEventListener("markdown-extension-event", event -> {
			final JsonNode data = event.getEventData();
			if (!"criterion-checked-changed".equals(data.path("event.detail.name").asString(""))) {
				return;
			}
			final String key = data.path("event.detail.key").asString("");
			if (!key.isBlank()) {
				listener.accept(new CheckedChange(key, data.path("event.detail.checked").asBoolean(false)));
			}
		}).addEventData("event.detail.name").addEventData("event.detail.key")
				.addEventData("event.detail.checked");
	}

	private void updateExtensionState() {
		setExtensionState(Map.of("criterionCheckboxes", true, "checkedCriterionKeys", List.copyOf(checkedCriterionKeys)));
	}

	record CheckedChange(String key, boolean checked) {
	}
}
