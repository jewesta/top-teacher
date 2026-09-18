## ![TopTeacher!](topteacher-app/src/main/resources/META-INF/resources/images/topteacher-logo-github.png)

TopTeacher! ist eine Web-App für Lehrkräfte. Sie unterstützt bei der aufwändigen Erstellung und Verwaltung von Erwartungshorizonten und der Erfassung von Klausurergebnissen. Besonderer Kniff von TopTeacher! ist, dass die Erwartungshorizonte direkt digital pro Schüler:in ausgefüllt werden können. Anschließend kann eine optisch ansprechende Schüler:innen-Version und Lehrer:innen-Version davon als PDF erzeugt werden.

Die App ist aus persönlichen Gründen entstanden und löst ein persönliches Problem. Ich stelle sie trotzdem offen zur Verfügung in der Hoffnung, dass sie anderen auch das Leben erleichtert. Ich kann aber keine Garantie dafür übernehmen, dass sie fehlerfrei funktioniert und allgemeingültig einsetzbar ist. Das Programm ist aktuell nur Einzelplatz-Fähig; eine Benutzerverwaltung habe ich vorgedacht, gibt es aktuell aber nicht.

# TopTeacher! Server

TopTeacher! ist dazu gedacht, als Server zu laufen und dann per Web-Browser aufgerufen zu werden. Als Java-Anwendung kann TopTeacher! im Server-Betrieb auf so gut wie jeder Plattform als `jar` gestartet werden. Dem Betrieb unter Linux und Windows steht damit grundsätzlich nichts im Weg.

# TopTeacher! App

Um technisch weniger Versierten die Benutzung zu erleichtern, stelle ich TopTeacher! auch als alleinstehend lauffähige macOS App zur Verfügung. Das macht auch einen unkomplizierten Test möglich. Die App läuft dann ganz normal im Dock und öffnet automatisch ein Browser-Fenster mit der Benutzeroberfläche. Die nötige Datenbank wird unter `/Users/<Benutzername>/Library/Application Support/TopTeacher/topteacher.mv.db` angelegt, falls sie noch nicht existiert. Weil ich die App nicht per Apple Developer ID signiere und notarisiere, gibt es beim ersten Start die üblichen Dinge zu beachten: Starten per Rechtsklick > Öffnen und dann ein ausdrückliches Erlauben des Programmstarts über `Systemeinstellungen` > `Datenschutz & Sicherheit`.

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
| `topteacher-mcp` | Model-Context-Protocol-Adapter und MCP-Endpunktschutz |
| `topteacher-app` | Spring-Boot- und Vaadin-Anwendung |
| `westarps-vaadin-markdown` | Wiederverwendbare Vaadin-Markdown-Editor-Komponenten. |

## Start

```shell
./run/start-dev.sh /Users/<Benutzername>/Documents/<top-teacher-db>
```

Der Pfad ist frei wählbar und muss lediglich beschreibbar sein. TopTeacher erstellt ihn selbst, wenn er noch fehlt. Der Name der Datenbank `<top-teacher-db>` wird ohne Endung angegeben. Die Endung `.mv.db` wird automatisch hinzugefügt.

Die App ist danach unter <http://localhost:8081/top-teacher> erreichbar. Port `8081` und Kontext-Pfad `top-teacher` können über
`server.port` und `server.servlet.context-path` in `topteacher-app/src/main/resources/application.properties` angepasst werden.

Das Release-`jar` ist so gebaut, dass Vaadin im Produktiv-Modus gestartet wird. Das oben genannte Startskript startet Vaadin im Entwicklermodus. Deshalb auch der Namenszusatz `-dev`. Ein lokaler Start mit Produktions-Bundle ist ebenfalls möglich:

```shell
./run/start-prod.sh /Users/<Benutzername>/Documents/<top-teacher-db>
```

`start-prod.sh` baut zuerst das produktive Jar unter `target/start-prod/` und startet es dann ohne Devtools, LiveReload und H2-Konsole.

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

## MCP interface

TopTeacher can expose a Model Context Protocol interface to AI clients through
Streamable HTTP. It is disabled by default. When enabled, the endpoint is
`http://localhost:8081/top-teacher/mcp` and every request requires a bearer
token.

Store the token outside the repository in a file readable only by its owner. It
must contain exactly one UTF-8 line with at least 32 characters. For example:

```shell
umask 077
openssl rand -hex 32 > /absoluter/pfad/topteacher-mcp-token
```

Enable MCP through environment variables when using one of the bundled start
scripts:

```shell
TT_MCP_ENABLED=true \
TT_MCP_TOKEN_FILE=/absoluter/pfad/topteacher-mcp-token \
./run/start-dev.sh /Users/<Benutzername>/Documents/<top-teacher-db>
```

The AI client must use the endpoint URL and the HTTP header
`Authorization: Bearer <token from the file>`.

The MCP server currently exposes these tools:

| Tool | Action |
| --- | --- |
| `list_courses` | List active or archived courses. |
| `list_course_pupils` | List the pupils currently assigned to a course; archived pupils are included only when explicitly requested. |
| `get_course_creation_options` | Read the valid school classes, course periods, active subjects, and active grading scales. |
| `create_course_with_pupils` | Atomically create an active course, create or reuse its pupils, and assign them. |
| `assign_pupils_to_course` | Add pupils to an existing active course without changing or removing its current roster. |
| `remove_pupils_from_course` | Remove pupils from an active course, subject to the same exam-assignment locks as the UI. |
| `list_pupils` | List active pupils by default, or explicitly inspect archived pupils. |
| `create_pupils` | Create active pupils without assigning them to a course. |
| `update_pupil` | Change a pupil's first name, surname, or both. |
| `list_exams` | List the exams of a course. |
| `get_level_of_expectations` | Read a complete level of expectations. |
| `create_level_of_expectations` | Create a complete level of expectations for a blank exam without overwriting anything. |
| `list_exam_pupils` | List the pupils assigned to an exam. |
| `get_pupil_result` | Read one pupil's result for an exam. |
| `create_database_backup` | Create a database backup in TopTeacher's configured target folder and report when backup is unavailable. |

