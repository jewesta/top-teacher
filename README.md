## ![TopTeacher!](topteacher-app/src/main/resources/META-INF/resources/images/topteacher-logo-github.png)

TopTeacher! ist eine Web-App für Lehrkräfte. Sie unterstützt bei der aufwändigen Erstellung und Verwaltung von Erwartungshorizonten und der Erfassung von Klausurergebnissen. Besonderer Kniff von TopTeacher! ist, dass die Erwartungshorizonte direkt digital pro Schüler:in ausgefüllt werden können. Anschließend kann eine optisch ansprechende Schüler:innen-Version und Lehrer:innen-Version davon als PDF erzeugt werden.

Die App ist aus persönlichen Gründen entstanden und löst ein persönliches Problem. Ich stelle sie trotzdem offen zur Verfügung in der Hoffnung, dass sie anderen auch das Leben erleichtert. Ich kann aber keine Garantie dafür übernehmen, dass sie fehlerfrei funktioniert und allgemeingültig einsetzbar ist. Das Programm ist aktuell nur Einzelplatz-Fähig; eine Benutzerverwaltung habe ich vorgedacht, gibt es aktuell aber nicht.

# TopTeacher! Server

TopTeacher! ist dazu gedacht, als Server zu laufen und dann per Web-Browser aufgerufen zu werden. Als Java-Anwendung kann TopTeacher! im Server-Betrieb auf so gut wie jeder Plattform als `jar` gestartet werden. Dem Betrieb unter Linux und Windows steht damit grundsätzlich nichts im Weg.

# TopTeacher! App

Um technisch weniger Versierten die Benutzung zu erleichtern, stelle ich TopTeacher! auch als alleinstehend lauffähige macOS App zur Verfügung. Das macht auch einen unkomplizierten Test möglich. Die App läuft dann ganz normal im Dock und öffnet automatisch ein Browser-Fenster mit der Benutzeroberfläche. Die nötige Datenbank wird unter `/Users/<Benutzername>/Library/Application Support/TopTeacher/topteacher.mv.db` angelegt, falls sie noch nicht existiert. Weil ich die App nicht signiere gibt es beim ersten Start die üblichen Dinge zu beachten: Starten per Rechtsklick > Öffnen und dann ein ausdrückliches Erlauben des Programmstarts über `Systemeinstellungen` > `Datenschutz & Sicherheit`.

Ich bitte um Verständnis, dass ich diese App aktuell nur für moderne Macs mit M-Prozessoren bauen lasse. Technisch versierten steht es frei, diese alleinstehende App auch für macOS x86 oder Windows erstellen zu lassen.

# Technische Details

## Verwendete Technologien

- Java 21
- Spring Boot 4
- Vaadin 25
- Maven
- JDBC
- H2-Datenbank

## Struktur

Das Repository ist ein Maven-Multimodul-Projekt:

| Modul | Funktion |
|:---|:---|
| `topteacher-model`| Gemeinsam genutzte Domänentypen |
| `topteacher-backend` | Persistenz- und Export-Services |
| `topteacher-app` | Spring-Boot- und Vaadin-Anwendung |
| `westarps-vaadin-markdown` | Wiederverwendbare Vaadin-Markdown-Editor-Komponenten. |

## Start

```shell
./run/start-dev.sh /Users/<Benutzername>/Documents/<top-teacher-db>
```

Der Pfad ist frei wählbar und muss lediglich beschreibbar sein. TopTeacher erstellt ihn selbst, wenn er noch fehlt. Der Name der Datenbank `<top-teacher-db>` wird ohne Endung angegeben. Die Endung `.mv.db` wird automatisch hinzugefügt.

Die App ist danach unter <http://localhost:8081/top-teacher> erreichbar. Port `8081` und Kontext-Pfad `top-teacher` können über
`server.port` und `server.servlet.context-path` in `topteacher-app/src/main/resources/application.properties` angepasst werden.

Das Release-`jar` ist so gebaut, dass Vaadin im Produktiv-Modus gestartet wird. Das oben genannte Startskript startet Vaadin im Entwicklermodus. Deshalb auch der Namenszusatz `-dev`.

## Standardpfade:

| Betriebssystem | Datenbankpfad ohne `.mv.db` |
| --- | --- |
| macOS | `~/Library/Application Support/TopTeacher/topteacher` |
| Windows | `%APPDATA%\TopTeacher\topteacher` |
| Linux | `$XDG_DATA_HOME/TopTeacher/topteacher` oder `~/.local/share/TopTeacher/topteacher` |

