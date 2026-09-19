package de.westarps.topteacher.ui.component;

import java.util.List;

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
		return new DirtySaveButton(binder, saveButton);
	}

	public static final class DirtySaveButton {

		private final Binder<?> binder;
		private final Button saveButton;
		private List<Object> cleanValues = List.of();

		private DirtySaveButton(final Binder<?> binder, final Button saveButton) {
			this.binder = binder;
			this.saveButton = saveButton;
			binder.setChangeDetectionEnabled(true);
			binder.addValueChangeListener(event -> update());
			reset();
		}

		public void reset() {
			cleanValues = currentValues();
			update();
		}

		private void update() {
			saveButton.setEnabled(isDirty());
		}

		private boolean isDirty() {
			return !currentValues().equals(cleanValues);
		}

		private List<Object> currentValues() {
			return binder.getFields().map(field -> (Object) field.getValue()).toList();
		}
	}
}
