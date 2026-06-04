package de.topteacher;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.aura.Aura;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@StyleSheet(Aura.STYLESHEET)
@SpringBootApplication
public class TopTeacherApplication implements AppShellConfigurator {

    public static void main(final String[] args) {
        SpringApplication.run(TopTeacherApplication.class, args);
    }
}

