package de.westarps.topteacher.ui.component.loe;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;

import de.westarps.topteacher.model.loe.LoeMaterialImage;

final class MaterialImageResources {

	private final Component owner;
	private final List<StreamRegistration> registrations = new ArrayList<>();

	MaterialImageResources(final Component owner) {
		this.owner = owner;
		owner.addDetachListener(event -> unregister());
	}

	Map<String, Map<String, Object>> register(final Collection<LoeMaterialImage> images) {
		unregister();
		final Map<String, Map<String, Object>> state = new LinkedHashMap<>();
		final VaadinSession session = VaadinSession.getCurrent();
		if (session == null || images == null) {
			return state;
		}
		for (final LoeMaterialImage image : images) {
			final String extension = image.mediaType().equals("image/png") ? ".png" : ".jpg";
			final StreamResource resource = new StreamResource(image.id() + extension,
					() -> new ByteArrayInputStream(image.data()));
			resource.setContentType(image.mediaType());
			resource.setCacheTime(0);
			final StreamRegistration registration = session.getResourceRegistry().registerResource(resource);
			registrations.add(registration);
			state.put(image.id(), Map.of("url", registration.getResourceUri().toASCIIString(), "pixelWidth",
					image.pixelWidth(), "pixelHeight", image.pixelHeight()));
		}
		return state;
	}

	private void unregister() {
		registrations.forEach(StreamRegistration::unregister);
		registrations.clear();
	}
}
