# TopTeacher

TopTeacher is planned as a local-network web utility for managing pupils' grades
and printing result sheets.

Planned technology:

- Java 21
- Spring Boot 4
- Vaadin 25
- Maven
- JDBC
- H2 database

## Development

The repository is a Maven multi-module build:

- `topteacher-app` contains the Spring Boot and Vaadin application.
- `westarps-vaadin-markdown` contains reusable Vaadin markdown editor components.

Run the application:

```shell
./run/run-app.sh
```

Open the app at <http://localhost:8080>.

The local H2 database is stored at `./data/topteacher.mv.db`. For DBeaver, use
an H2 Embedded connection with this JDBC URL:

```text
jdbc:h2:file:/Users/jens/repositories/TopTeacher/data/topteacher;AUTO_SERVER=TRUE
```

The H2 console is available in development at
<http://localhost:8080/h2-console>.