Archived pupils are historical records throughout TopTeacher. They are hidden
from default discovery, excluded from identity and duplicate matching, cannot
be newly assigned, and are never reactivated as a side effect of another
operation. `list_pupils` returns active pupils by default and includes lifecycle
and latest-class information; archived pupils are visible only through an
explicit `ARCHIVED` or `ALL` scope. The MCP interface does not expose a way to
change lifecycle state.

Exact active pupil-name matches are never merged automatically. The
course-creation tool requires an explicit `REUSE`, `CREATE`, or `SKIP` decision;
assigning pupils to an existing course uses the same decisions. A uniquely
named active pupil who is already assigned satisfies the request without a
write. Standalone pupil creation offers `CREATE` or `SKIP`. Skipping every
proposed pupil cancels the operation instead of creating an empty course or
performing an empty write. Renaming a pupil to the exact name of another active
pupil requires `UPDATE_ANYWAY` or `CANCEL` confirmation.

Conflict handling uses native MCP elicitation when the client advertises that
capability. Otherwise the tool returns `NEEDS_RESOLUTION` or
`NEEDS_CONFIRMATION` without changing the database; the model should ask the
user in normal chat and retry the same tool with the decisions. Once all course
roster conflicts are resolved, new pupils and assignments are written in one
transaction. Existing-course assignment is add-only: it never removes pupils or
changes course properties. Its response describes the requested additions, not
the complete roster; clients should use `list_course_pupils` when the user asks
who is currently in a course or whether the roster is complete.

Course removal uses exact pupil IDs from `list_course_pupils` and delegates to
the same integrity rule as the UI: a pupil assigned to any exam in that course
cannot be removed, even if no results have been entered. When a batch contains
locked pupils, the user can choose `SKIP` to keep those pupils and remove the
eligible remainder, or `CANCEL` to leave the entire batch unchanged. The MCP
operation never removes pupil records or changes exam assignments.

`create_database_backup` delegates to the same backup service as the settings
UI. It accepts no destination path and does not change backup configuration. A
missing target folder or a backup failure is returned as a structured `FAILED`
result with TopTeacher's error message instead of a generic protocol error.

### LM Studio

In LM Studio, open `Program` → `Install` → `Edit mcp.json` and add TopTeacher as
a remote MCP server. Replace the placeholder with the token stored in the
credential file:

```json
{
  "mcpServers": {
    "topteacher": {
      "url": "http://127.0.0.1:8081/top-teacher/mcp",
      "headers": {
        "Authorization": "Bearer <token from the file>"
      }
    }
  }
}
```

LM Studio currently handles the conflict fallback through ordinary chat. A
useful first prompt for a tool-capable local model is:

> Lege in TopTeacher einen Englischkurs für die 8a im Schuljahr 2026/27 an.
> Verwende den Notenschlüssel mit 100 Punkten. Hier ist die Schülerliste: …

## macOS App

Eine lokal ausführbare App mit eingebettetem Java-Runtime kann mit `jpackage`
aus dem JDK 21 gebaut werden:

```shell
./run/package.sh macos-app /Volumes/topteacher-builds
```

Das Skript baut zuerst das Produktions-Jar, erzeugt danach lokal ein App-Image und kopiert ein ZIP-Archiv unter `<release-target>/v<version>/`. Release-Builds verwenden den Dateinamen `TopTeacher-<version>-<architecture>.zip`; Snapshot-Builds verwenden statt `SNAPSHOT` den kurzen Git-Commit-Hash, zum Beispiel `/Volumes/topteacher-builds/v0.0.1-SNAPSHOT/TopTeacher-0.0.1-a1b2c3d-arm64.zip`. Vor dem ZIP-Erstellen werden erweiterte Dateiattribute entfernt; danach wird das App-Bundle ad-hoc signiert und verifiziert. Das ZIP wird ohne Resource-Fork- und Quarantine-Metadaten erstellt. Das ersetzt keine Developer-ID-Notarisierung, vermeidet aber bei privaten Downloads die irreführende Gatekeeper-Meldung, die App sei beschädigt. Beim Start öffnet die App automatisch <http://localhost:8081/top-teacher/> im Standardbrowser. Auf macOS erscheint TopTeacher! als Dock-App; ein Klick auf das Dock-Icon öffnet TopTeacher! wieder im Standardbrowser. Der native Menüpunkt zum Beenden der App beendet den lokalen Server. Das Dock-Kontextmenü enthält `About TopTeacher!`. Die H2-Konsole ist in diesem App-Modus deaktiviert; die Datenbank verwendet weiterhin den betriebssystemabhängigen Benutzerdatenpfad.

Auf macOS erzeugt das Skript `packaging/topteacher.icns` aus `topteacher-app/src/main/resources/META-INF/resources/images/topteacher-icon.png`.

Jar und macOS-App können zusammen gebaut werden:

```shell
./run/package-all.sh /Volumes/topteacher-builds
```
