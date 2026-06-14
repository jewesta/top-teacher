insert into pupil (name, surname, lifecycle)
select demo.name, demo.surname, demo.lifecycle
from (
    values
        ('Emma', 'Schneider', 'ACTIVE'),
        ('Noah', 'Fischer', 'ACTIVE'),
        ('Mia', 'Weber', 'ACTIVE'),
        ('Ben', 'Meyer', 'ACTIVE'),
        ('Hannah', 'Wagner', 'ACTIVE'),
        ('Finn', 'Becker', 'ACTIVE'),
        ('Lina', 'Hoffmann', 'ACTIVE'),
        ('Paul', 'Schulz', 'ACTIVE'),
        ('Lea', 'Koch', 'ACTIVE'),
        ('Luis', 'Bauer', 'ACTIVE'),
        ('Sofia', 'Richter', 'ACTIVE'),
        ('Jonas', 'Klein', 'ACTIVE'),
        ('Marie', 'Wolf', 'ACTIVE'),
        ('Elias', 'Schroeder', 'ACTIVE'),
        ('Clara', 'Neumann', 'ACTIVE'),
        ('Leon', 'Schwarz', 'ACTIVE'),
        ('Anna', 'Zimmermann', 'ACTIVE'),
        ('Oskar', 'Braun', 'ACTIVE'),
        ('Nora', 'Krueger', 'INACTIVE'),
        ('Max', 'Hartmann', 'INACTIVE')
) demo(name, surname, lifecycle)
where not exists (
    select 1
    from pupil p
    where p.name = demo.name
      and p.surname = demo.surname
);

