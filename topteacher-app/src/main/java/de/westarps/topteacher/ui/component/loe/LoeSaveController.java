package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.vaadin.flow.component.button.Button;

final class LoeSaveController {

	private final List<Button> dirtyButtons = new ArrayList<>();
	private BooleanSupplier dirtySupplier = () -> false;
	private Runnable saveAction = () -> {
	};
	private Runnable discardAction = () -> {
	};

	void setDirtySupplier(final BooleanSupplier dirtySupplier) {
		this.dirtySupplier = dirtySupplier;
		update();
	}

	void setSaveAction(final Runnable saveAction) {
		this.saveAction = saveAction;
	}

	void setDiscardAction(final Runnable discardAction) {
		this.discardAction = discardAction;
	}

	void clearButtons() {
		dirtyButtons.clear();
	}

	Button register(final Button button) {
		dirtyButtons.add(button);
		button.setEnabled(isDirty());
		return button;
	}

	void update() {
		final boolean dirty = isDirty();
		dirtyButtons.forEach(button -> button.setEnabled(dirty));
	}

	void save() {
		saveAction.run();
		update();
	}

	void discard() {
		discardAction.run();
		update();
	}

	private boolean isDirty() {
		return dirtySupplier.getAsBoolean();
	}
}