## Überwachung der Datenbank

Wer möchte, kann z.B. mit [DBeaver](https://dbeaver.io/) eine Verbindung zu der H2-Datenbank aufbauen. Zum Beispiel unter macOS mit dieser URL:

```text
jdbc:h2:file:/Users/<Benutzername>/Library/Application Support/TopTeacher/topteacher;AUTO_SERVER=TRUE
```

...oder wo auch immer die lokale H2-Datenbankdatei abgelegt wurde. Die H2-Konsole ist in der Entwicklungsumgebung hier erreichbar:
<http://localhost:8081/top-teacher/h2-console>.

## Kommandozeilen-Properties

Für den Betrieb als Jar können Spring-Boot-Properties direkt auf der Kommandozeile
übergeben werden:

```shell
java -jar TopTeacher.jar --server.port=8081
```

Mehrere Properties werden einfach hintereinander angegeben. Werte mit
Sonderzeichen wie `;` sollten in Anführungszeichen gesetzt werden:

```shell
java -jar TopTeacher.jar \
  --server.port=8081 \
  --server.servlet.context-path=/top-teacher \
  --tt.database.file=/Users/<Benutzername>/topteacher/data/topteacher
```

Wichtige Properties:

| Property | Standardwert | Bedeutung |
| --- | --- | --- |
| `tt.database.file` | Betriebssystemabhängiger Benutzerdatenpfad | Optionaler Pfad zur H2-Datenbank ohne `.mv.db`-Suffix, z. B. `/Users/<Benutzername>/topteacher/data/topteacher`. Wenn die Property gesetzt ist, muss der Ordner existieren und beschreibbar sein. |
| `server.port` | `8081` | HTTP-Port der Anwendung. |
| `server.servlet.context-path` | `/top-teacher` | Pfad, unter dem die Anwendung erreichbar ist. |
| `spring.datasource.url` | `jdbc:h2:file:${tt.database.file};AUTO_SERVER=TRUE` | JDBC-URL der H2-Datenbank. Normalerweise muss diese Property nicht gesetzt werden. |
| `spring.datasource.username` | `sa` | Benutzername der H2-Verbindung. |
| `spring.datasource.password` | leer | Passwort der H2-Verbindung. |
| `spring.h2.console.enabled` | `true` | Aktiviert die H2-Konsole. Für produktiven Betrieb kann sie per `false` deaktiviert werden. |
| `spring.h2.console.path` | `/h2-console` | Pfad der H2-Konsole relativ zum Context Path. |

Beim ersten Start führt TopTeacher durch die Datenbank-Initialisierung. Dabei kann eine leere Datenbank mit Basisdaten oder eine Datenbank mit Demodaten angelegt werden. Später kann die Datenbank in den Einstellungen im Tab `Zurücksetzen` erneut initialisiert werden.

## macOS App

Eine lokal ausführbare App mit eingebettetem Java-Runtime kann mit `jpackage`
aus dem JDK 21 gebaut werden:

```shell
./run/package.sh macos-app /Volumes/topteacher-builds
```

Das Skript baut zuerst das Produktions-Jar, erzeugt danach lokal ein App-Image und kopiert ein ZIP-Archiv unter `<release-target>/v<version>/TopTeacher.app.<architecture>.zip`, zum Beispiel `/Volumes/topteacher-builds/v0.0.1-SNAPSHOT/TopTeacher.app.arm64.zip`. Beim Start öffnet die App automatisch <http://localhost:8081/top-teacher/> im Standardbrowser. Auf macOS erscheint TopTeacher! als Dock-App; ein Klick auf das Dock-Icon öffnet TopTeacher! wieder im Standardbrowser. Der native Menüpunkt zum Beenden der App beendet den lokalen Server. Das Dock-Kontextmenü enthält `About TopTeacher!`. Die H2-Konsole ist in diesem App-Modus deaktiviert; die Datenbank verwendet weiterhin den betriebssystemabhängigen Benutzerdatenpfad.

Auf macOS erzeugt das Skript `packaging/topteacher.icns` aus `topteacher-app/src/main/resources/META-INF/resources/images/topteacher-icon.png`.

Jar und macOS-App können zusammen gebaut werden:

```shell
./run/package-all.sh /Volumes/topteacher-builds
```