insert into course (school_class, subject_id, calendar_year, course_period, lifecycle, grading_scale_id)
select demo.school_class, subject.id, demo.calendar_year, demo.course_period, demo.lifecycle, gs.id
from (
    values
        ('CLS_10A', 'Erdkunde', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_Q1', 'Chemie', 2026, 'FULL_YEAR', 'ACTIVE')
) demo(school_class, subject_name, calendar_year, course_period, lifecycle)
join subject
    on subject.name = demo.subject_name
join grading_scale gs
    on gs.name = 'Standard'
where not exists (
    select 1
    from course c
    where c.school_class = demo.school_class
      and c.subject_id = subject.id
      and c.calendar_year = demo.calendar_year
      and c.course_period = demo.course_period
);

insert into exam (course_id, title, exam_date)
select c.id, demo.title, demo.exam_date
from (
    values
        ('CLS_10A', 'Erdkunde', 2026, 'FULL_YEAR', 'Klausur Windenergie und Klimawandel', date '2026-11-12'),
        ('CLS_Q1', 'Chemie', 2026, 'FULL_YEAR', 'Klausur Reaktionsgeschwindigkeit und Gleichgewicht', date '2026-12-03')
) demo(school_class, subject_name, calendar_year, course_period, title, exam_date)
join course c
    on c.school_class = demo.school_class
   and c.calendar_year = demo.calendar_year
   and c.course_period = demo.course_period
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
where not exists (
    select 1
    from exam e
    where e.course_id = c.id
      and e.title = demo.title
);

insert into course_pupil (course_id, pupil_id)
select c.id, p.id
from course c
join subject
    on subject.id = c.subject_id
join pupil p
    on p.lifecycle = 'ACTIVE'
where subject.name in ('Erdkunde', 'Chemie')
  and c.calendar_year = 2026
  and c.course_period = 'FULL_YEAR'
  and not exists (
      select 1
      from course_pupil cp
      where cp.course_id = c.id
        and cp.pupil_id = p.id
  );

insert into exam_pupil (exam_id, pupil_id)
select e.id, cp.pupil_id
from exam e
join course_pupil cp
    on cp.course_id = e.course_id
where not exists (
    select 1
    from exam_pupil ep
    where ep.exam_id = e.id
      and ep.pupil_id = cp.pupil_id
);

insert into eh_part (exam_id, title, sort_order)
select e.id, demo.title, demo.sort_order
from (
    values
        ('Erdkunde', 'Klausur Windenergie und Klimawandel', 0, 'Klausur: Windenergie, Klimawandel und Energiewende'),
        ('Chemie', 'Klausur Reaktionsgeschwindigkeit und Gleichgewicht', 0, 'Klausur: Reaktionsgeschwindigkeiten und chemisches Gleichgewicht')
) demo(subject_name, exam_title, sort_order, title)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
   and e.title = demo.exam_title
where not exists (
    select 1
    from eh_part part
    where part.exam_id = e.id
      and part.title = demo.title
);

insert into eh_category (part_id, title, description_markdown, sort_order)
select part.id, demo.title, demo.description_markdown, demo.sort_order
from (
    values
        ('Erdkunde', 'Klausur: Windenergie, Klimawandel und Energiewende', 0, 'Aufgaben', ''),
        ('Erdkunde', 'Klausur: Windenergie, Klimawandel und Energiewende', 1, 'Darstellungsleistung', 'Bewertet werden Struktur, Belegnutzung, fachsprachliche Genauigkeit und sprachliche Korrektheit.'),
        ('Chemie', 'Klausur: Reaktionsgeschwindigkeiten und chemisches Gleichgewicht', 0, 'Aufgaben', ''),
        ('Chemie', 'Klausur: Reaktionsgeschwindigkeiten und chemisches Gleichgewicht', 1, 'Darstellungsleistung', 'Bewertet werden Struktur, Belegnutzung, fachsprachliche Genauigkeit und sprachliche Korrektheit.')
) demo(subject_name, part_title, sort_order, title, description_markdown)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
join eh_part part
    on part.exam_id = e.id
   and part.title = demo.part_title
where not exists (
    select 1
    from eh_category category
    where category.part_id = part.id
      and category.title = demo.title
);

insert into eh_task (category_id, title, sort_order)
select category.id, demo.title, demo.sort_order
from (
    values
        ('Erdkunde', 'Aufgaben', 0, 'Aufgabe 1: Windenergie als erneuerbare Energiequelle'),
        ('Erdkunde', 'Aufgaben', 1, 'Aufgabe 2: Klimawandel und Energiebedarf'),
        ('Erdkunde', 'Aufgaben', 2, 'Aufgabe 3: Transformation des Energiesektors beurteilen'),
        ('Erdkunde', 'Darstellungsleistung', 0, 'Strukturiert arbeiten'),
        ('Erdkunde', 'Darstellungsleistung', 1, 'Belege nutzen'),
        ('Erdkunde', 'Darstellungsleistung', 2, 'Sprachlich korrekt schreiben'),
        ('Chemie', 'Aufgaben', 0, 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie'),
        ('Chemie', 'Aufgaben', 1, 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen'),
        ('Chemie', 'Aufgaben', 2, 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren'),
        ('Chemie', 'Darstellungsleistung', 0, 'Strukturiert arbeiten'),
        ('Chemie', 'Darstellungsleistung', 1, 'Belege nutzen'),
        ('Chemie', 'Darstellungsleistung', 2, 'Sprachlich korrekt schreiben')
) demo(subject_name, category_title, sort_order, title)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
join eh_part part
    on part.exam_id = e.id
join eh_category category
    on category.part_id = part.id
   and category.title = demo.category_title
where not exists (
    select 1
    from eh_task task
    where task.category_id = category.id
      and task.title = demo.title
);

insert into eh_requirement (task_id, description_markdown, max_points, bonus, sort_order)
select task.id, demo.description_markdown, demo.max_points, false, demo.sort_order
from (
    values
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 0, 6,
'erläutert die [Funktionsweise von Windenergieanlagen](eh:1), indem Rotor, Generator und Netzeinspeisung sachgerecht beschrieben werden.'),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 1, 6,
'wertet geeignete [Standortfaktoren](eh:1) aus, z. B. Windhöffigkeit, Relief, Abstand zu Siedlungen und Anschluss an das Stromnetz.'),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 2, 6,
'stellt zentrale [Chancen](eh:1) und [Grenzen](eh:2) der Windenergienutzung für Versorgungssicherheit, Landschaft und Akzeptanz dar.'),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 3, 6,
'nutzt Materialdaten, Karten oder Diagramme, um eine [Standortentscheidung](eh:1) nachvollziehbar zu begründen.'),
        ('Erdkunde', 'Aufgabe 2: Klimawandel und Energiebedarf', 0, 7,
'beschreibt Ursachen des anthropogenen [Klimawandels](eh:1) und verknüpft sie mit der Nutzung fossiler Energieträger.'),
        ('Erdkunde', 'Aufgabe 2: Klimawandel und Energiebedarf', 1, 7,
'erklärt Folgen steigender Temperaturen für [Ökosysteme](eh:1), Wirtschaftsräume und gesellschaftliche Vulnerabilität.'),
        ('Erdkunde', 'Aufgabe 2: Klimawandel und Energiebedarf', 2, 6,
'ordnet die Rolle erneuerbarer Energien für [Emissionsminderung](eh:1), Anpassung und internationale Klimaziele ein.'),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 0, 7,
'analysiert Bausteine der [Transformation des Energiesektors](eh:1), z. B. Ausbau erneuerbarer Energien, Speicher, Netze und Effizienz.'),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 1, 7,
'beurteilt Zielkonflikte zwischen [Klimaschutz](eh:1), Versorgungssicherheit, Kosten, Flächenbedarf und gesellschaftlicher Akzeptanz.'),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 2, 6,
'entwickelt ein begründetes Fazit, das lokale Maßnahmen mit globaler [Verantwortung](eh:1) verbindet.'),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 3, 6,
'verwendet passende Fachbegriffe wie [Volatilität](eh:1), Grundlast, Sektorkopplung und Dekarbonisierung sachgerecht.'),
        ('Erdkunde', 'Strukturiert arbeiten', 0, 5,
'gliedert die Bearbeitung in eine [klare Abfolge](eh:1) aus Einleitung, Auswertung, Beurteilung und Fazit.'),
        ('Erdkunde', 'Strukturiert arbeiten', 1, 5,
'formuliert zusammenhängend und führt Teilschritte durch passende [Überleitungen](eh:1) nachvollziehbar zusammen.'),
        ('Erdkunde', 'Strukturiert arbeiten', 2, 4,
'setzt Schwerpunkte so, dass zentrale Argumente erkennbar gewichtet werden und keine bloße [Materialnacherzählung](eh:1) entsteht.'),
        ('Erdkunde', 'Belege nutzen', 0, 5,
'bezieht sich präzise auf Material, Karten oder Diagramme und nutzt [Belege](eh:1) zur Stützung eigener Aussagen.'),
        ('Erdkunde', 'Belege nutzen', 1, 4,
'unterscheidet sauber zwischen Materialbefund, Erklärung und [eigener Bewertung](eh:1).'),
        ('Erdkunde', 'Sprachlich korrekt schreiben', 0, 4,
'schreibt fachsprachlich genau, verständlich und weitgehend [sprachlich korrekt](eh:1).'),
        ('Erdkunde', 'Sprachlich korrekt schreiben', 1, 3,
'verwendet Absätze, Satzzeichen und Fachbegriffe so, dass die Darstellung [leserfreundlich](eh:1) bleibt.'),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 0, 6,
'erklärt die [Kollisionstheorie](eh:1) als Modell dafür, warum wirksame Zusammenstöße Voraussetzung für eine Reaktion sind.'),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 1, 6,
'beschreibt den Einfluss von Temperatur, Konzentration, Oberfläche und Katalysator auf die [Reaktionsgeschwindigkeit](eh:1).'),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 2, 6,
'deutet Energiediagramme mit Blick auf [Aktivierungsenergie](eh:1), Reaktionsenthalpie und Wirkung eines [Katalysators](eh:2).'),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 3, 6,
'plant oder wertet einen Versuch zur Geschwindigkeitsmessung aus und benennt eine geeignete [Messgröße](eh:1).'),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 0, 7,
'beschreibt das chemische [Gleichgewicht](eh:1) als dynamischen Zustand, in dem Hin- und Rückreaktion gleich schnell ablaufen.'),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 1, 7,
'erklärt Gleichgewichtseinstellungen bei Änderung von Konzentration, Druck oder Temperatur mit dem [Prinzip von Le Chatelier](eh:1).'),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 2, 6,
'unterscheidet kinetische und energetische Aspekte und beschreibt die Bedeutung von [Hinreaktion](eh:1) und [Rückreaktion](eh:2).'),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 0, 7,
'stellt die Reaktionsgleichung und technische Zielsetzung des [Haber-Bosch-Verfahrens](eh:1) dar.'),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 1, 7,
'beurteilt den Einfluss von [Druck](eh:1) und [Temperatur](eh:2) auf Ausbeute, Geschwindigkeit und Energiebedarf.'),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 2, 6,
'erläutert die Rolle des [Katalysators](eh:1) sowie die kontinuierliche Abtrennung von Ammoniak für die Gleichgewichtslage.'),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 3, 6,
'bewertet den industriellen Prozess unter den Gesichtspunkten [Wirtschaftlichkeit](eh:1), Sicherheit und Nachhaltigkeit.'),
        ('Chemie', 'Strukturiert arbeiten', 0, 5,
'gliedert Rechnungen, Erklärungen und Bewertungen in eine [klare Abfolge](eh:1).'),
        ('Chemie', 'Strukturiert arbeiten', 1, 5,
'stellt Beobachtung, Deutung und Schlussfolgerung getrennt dar und nutzt passende [Überleitungen](eh:1).'),
        ('Chemie', 'Strukturiert arbeiten', 2, 4,
'setzt Schwerpunkte so, dass zentrale chemische Zusammenhänge erkennbar gewichtet werden und keine bloße [Stoffsammlung](eh:1) entsteht.'),
        ('Chemie', 'Belege nutzen', 0, 5,
'bezieht sich präzise auf Messwerte, Diagramme, Reaktionsgleichungen oder Materialien und nutzt [Belege](eh:1) zur Stützung eigener Aussagen.'),
        ('Chemie', 'Belege nutzen', 1, 4,
'unterscheidet sauber zwischen Beobachtung, Modellvorstellung und [eigener Bewertung](eh:1).'),
        ('Chemie', 'Sprachlich korrekt schreiben', 0, 4,
'verwendet Fachsprache, Formelzeichen und Einheiten genau und schreibt weitgehend [sprachlich korrekt](eh:1).'),
        ('Chemie', 'Sprachlich korrekt schreiben', 1, 3,
'formuliert verständlich und hält Reaktionsgleichungen, Satzzeichen und Fachbegriffe [leserfreundlich](eh:1).')
) demo(subject_name, task_title, sort_order, max_points, description_markdown)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
join eh_part part
    on part.exam_id = e.id
