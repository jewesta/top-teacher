# Issue 2

## Create First Version of the App

This issue created the first usable version of TopTeacher from an empty
repository. The result is a local-network Vaadin/Spring Boot application for
managing pupils, courses, exams, levels of expectations, result entry, and first
PDF exports.

## Application Foundation

- Initialized the project as a Maven multi-module build.
- Added `topteacher-model`, `topteacher-backend`, and `topteacher-app` modules
  with Java 21, Spring Boot, Vaadin 25, JDBC, and H2.
- Added a reusable `westarps-vaadin-markdown` module for Markdown editing and
  viewing.
- Added local run support, live reload/dev tooling, a Spring Boot startup
  banner, application styling, and the first operational docs.
- Established schema and demo-data SQL files for the H2-backed local database.

## Core Domain and Persistence

- Added core model concepts for pupils, courses, exams, school years, terms,
  classes, subjects, lifecycle state, grading scales, and level-of-expectations
  structures.
- Implemented JDBC repositories for pupils, courses, exams, grading scales, and
  levels of expectations.
- Added course-pupil assignment as the central relation for later grading work.
- Added grading scales with fixed point ranges and assigned them through
  courses.

## User Interface

- Built the main Vaadin app shell with top navigation and German UI labels.
- Added master-data views for pupils, courses, and exams.
- Introduced reusable view/editor patterns for master-detail screens, quick
  filtering, dirty-state aware save actions, and multi-selection behavior.
- Added course pupil assignment handling and result-oriented editor areas.
- Tuned the Lumo theme, app styling, logo handling, and layout behavior.

## Level of Expectations and Results

- Added a full level-of-expectations designer for exams with the hierarchy:
  Klausurteil, Leistungskategorie, Teilaufgabe, Anforderung.
- Added Markdown descriptions, bonus requirements, collapsible sections,
  aggregation badges, percentage badges, and note sections.
- Refactored the level-of-expectations UI into reusable section components.
- Added custom Markdown tag support for criteria such as `[Zeitform](eh:1)`.
- Added result entry per pupil with points, notes, and criterion checkboxes.
- Added point rules for regular points, bonus points, and grading-scale maximum
  validation.

## Markdown Component

- Split the Markdown editor/viewer into a reusable Vaadin module.
- Added configurable toolbar commands, tag support, generated tag ids, rendered
  criteria badges, and checkbox rendering for result entry.
- Kept TopTeacher-specific tags configurable from Java rather than hard-coded
  into the generic module.

## PDF Export

- Added a first pupil-facing level-of-expectations PDF export path.
- Built Thymeleaf rendering, Markdown sanitizing, safe HTML conversion, and PDF
  rendering.
- Added shared export CSS and A5-on-A4 landscape PDF imposition.
- Ensured pupil-facing exports remove internal criteria markers while preserving
  normal Markdown formatting.

## Tests and Documentation

- Added focused unit and repository tests around model behavior, repositories,
  UI components, level-of-expectations behavior, result entry, Markdown/export
  handling, and point rules.
- Added operational documentation for the level-of-expectations model.
- Added `AGENTS.md` with project-specific engineering notes.
