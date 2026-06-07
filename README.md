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

## Kommandozeilen-Properties

Für den Betrieb als Jar können Spring-Boot-Properties direkt auf der Kommandozeile
übergeben werden:

```shell
java -jar topteacher-app-0.0.1-SNAPSHOT.jar --server.port=8081
```

Mehrere Properties werden einfach hintereinander angegeben. Werte mit
Sonderzeichen wie `;` sollten in Anführungszeichen gesetzt werden:

```shell
java -jar topteacher-app-0.0.1-SNAPSHOT.jar \
  --server.port=8081 \
  --server.servlet.context-path=/top-teacher \
  '--spring.datasource.url=jdbc:h2:file:/Users/jens/topteacher/data/topteacher;AUTO_SERVER=TRUE'
```

Wichtige Properties:

| Property | Standardwert | Bedeutung |
| --- | --- | --- |
| `server.port` | `8081` | HTTP-Port der Anwendung. |
| `server.servlet.context-path` | `/top-teacher` | Pfad, unter dem die Anwendung erreichbar ist. |
| `spring.datasource.url` | `jdbc:h2:file:./data/topteacher;AUTO_SERVER=TRUE` | Speicherort der H2-Datenbank. Der Live-Datenbankpfad sollte lokal auf dem Rechner liegen; Backups können auf ein NAS geschrieben werden. |
| `spring.datasource.username` | `sa` | Benutzername der H2-Verbindung. |
| `spring.datasource.password` | leer | Passwort der H2-Verbindung. |
| `spring.h2.console.enabled` | `true` | Aktiviert die H2-Konsole. Für produktiven Betrieb kann sie per `false` deaktiviert werden. |
| `spring.h2.console.path` | `/h2-console` | Pfad der H2-Konsole relativ zum Context Path. |
| `tt.demo-data.create` | `false` | Erstellt beim Start Demo-Daten, aber nur wenn noch keine fachlichen Daten vorhanden sind. |

Demo-Daten für eine neue Demo-Datenbank erzeugen:

```shell
java -jar topteacher-app-0.0.1-SNAPSHOT.jar \
  '--spring.datasource.url=jdbc:h2:file:./data/topteacher-demo;AUTO_SERVER=TRUE' \
  --tt.demo-data.create=true
```

Die Anwendung erstellt dabei zuerst Schema und Basisdaten, prüft dann, ob die
Datenbank noch keine fachlichen Daten enthält, und lädt anschließend die
Demo-Daten. Wenn bereits Schülerinnen, Kurse, Klausuren, Level-of-Expectations
oder andere fachliche Daten vorhanden sind, bricht der Start bewusst ab. Nach dem
Erzeugen der Demo-Daten sollte `--tt.demo-data.create=true` wieder weggelassen
werden.

In der Entwicklung kann dieselbe Property über das Maven-Plugin gesetzt werden:

```shell
./run/run-app.sh -Dspring-boot.run.arguments=--tt.demo-data.create=true
```