join eh_category category
    on category.part_id = part.id
join eh_task task
    on task.category_id = category.id
   and task.title = demo.task_title
where not exists (
    select 1
    from eh_requirement requirement
    where requirement.task_id = task.id
      and requirement.sort_order = demo.sort_order
);

insert into eh_criterion (requirement_id, criterion_key, label, sort_order, active)
select requirement.id, demo.criterion_key, demo.label, demo.criterion_sort_order, true
from (
    values
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 0, '1', 'Funktionsweise von Windenergieanlagen', 0),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 1, '1', 'Standortfaktoren', 0),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 2, '1', 'Chancen', 0),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 2, '2', 'Grenzen', 1),
        ('Erdkunde', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 3, '1', 'Standortentscheidung', 0),
        ('Erdkunde', 'Aufgabe 2: Klimawandel und Energiebedarf', 0, '1', 'Klimawandel', 0),
        ('Erdkunde', 'Aufgabe 2: Klimawandel und Energiebedarf', 1, '1', 'Ökosysteme', 0),
        ('Erdkunde', 'Aufgabe 2: Klimawandel und Energiebedarf', 2, '1', 'Emissionsminderung', 0),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 0, '1', 'Transformation des Energiesektors', 0),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 1, '1', 'Klimaschutz', 0),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 2, '1', 'Verantwortung', 0),
        ('Erdkunde', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 3, '1', 'Volatilität', 0),
        ('Erdkunde', 'Strukturiert arbeiten', 0, '1', 'klare Abfolge', 0),
        ('Erdkunde', 'Strukturiert arbeiten', 1, '1', 'Überleitungen', 0),
        ('Erdkunde', 'Strukturiert arbeiten', 2, '1', 'Materialnacherzählung', 0),
        ('Erdkunde', 'Belege nutzen', 0, '1', 'Belege', 0),
        ('Erdkunde', 'Belege nutzen', 1, '1', 'eigene Bewertung', 0),
        ('Erdkunde', 'Sprachlich korrekt schreiben', 0, '1', 'sprachlich korrekt', 0),
        ('Erdkunde', 'Sprachlich korrekt schreiben', 1, '1', 'leserfreundlich', 0),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 0, '1', 'Kollisionstheorie', 0),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 1, '1', 'Reaktionsgeschwindigkeit', 0),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 2, '1', 'Aktivierungsenergie', 0),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 2, '2', 'Katalysator', 1),
        ('Chemie', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 3, '1', 'Messgröße', 0),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 0, '1', 'Gleichgewicht', 0),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 1, '1', 'Prinzip von Le Chatelier', 0),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 2, '1', 'Hinreaktion', 0),
        ('Chemie', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 2, '2', 'Rückreaktion', 1),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 0, '1', 'Haber-Bosch-Verfahren', 0),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 1, '1', 'Druck', 0),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 1, '2', 'Temperatur', 1),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 2, '1', 'Katalysator', 0),
        ('Chemie', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 3, '1', 'Wirtschaftlichkeit', 0),
        ('Chemie', 'Strukturiert arbeiten', 0, '1', 'klare Abfolge', 0),
        ('Chemie', 'Strukturiert arbeiten', 1, '1', 'Überleitungen', 0),
        ('Chemie', 'Strukturiert arbeiten', 2, '1', 'Stoffsammlung', 0),
        ('Chemie', 'Belege nutzen', 0, '1', 'Belege', 0),
        ('Chemie', 'Belege nutzen', 1, '1', 'eigene Bewertung', 0),
        ('Chemie', 'Sprachlich korrekt schreiben', 0, '1', 'sprachlich korrekt', 0),
        ('Chemie', 'Sprachlich korrekt schreiben', 1, '1', 'leserfreundlich', 0)
) demo(subject_name, task_title, requirement_sort_order, criterion_key, label, criterion_sort_order)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
join eh_part part
    on part.exam_id = e.id
