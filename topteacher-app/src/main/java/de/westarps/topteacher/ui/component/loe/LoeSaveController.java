package de.westarps.topteacher.ui.component.loe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.vaadin.flow.component.button.Button;

final class LoeSaveController {

	private final List<Button> dirtyButtons = new ArrayList<>();
	private final List<Button> saveButtons = new ArrayList<>();
	private final List<Button> cleanButtons = new ArrayList<>();
	private BooleanSupplier dirtySupplier = () -> false;
	private BooleanSupplier saveAllowedSupplier = () -> true;
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

	void setSaveAllowedSupplier(final BooleanSupplier saveAllowedSupplier) {
		this.saveAllowedSupplier = saveAllowedSupplier;
		update();
	}

	void setDiscardAction(final Runnable discardAction) {
		this.discardAction = discardAction;
	}

	void clearButtons() {
		dirtyButtons.clear();
		saveButtons.clear();
		cleanButtons.clear();
	}

	Button register(final Button button) {
		dirtyButtons.add(button);
		button.setEnabled(isDirty());
		return button;
	}

	Button registerSave(final Button button) {
		saveButtons.add(button);
		button.setEnabled(isDirty() && isSaveAllowed());
		return button;
	}

	Button registerClean(final Button button) {
		cleanButtons.add(button);
		button.setEnabled(!isDirty());
		return button;
	}

	void update() {
		final boolean dirty = isDirty();
		dirtyButtons.forEach(button -> button.setEnabled(dirty));
		saveButtons.forEach(button -> button.setEnabled(dirty && isSaveAllowed()));
		cleanButtons.forEach(button -> button.setEnabled(!dirty));
	}

	void save() {
		if (!isSaveAllowed()) {
			update();
			return;
		}
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

	private boolean isSaveAllowed() {
		return saveAllowedSupplier.getAsBoolean();
	}
}
