## ![TopTeacher!](topteacher-app/src/main/resources/META-INF/resources/images/topteacher-logo-github.png)

TopTeacher! ist eine Web-App für das lokale Netzwerk zur Verwaltung von Schülerinnen und Schülern, Kursen, Klausuren, Erwartungshorizonten und Ergebnissen. Die App unterstützt Lehrkräfte bei der Eingabe von Bewertungsdaten, der Auswertung von Klausuren sowie beim Erstellen druckbarer Ergebnisbögen und Exporte.

Technologie:

- Java 21
- Spring Boot 4
- Vaadin 25
- Maven
- JDBC
- H2-Datenbank

## Entwicklung

Das Repository ist ein Maven-Multimodul-Projekt:

- `topteacher-model` enthält gemeinsam genutzte Domänentypen.
- `topteacher-backend` enthält Persistenz- und Export-Services.
- `topteacher-app` enthält die Spring-Boot- und Vaadin-Anwendung.
- `westarps-vaadin-markdown` enthält wiederverwendbare Vaadin-Markdown-Editor-Komponenten.

Anwendung starten:

```shell
./run/run-app.sh
```

Die App ist danach unter <http://localhost:8081/top-teacher> erreichbar. Port und Pfad werden über
`server.port` und `server.servlet.context-path` in `topteacher-app/src/main/resources/application.properties`
festgelegt.

Die lokale H2-Datenbank liegt unter `./data/topteacher.mv.db`. Für DBeaver kann eine H2-Embedded-Verbindung mit folgender JDBC-URL verwendet werden:

```text
jdbc:h2:file:/Users/jens/repositories/TopTeacher/data/topteacher;AUTO_SERVER=TRUE
```

Die H2-Konsole ist in der Entwicklungsumgebung hier erreichbar:
<http://localhost:8081/top-teacher/h2-console>.