join eh_category category
    on category.part_id = part.id
join eh_task task
    on task.category_id = category.id
   and task.title = demo.task_title
join eh_requirement requirement
    on requirement.task_id = task.id
   and requirement.sort_order = demo.requirement_sort_order
where not exists (
    select 1
    from eh_criterion criterion
    where criterion.requirement_id = requirement.id
      and criterion.criterion_key = demo.criterion_key
);

insert into eh_requirement_result (requirement_id, pupil_id, points, comment_text)
select requirement.id, pupil.id, demo.points, demo.comment_text
from (
    values
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 0, 5, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 1, 5, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 2, 4, 'Chancen und Grenzen noch stärker gegeneinander abwägen.'),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 1: Windenergie als erneuerbare Energiequelle', 3, 5, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 2: Klimawandel und Energiebedarf', 0, 6, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 2: Klimawandel und Energiebedarf', 1, 6, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 2: Klimawandel und Energiebedarf', 2, 5, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 0, 6, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 1, 6, 'Zielkonflikte gut erkannt.'),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 2, 5, 'Klares Fazit, ein lokaler Bezug könnte genauer sein.'),
        ('Erdkunde', 'Mia', 'Weber', 'Aufgabe 3: Transformation des Energiesektors beurteilen', 3, 5, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Strukturiert arbeiten', 0, 5, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Strukturiert arbeiten', 1, 4, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Strukturiert arbeiten', 2, 3, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Belege nutzen', 0, 4, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Belege nutzen', 1, 3, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Sprachlich korrekt schreiben', 0, 4, ''),
        ('Erdkunde', 'Mia', 'Weber', 'Sprachlich korrekt schreiben', 1, 3, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 0, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 1, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 2, 4, 'Katalysatorwirkung noch genauer am Diagramm erklären.'),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 1: Reaktionsgeschwindigkeiten und Kollisionstheorie', 3, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 0, 6, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 1, 5, 'Druck- und Temperatureinfluss klarer trennen.'),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 2: Chemisches Gleichgewicht und Gleichgewichtseinstellungen', 2, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 0, 6, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 1, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 2, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Aufgabe 3: Haber-Bosch-Verfahren und Einflussfaktoren', 3, 5, ''),
        ('Chemie', 'Finn', 'Becker', 'Strukturiert arbeiten', 0, 4, ''),
        ('Chemie', 'Finn', 'Becker', 'Strukturiert arbeiten', 1, 4, ''),
        ('Chemie', 'Finn', 'Becker', 'Strukturiert arbeiten', 2, 3, ''),
        ('Chemie', 'Finn', 'Becker', 'Belege nutzen', 0, 4, ''),
        ('Chemie', 'Finn', 'Becker', 'Belege nutzen', 1, 3, ''),
        ('Chemie', 'Finn', 'Becker', 'Sprachlich korrekt schreiben', 0, 4, ''),
        ('Chemie', 'Finn', 'Becker', 'Sprachlich korrekt schreiben', 1, 3, '')
) demo(subject_name, pupil_name, pupil_surname, task_title, requirement_sort_order, points, comment_text)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
join eh_part part
    on part.exam_id = e.id
