package de.westarps.topteacher.ui.component;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

class FormBindersTests {

	@Test
	void dirtySaveButtonFollowsBinderChangesAndReverts() {
		final TextField name = new TextField();
		final Button saveButton = new Button("Speichern");
		final Binder<FormData> binder = new Binder<>();
		binder.forField(name).bind(FormData::getName, FormData::setName);
		final FormBinders.DirtySaveButton dirtySaveButton = FormBinders.bindDirtySaveButton(binder, saveButton);

		binder.readBean(new FormData("Bio"));
		dirtySaveButton.reset();

		assertThat(saveButton.isEnabled()).isFalse();

		name.setValue("Physik");

		assertThat(saveButton.isEnabled()).isTrue();

		name.setValue("Bio");

		assertThat(saveButton.isEnabled()).isFalse();
	}

	@Test
	void dirtySaveButtonRespectsAdditionalEnablementCondition() {
		final TextField name = new TextField();
		final Button saveButton = new Button("Speichern");
		final Binder<FormData> binder = new Binder<>();
		final AtomicBoolean available = new AtomicBoolean(false);
		binder.forField(name).bind(FormData::getName, FormData::setName);
		final FormBinders.DirtySaveButton dirtySaveButton = FormBinders.bindDirtySaveButton(binder, saveButton,
				available::get);

		binder.readBean(new FormData("Bio"));
		dirtySaveButton.reset();
		name.setValue("Physik");

		assertThat(saveButton.isEnabled()).isFalse();

		available.set(true);
		dirtySaveButton.update();

		assertThat(saveButton.isEnabled()).isTrue();
	}

	private static final class FormData {

		private String name;

		private FormData(final String name) {
			this.name = name;
		}

		public String getName() {
			return name;
		}

		public void setName(final String name) {
			this.name = name;
		}
	}
}
