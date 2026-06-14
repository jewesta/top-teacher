package de.westarps.topteacher.ui.component;

import java.util.List;
import java.util.function.BooleanSupplier;

import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.data.binder.Binder;

public final class FormBinders {

	private FormBinders() {
	}

	public static void clearValidation(final Binder<?> binder) {
		binder.getFields().filter(HasValidation.class::isInstance).map(HasValidation.class::cast)
				.forEach(field -> field.setInvalid(false));
	}

	public static DirtySaveButton bindDirtySaveButton(final Binder<?> binder, final Button saveButton) {
		return bindDirtySaveButton(binder, saveButton, () -> true);
	}

	public static DirtySaveButton bindDirtySaveButton(final Binder<?> binder, final Button saveButton,
			final BooleanSupplier enabledWhenDirty) {
		return new DirtySaveButton(binder, saveButton, enabledWhenDirty);
	}

	public static final class DirtySaveButton {

		private final Binder<?> binder;
		private final Button saveButton;
		private final BooleanSupplier enabledWhenDirty;
		private List<Object> cleanValues = List.of();

		private DirtySaveButton(final Binder<?> binder, final Button saveButton,
				final BooleanSupplier enabledWhenDirty) {
			this.binder = binder;
			this.saveButton = saveButton;
			this.enabledWhenDirty = enabledWhenDirty;
			binder.setChangeDetectionEnabled(true);
			binder.addValueChangeListener(event -> update());
			reset();
		}

		public void reset() {
			cleanValues = currentValues();
			update();
		}

		public void update() {
			saveButton.setEnabled(enabledWhenDirty.getAsBoolean() && isDirty());
		}

		public boolean isDirty() {
			return !currentValues().equals(cleanValues);
		}

		private List<Object> currentValues() {
			return binder.getFields().map(field -> (Object) field.getValue()).toList();
		}
	}
}
