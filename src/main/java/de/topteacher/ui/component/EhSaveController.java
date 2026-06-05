package de.topteacher.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.vaadin.flow.component.button.Button;

final class EhSaveController {

	private final List<Button> saveButtons = new ArrayList<>();
	private BooleanSupplier dirtySupplier = () -> false;
	private Runnable saveAction = () -> {
	};

	void setDirtySupplier(final BooleanSupplier dirtySupplier) {
		this.dirtySupplier = dirtySupplier;
		update();
	}

	void setSaveAction(final Runnable saveAction) {
		this.saveAction = saveAction;
	}

	void clearButtons() {
		saveButtons.clear();
	}

	Button register(final Button button) {
		saveButtons.add(button);
		button.setEnabled(isDirty());
		return button;
	}

	void update() {
		final boolean dirty = isDirty();
		saveButtons.forEach(button -> button.setEnabled(dirty));
	}

	void save() {
		saveAction.run();
		update();
	}

	private boolean isDirty() {
		return dirtySupplier.getAsBoolean();
	}
}
