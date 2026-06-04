package de.topteacher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

@Theme(value = "topteacher", variant = Lumo.LIGHT)
@SpringBootApplication
public class TopTeacherApplication implements AppShellConfigurator {

	public static void main(final String[] args) {
		SpringApplication.run(TopTeacherApplication.class, args);
	}
}
