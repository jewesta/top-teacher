package de.topteacher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.lumo.Lumo;

@StyleSheet(Lumo.STYLESHEET)
@CssImport("./styles.css")
@SpringBootApplication
public class TopTeacherApplication implements AppShellConfigurator {

	public static void main(final String[] args) {
		SpringApplication.run(TopTeacherApplication.class, args);
	}
}