join eh_category category
    on category.part_id = part.id
join eh_task task
    on task.category_id = category.id
   and task.title = demo.task_title
join eh_requirement requirement
    on requirement.task_id = task.id
   and requirement.sort_order = demo.requirement_sort_order
join pupil
    on pupil.name = demo.pupil_name
   and pupil.surname = demo.pupil_surname
where not exists (
    select 1
    from eh_requirement_result result
    where result.requirement_id = requirement.id
      and result.pupil_id = pupil.id
);

insert into eh_criterion_result (criterion_id, pupil_id, achieved)
select criterion.id, pupil.id, criterion.sort_order < result.points
from eh_criterion criterion
join eh_requirement requirement
    on requirement.id = criterion.requirement_id
join eh_requirement_result result
    on result.requirement_id = requirement.id
join pupil
    on pupil.id = result.pupil_id
join eh_task task
    on task.id = requirement.task_id
join eh_category category
    on category.id = task.category_id
join eh_part part
    on part.id = category.part_id
join exam e
    on e.id = part.exam_id
join course c
    on c.id = e.course_id
join subject
    on subject.id = c.subject_id
where subject.name in ('Erdkunde', 'Chemie')
  and c.calendar_year = 2026
  and c.course_period = 'FULL_YEAR'
  and not exists (
      select 1
      from eh_criterion_result existing_result
      where existing_result.criterion_id = criterion.id
        and existing_result.pupil_id = pupil.id
  );

insert into exam_note_section (exam_id, title, description_markdown, sort_order)
select e.id, demo.title, demo.description_markdown, demo.sort_order
from (
    values
        ('Erdkunde', 0, 'Hinweise / Tipps', 'Beim Überarbeiten besonders auf Materialbelege, Gewichtung der Zielkonflikte und ein klares Fazit achten.'),
        ('Chemie', 0, 'Hinweise / Tipps', 'Beim Überarbeiten besonders auf Teilchenmodell, Gleichgewichtsbegründung und saubere Fachsprache achten.')
) demo(subject_name, sort_order, title, description_markdown)
join course c
    on c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join subject
    on subject.id = c.subject_id
   and subject.name = demo.subject_name
join exam e
    on e.course_id = c.id
where not exists (
    select 1
    from exam_note_section note
    where note.exam_id = e.id
      and note.title = demo.title
);
