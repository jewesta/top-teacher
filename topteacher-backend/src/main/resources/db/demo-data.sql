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

insert into course (school_class, subject, calendar_year, course_period, lifecycle, grading_scale_id)
select demo.school_class, demo.subject, demo.calendar_year, demo.course_period, demo.lifecycle, gs.id
from (
    values
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_Q1', 'SPANISH', 2025, 'FULL_YEAR', 'INACTIVE')
) demo(school_class, subject, calendar_year, course_period, lifecycle)
join grading_scale gs
    on gs.name = 'Standard'
where not exists (
    select 1
    from course c
    where c.school_class = demo.school_class
      and c.subject = demo.subject
      and c.calendar_year = demo.calendar_year
      and c.course_period = demo.course_period
);

insert into exam (course_id, title, exam_date)
select c.id, demo.title, demo.exam_date
from (
    values
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', '1. Klausur', date '2026-09-22'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', '2. Klausur', date '2026-12-08'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', '1. Klausur', date '2026-09-29'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', '1. Klausur', date '2026-10-06'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', '1. Klausur', date '2026-09-24')
) demo(school_class, subject, calendar_year, course_period, title, exam_date)
join course c
    on c.school_class = demo.school_class
   and c.subject = demo.subject
   and c.calendar_year = demo.calendar_year
   and c.course_period = demo.course_period
where not exists (
    select 1
    from exam e
    where e.course_id = c.id
      and e.title = demo.title
);

insert into course_pupil (course_id, pupil_id)
select c.id, p.id
from (
    values
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Emma', 'Schneider'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Noah', 'Fischer'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Mia', 'Weber'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Ben', 'Meyer'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Hannah', 'Wagner'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Finn', 'Becker'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Lina', 'Hoffmann'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Paul', 'Schulz'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Lea', 'Koch'),
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'Luis', 'Bauer'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Emma', 'Schneider'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Mia', 'Weber'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Hannah', 'Wagner'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Lina', 'Hoffmann'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Lea', 'Koch'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Sofia', 'Richter'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Marie', 'Wolf'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'Clara', 'Neumann'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Sofia', 'Richter'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Jonas', 'Klein'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Marie', 'Wolf'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Elias', 'Schroeder'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Clara', 'Neumann'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Leon', 'Schwarz'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Anna', 'Zimmermann'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'Oskar', 'Braun'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Finn', 'Becker'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Paul', 'Schulz'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Luis', 'Bauer'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Jonas', 'Klein'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Elias', 'Schroeder'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Leon', 'Schwarz'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Oskar', 'Braun')
) demo(school_class, subject, calendar_year, course_period, pupil_name, pupil_surname)
join course c
    on c.school_class = demo.school_class
   and c.subject = demo.subject
   and c.calendar_year = demo.calendar_year
   and c.course_period = demo.course_period
join pupil p
    on p.name = demo.pupil_name
   and p.surname = demo.pupil_surname
where not exists (
    select 1
    from course_pupil cp
    where cp.course_id = c.id
      and cp.pupil_id = p.id
);

insert into exam (course_id, title, exam_date)
select c.id, demo.title, demo.exam_date
from (
    values
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'Klausur Nr. 4', date '2026-05-21')
) demo(school_class, subject, calendar_year, course_period, title, exam_date)
join course c
    on c.school_class = demo.school_class
   and c.subject = demo.subject
   and c.calendar_year = demo.calendar_year
   and c.course_period = demo.course_period
where not exists (
    select 1
    from exam e
    where e.course_id = c.id
      and e.title = demo.title
);

update eh_part
set title = case
    when title = 'Klausurteil A (70%): Schreiben mit Leseverstehen (integriert)' then 'Klausurteil A: Schreiben mit Leseverstehen (integriert)'
    when title = 'Klausurteil B (30%): schriftliche Sprachmittlung D-E (isoliert)' then 'Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)'
    else title
end
where exam_id = (
    select e.id
    from exam e
    join course c on c.id = e.course_id
    where c.school_class = 'CLS_EF'
      and c.subject = 'ENGLISH'
      and c.calendar_year = 2026
      and c.course_period = 'FULL_YEAR'
      and e.title = 'Klausur Nr. 4'
)
  and title in (
      'Klausurteil A (70%): Schreiben mit Leseverstehen (integriert)',
      'Klausurteil B (30%): schriftliche Sprachmittlung D-E (isoliert)'
  );

insert into eh_part (exam_id, title, sort_order)
select e.id, demo.title, demo.sort_order
from (
    values
        (0, 'Klausurteil A: Schreiben mit Leseverstehen (integriert)'),
        (1, 'Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)')
) demo(sort_order, title)
join course c
    on c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join exam e
    on e.course_id = c.id
   and e.title = 'Klausur Nr. 4'
where not exists (
    select 1
    from eh_part p
    where p.exam_id = e.id
      and p.title = demo.title
);

insert into eh_category (part_id, title, description_markdown, sort_order)
select p.id, demo.title, demo.description_markdown, demo.sort_order
from (
    values
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 0, '1. Inhaltliche Leistung', ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 1, '2. Sprachliche Leistung / Darstellungsleistung', 'Die Bewertung erfolgt orientiert an den in den Lehrplänen ausgewiesenen Referenzniveaus des *Gemeinsamen europäischen Referenzrahmens* (GeR).'),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 0, '1. Inhaltliche Leistung', ''),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 1, '2. Sprachliche Leistung / Darstellungsleistung', 'Die Bewertung erfolgt orientiert an den in den Kernlehrplänen ausgewiesenen Referenzniveaus des *Gemeinsamen europäischen Referenzrahmens* (GeR).')
) demo(part_title, sort_order, title, description_markdown)
join course c
    on c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join exam e
    on e.course_id = c.id
   and e.title = 'Klausur Nr. 4'
join eh_part p
    on p.exam_id = e.id
   and p.title = demo.part_title
where not exists (
    select 1
    from eh_category cat
    where cat.part_id = p.id
      and cat.title = demo.title
);

update eh_task
set title = case
    when title = 'Kommunikative Textgestaltung [14 P.]' then 'Kommunikative Textgestaltung'
    when title = 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel [14 P.]' then 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel'
    when title = 'Sprachrichtigkeit [14 P.]' then 'Sprachrichtigkeit'
    else title
end
where title in (
      'Kommunikative Textgestaltung [14 P.]',
      'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel [14 P.]',
      'Sprachrichtigkeit [14 P.]'
  )
  and category_id in (
      select cat.id
      from eh_category cat
      join eh_part p on p.id = cat.part_id
      join exam e on e.id = p.exam_id
      join course c on c.id = e.course_id
      where c.school_class = 'CLS_EF'
        and c.subject = 'ENGLISH'
        and c.calendar_year = 2026
        and c.course_period = 'FULL_YEAR'
        and e.title = 'Klausur Nr. 4'
  );

insert into eh_task (category_id, title, sort_order)
select cat.id, demo.title, demo.sort_order
from (
    values
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 0, 'Teilaufgabe 1 (Comprehension)'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 1, 'Teilaufgabe 2 (Analysis)'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 2, 'Teilaufgabe 3 (Re-creation of text)'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit'),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '1. Inhaltliche Leistung', 0, 'Inhaltliche Leistung'),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '2. Sprachliche Leistung / Darstellungsleistung', 0, 'Sprachliche Leistung')
) demo(part_title, category_title, sort_order, title)
join course c
    on c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join exam e
    on e.course_id = c.id
   and e.title = 'Klausur Nr. 4'
join eh_part p
    on p.exam_id = e.id
   and p.title = demo.part_title
join eh_category cat
    on cat.part_id = p.id
   and cat.title = demo.category_title
where not exists (
    select 1
    from eh_task t
    where t.category_id = cat.id
      and t.title = demo.title
);

insert into eh_requirement (task_id, description_markdown, max_points, bonus, sort_order)
select t.id, demo.description_markdown, demo.max_points, demo.bonus, demo.sort_order
from (
    values
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 0, 6, false,
'stellt dar, aus welchen Gründen Jugendliche laut Aussage des Autors mehr politische Mitsprache bei der Klimapolitik erhalten sollen:

- Jugendliche sind am meisten von den [Folgen des Klimawandels](eh:1) betroffen
- das [Tempo des Klimawandels](eh:2) nimmt zu und der Zeitraum für effektive Gegenmaßnahmen nimmt ab
- es ist undemokratisch, eine [Generation von Jugendlichen](eh:3) von politischen Entscheidungen auszuschließen, die ihre Zukunft prägen
- durch die Förderung ihres [Verantwortungsbewusstseins](eh:4) ist eine erfolgreichere Umsetzung von Klimapolitik möglich
- es ist eine Frage der [generationenübergreifenden Gerechtigkeit](eh:5)
- Jugendliche haben ein [vertieftes Verständnis](eh:6) der aktuellen Herausforderungen'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 0, 2, false,
'Der Autor Anurit Kanti ruft dazu auf, Jugendliche ab sofort in die Klimapolitik aktiv einzubinden und sicherzustellen, dass sie ihre Lebenswirklichkeit mitgestalten können. Er verleiht seiner [Aussageabsicht](eh:1) Nachdruck durch:

- die Verwendung von [Argumentationsstrategien](eh:2), die die Bedeutsamkeit der Jugendlichen betonen
- einen spezifischen Sprachgebrauch, der die Dringlichkeit des Handelns hervorhebt.'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 1, 6, false,
'analysiert die Argumentationsstrategien, z. B.:

- [Widerspruch](eh:1): Jugendliche haben zu wenig Mitspracherecht, obwohl sie am meisten betroffen sind
  - verdeutlicht die Handlungsnotwendigkeit
- [Studie Global Risks Report 2025](eh:2)
  - belegt, dass globale Risiken durch klimabedingte Extremwetter noch nie so hoch waren
- Einfügen von [statistischen Fakten](eh:3)
  - belegen den zu niedrigen Anteil von jungen Politikern
- Herstellen eines [Kontrastes](eh:4) zwischen Folgen der Klimapolitik und fehlender Beteiligung
- [persönliche Erfahrung](eh:5) des Autors in einer politisch aktiven Organisation
- [Beispiele für Projekte](eh:6), die von Jugendlichen gegründet wurden'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 2, 4, false,
'analysiert den Sprachgebrauch, z. B.:

- [adversative Adverbien](eh:1) und Präpositionen wie „yet“ und „despite“
- [Aufzählungen](eh:2), z. B. „it is a matter of justice, effectiveness and innovation“
- [Parallelismus](eh:3), z. B. „The emissions we release today ...“
- [Metaphorik](eh:4), z. B. „the wheels need to be set in motion“'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 3, 2, true,
'erfüllt ein weiteres aufgabenbezogenes Kriterium'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 3 (Re-creation of text)', 0, 2, false,
'verfasst einen adressaten- und situationsgerechten [Redebeitrag](eh:1), der:

- die vorgegebenen Rahmenbedingungen konsequent berücksichtigt
- eine [Intention](eh:2) aufzeigt'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 3 (Re-creation of text)', 1, 6, false,
'nennt Gründe für den Schutz von biologisch diversen Ökosystemen, etwa:

- biologisch diverse Ökosysteme bilden die [Lebensgrundlage](eh:1) auf der Erde
- sie liefern [Sauerstoff, Wasser, Essen und Medizin](eh:2)
- durch Abholzung, Umweltverschmutzung und Klimawandel gerät die [Natur aus dem Gleichgewicht](eh:3)
- intakte Ökosysteme können den [Klimawandel abschwächen](eh:4)
- biologisch diverse Ökosysteme dienen als [Schutz unserer Zukunft](eh:5)
- nachhaltiger Anbau von Lebensmitteln trägt zur [Einkommenssicherung](eh:6) bei'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 3 (Re-creation of text)', 2, 2, false,
'schließt die Rede mit Blick auf die in der Aufgabe zum Ausdruck gebrachten [Ideen](eh:1) und auf Basis der vorangegangenen [Ausführungen](eh:2)'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '1. Inhaltliche Leistung', 'Teilaufgabe 3 (Re-creation of text)', 3, 2, true,
'erfüllt ein weiteres aufgabenbezogenes Kriterium'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, 6, false,
'**Aufgabenbezug/Textformate:** richtet ihren / seinen Text auf die [Aufgabenstellung](eh:1) aus und beachtet die [Textsortenmerkmale](eh:2) der jeweils geforderten Zielformate. Der Text ist [adressatengerecht](eh:3), [situationsgerecht](eh:4), [funktional](eh:5) und [kohärent](eh:6).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, 4, false,
'**Textaufbau:** erstellt einen sachgerecht strukturierten, [leserfreundlichen Text](eh:1), u. a. durch [sprachliche Verknüpfungen](eh:2), [Absätze](eh:3) und erkennbare [Sinnabschnitte](eh:4).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, 4, false,
'**Ökonomie (/Belegtechnik):** formuliert [hinreichend ausführlich](eh:1), aber ohne [unnötige Wiederholungen](eh:2) und [Umständlichkeiten](eh:3), auch unter funktionaler Verwendung von [Verweisen und Zitaten](eh:4).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 2, false,
'**Eigenständigkeit:** löst sich vom [Ausgangstext](eh:1) und formuliert [eigenständig](eh:2).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, 8, false,
'**Wortschatz:** verwendet einen [sachlichen Wortschatz](eh:1), [stilistisch angemessenen Wortschatz](eh:2), [differenzierten Wortschatz](eh:3), [allgemeinen Wortschatz](eh:4), [thematischen Wortschatz](eh:5), [analytischen Wortschatz](eh:6), [präzise Formulierungen](eh:7) und [variierende Ausdrücke](eh:8).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, 4, false,
'**Satzbau:** verwendet einen [variablen Satzbau](eh:1), dem jeweiligen [Zielformat](eh:2) angemessene [Syntax](eh:3) und [klare Satzstrukturen](eh:4).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, 6, false,
'**Wortschatz:** beachtet die Normen der sprachlichen Korrektheit durch [treffende Wortwahl](eh:1), [korrekte Kollokationen](eh:2), [angemessene Registerwahl](eh:3), [lexikalische Genauigkeit](eh:4), [thematische Passung](eh:5) und [Variation](eh:6).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, 6, false,
'**Grammatik:** beachtet [Zeitformen](eh:1), [Satzstellung](eh:2), [Kongruenz](eh:3), [Präpositionen](eh:4), [Artikelgebrauch](eh:5) und [komplexere Strukturen](eh:6).'),

        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, 2, false,
'**Orthographie:** beachtet [Rechtschreibung](eh:1) und [Zeichensetzung](eh:2).'),

        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '1. Inhaltliche Leistung', 'Inhaltliche Leistung', 0, 6, false,
'stellt die im Artikel genannten Klimaziele der EU-Mitgliedstaaten für 2040 dar:

- [Reduktion der Treibhausgasemissionen](eh:1) um 90 % bis 2040 im Vergleich zu 1990
- ab 2036 dürfen bis zu [5 Prozentpunkte](eh:2) der Reduktion durch internationale Klimazertifikate erreicht werden
- dadurch müssen die EU-Staaten ihre [eigenen Emissionen](eh:3) effektiv nur um 85 % senken
- Einführung des [Emissionshandels](eh:4) für Verkehr, Gebäude und weitere Bereiche
- Umsetzung erfolgt [erst ab 2028](eh:5)
- Klimaziele werden als [politische Einigung](eh:6) dargestellt'),

        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '1. Inhaltliche Leistung', 'Inhaltliche Leistung', 1, 6, false,
'stellt die im Artikel genannte Reaktion des deutschen Umweltministers auf die Entscheidungen dar:

- er begrüßt die Einigung als [wegweisenden Beschluss](eh:1)
- er bewertet die Entscheidung als [Stärkung Europas](eh:2)
- er betont die [Sicherung Europas](eh:3)
- weniger Verbrauch von [Öl](eh:4) sei vorteilhaft
- weniger Verbrauch von [Gas](eh:5) sei in der aktuellen Weltlage wichtig
- die Entscheidung habe einen [Vorteil für Europa](eh:6)'),

        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '1. Inhaltliche Leistung', 'Inhaltliche Leistung', 2, 2, true,
'erfüllt ein weiteres aufgabenbezogenes Kriterium'),

        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Sprachliche Leistung', 0, 6, false,
'**Kommunikative Textgestaltung:** richtet ihren / seinen Text konsequent und explizit auf die [Intention](eh:1) und den [Adressaten](eh:2) aus, berücksichtigt den [situativen Kontext](eh:3), beachtet die [Textsortenmerkmale](eh:4), erstellt einen [strukturierten Text](eh:5) und gestaltet ihn [hinreichend ausführlich](eh:6).'),

        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Sprachliche Leistung', 1, 6, false,
'**Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel:** löst sich vom [Wortlaut des Ausgangstextes](eh:1), formuliert [eigenständig](eh:2), verwendet [Kompensationsstrategien](eh:3), nutzt einen [sachlichen Wortschatz](eh:4), nutzt einen [thematischen Wortschatz](eh:5) und verwendet einen [variablen Satzbau](eh:6).'),

        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', '2. Sprachliche Leistung / Darstellungsleistung', 'Sprachliche Leistung', 2, 6, false,
'**Sprachrichtigkeit:** beachtet die Normen der sprachlichen Korrektheit in den Bereichen [Wortschatz](eh:1), [Grammatik](eh:2), [Orthographie](eh:3), [Zeichensetzung](eh:4), [Kongruenz](eh:5) und [Satzstellung](eh:6).')
) demo(part_title, category_title, task_title, sort_order, max_points, bonus, description_markdown)
join course c
    on c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join exam e
    on e.course_id = c.id
   and e.title = 'Klausur Nr. 4'
join eh_part p
    on p.exam_id = e.id
   and p.title = demo.part_title
join eh_category cat
    on cat.part_id = p.id
   and cat.title = demo.category_title
join eh_task t
    on t.category_id = cat.id
   and t.title = demo.task_title
where not exists (
    select 1
    from eh_requirement r
    where r.task_id = t.id
      and r.sort_order = demo.sort_order
);

insert into eh_criterion (requirement_id, criterion_key, label, sort_order, active)
select r.id, demo.criterion_key, demo.label, demo.criterion_sort_order, true
from (
    values
        ('Teilaufgabe 1 (Comprehension)', 0, '1', 'Folgen des Klimawandels', 0),
        ('Teilaufgabe 1 (Comprehension)', 0, '2', 'Tempo des Klimawandels', 1),
        ('Teilaufgabe 1 (Comprehension)', 0, '3', 'Generation von Jugendlichen', 2),
        ('Teilaufgabe 1 (Comprehension)', 0, '4', 'Verantwortungsbewusstsein', 3),
        ('Teilaufgabe 1 (Comprehension)', 0, '5', 'generationenübergreifende Gerechtigkeit', 4),
        ('Teilaufgabe 1 (Comprehension)', 0, '6', 'vertieftes Verständnis', 5),
        ('Teilaufgabe 2 (Analysis)', 0, '1', 'Aussageabsicht', 0),
        ('Teilaufgabe 2 (Analysis)', 0, '2', 'Argumentationsstrategien', 1),
        ('Teilaufgabe 2 (Analysis)', 1, '1', 'Widerspruch', 0),
        ('Teilaufgabe 2 (Analysis)', 1, '2', 'Studie Global Risks Report 2025', 1),
        ('Teilaufgabe 2 (Analysis)', 1, '3', 'statistische Fakten', 2),
        ('Teilaufgabe 2 (Analysis)', 1, '4', 'Kontrast', 3),
        ('Teilaufgabe 2 (Analysis)', 1, '5', 'persönliche Erfahrung', 4),
        ('Teilaufgabe 2 (Analysis)', 1, '6', 'Beispiele für Projekte', 5),
        ('Teilaufgabe 2 (Analysis)', 2, '1', 'adversative Adverbien', 0),
        ('Teilaufgabe 2 (Analysis)', 2, '2', 'Aufzählungen', 1),
        ('Teilaufgabe 2 (Analysis)', 2, '3', 'Parallelismus', 2),
        ('Teilaufgabe 2 (Analysis)', 2, '4', 'Metaphorik', 3),
        ('Teilaufgabe 3 (Re-creation of text)', 0, '1', 'Redebeitrag', 0),
        ('Teilaufgabe 3 (Re-creation of text)', 0, '2', 'Intention', 1),
        ('Teilaufgabe 3 (Re-creation of text)', 1, '1', 'Lebensgrundlage', 0),
        ('Teilaufgabe 3 (Re-creation of text)', 1, '2', 'Sauerstoff, Wasser, Essen und Medizin', 1),
        ('Teilaufgabe 3 (Re-creation of text)', 1, '3', 'Natur aus dem Gleichgewicht', 2),
        ('Teilaufgabe 3 (Re-creation of text)', 1, '4', 'Klimawandel abschwächen', 3),
        ('Teilaufgabe 3 (Re-creation of text)', 1, '5', 'Schutz unserer Zukunft', 4),
        ('Teilaufgabe 3 (Re-creation of text)', 1, '6', 'Einkommenssicherung', 5),
        ('Teilaufgabe 3 (Re-creation of text)', 2, '1', 'Ideen', 0),
        ('Teilaufgabe 3 (Re-creation of text)', 2, '2', 'Ausführungen', 1),
        ('Kommunikative Textgestaltung', 0, '1', 'Aufgabenstellung', 0),
        ('Kommunikative Textgestaltung', 0, '2', 'Textsortenmerkmale', 1),
        ('Kommunikative Textgestaltung', 0, '3', 'adressatengerecht', 2),
        ('Kommunikative Textgestaltung', 0, '4', 'situationsgerecht', 3),
        ('Kommunikative Textgestaltung', 0, '5', 'funktional', 4),
        ('Kommunikative Textgestaltung', 0, '6', 'kohärent', 5),
        ('Kommunikative Textgestaltung', 1, '1', 'leserfreundlicher Text', 0),
        ('Kommunikative Textgestaltung', 1, '2', 'sprachliche Verknüpfungen', 1),
        ('Kommunikative Textgestaltung', 1, '3', 'Absätze', 2),
        ('Kommunikative Textgestaltung', 1, '4', 'Sinnabschnitte', 3),
        ('Kommunikative Textgestaltung', 2, '1', 'hinreichend ausführlich', 0),
        ('Kommunikative Textgestaltung', 2, '2', 'unnötige Wiederholungen', 1),
        ('Kommunikative Textgestaltung', 2, '3', 'Umständlichkeiten', 2),
        ('Kommunikative Textgestaltung', 2, '4', 'Verweise und Zitate', 3),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, '1', 'Ausgangstext', 0),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, '2', 'eigenständig', 1),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '1', 'sachlicher Wortschatz', 0),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '2', 'stilistisch angemessener Wortschatz', 1),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '3', 'differenzierter Wortschatz', 2),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '4', 'allgemeiner Wortschatz', 3),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '5', 'thematischer Wortschatz', 4),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '6', 'analytischer Wortschatz', 5),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '7', 'präzise Formulierungen', 6),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '8', 'variierende Ausdrücke', 7),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '1', 'variabler Satzbau', 0),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '2', 'Zielformat', 1),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '3', 'Syntax', 2),
        ('Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '4', 'klare Satzstrukturen', 3),
        ('Sprachrichtigkeit', 0, '1', 'treffende Wortwahl', 0),
        ('Sprachrichtigkeit', 0, '2', 'korrekte Kollokationen', 1),
        ('Sprachrichtigkeit', 0, '3', 'angemessene Registerwahl', 2),
        ('Sprachrichtigkeit', 0, '4', 'lexikalische Genauigkeit', 3),
        ('Sprachrichtigkeit', 0, '5', 'thematische Passung', 4),
        ('Sprachrichtigkeit', 0, '6', 'Variation', 5),
        ('Sprachrichtigkeit', 1, '1', 'Zeitformen', 0),
        ('Sprachrichtigkeit', 1, '2', 'Satzstellung', 1),
        ('Sprachrichtigkeit', 1, '3', 'Kongruenz', 2),
        ('Sprachrichtigkeit', 1, '4', 'Präpositionen', 3),
        ('Sprachrichtigkeit', 1, '5', 'Artikelgebrauch', 4),
        ('Sprachrichtigkeit', 1, '6', 'komplexere Strukturen', 5),
        ('Sprachrichtigkeit', 2, '1', 'Rechtschreibung', 0),
        ('Sprachrichtigkeit', 2, '2', 'Zeichensetzung', 1),
        ('Inhaltliche Leistung', 0, '1', 'Reduktion der Treibhausgasemissionen', 0),
        ('Inhaltliche Leistung', 0, '2', '5 Prozentpunkte', 1),
        ('Inhaltliche Leistung', 0, '3', 'eigene Emissionen', 2),
        ('Inhaltliche Leistung', 0, '4', 'Emissionshandel', 3),
        ('Inhaltliche Leistung', 0, '5', 'erst ab 2028', 4),
        ('Inhaltliche Leistung', 0, '6', 'politische Einigung', 5),
        ('Inhaltliche Leistung', 1, '1', 'wegweisender Beschluss', 0),
        ('Inhaltliche Leistung', 1, '2', 'Stärkung Europas', 1),
        ('Inhaltliche Leistung', 1, '3', 'Sicherung Europas', 2),
        ('Inhaltliche Leistung', 1, '4', 'Öl', 3),
        ('Inhaltliche Leistung', 1, '5', 'Gas', 4),
        ('Inhaltliche Leistung', 1, '6', 'Vorteil für Europa', 5),
        ('Sprachliche Leistung', 0, '1', 'Intention', 0),
        ('Sprachliche Leistung', 0, '2', 'Adressaten', 1),
        ('Sprachliche Leistung', 0, '3', 'situativer Kontext', 2),
        ('Sprachliche Leistung', 0, '4', 'Textsortenmerkmale', 3),
        ('Sprachliche Leistung', 0, '5', 'strukturierter Text', 4),
        ('Sprachliche Leistung', 0, '6', 'hinreichend ausführlich', 5),
        ('Sprachliche Leistung', 1, '1', 'Wortlaut des Ausgangstextes', 0),
        ('Sprachliche Leistung', 1, '2', 'eigenständig', 1),
        ('Sprachliche Leistung', 1, '3', 'Kompensationsstrategien', 2),
        ('Sprachliche Leistung', 1, '4', 'sachlicher Wortschatz', 3),
        ('Sprachliche Leistung', 1, '5', 'thematischer Wortschatz', 4),
        ('Sprachliche Leistung', 1, '6', 'variabler Satzbau', 5),
        ('Sprachliche Leistung', 2, '1', 'Wortschatz', 0),
        ('Sprachliche Leistung', 2, '2', 'Grammatik', 1),
        ('Sprachliche Leistung', 2, '3', 'Orthographie', 2),
        ('Sprachliche Leistung', 2, '4', 'Zeichensetzung', 3),
        ('Sprachliche Leistung', 2, '5', 'Kongruenz', 4),
        ('Sprachliche Leistung', 2, '6', 'Satzstellung', 5)
) demo(task_title, requirement_sort_order, criterion_key, label, criterion_sort_order)
join eh_task t
    on t.title = demo.task_title
join eh_requirement r
    on r.task_id = t.id
   and r.sort_order = demo.requirement_sort_order
join eh_category cat
    on cat.id = t.category_id
join eh_part part
    on part.id = cat.part_id
join exam e
    on e.id = part.exam_id
   and e.title = 'Klausur Nr. 4'
join course c
    on c.id = e.course_id
   and c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
where not exists (
    select 1
    from eh_criterion cr
    where cr.requirement_id = r.id
      and cr.criterion_key = demo.criterion_key
);

insert into eh_requirement_result (requirement_id, pupil_id, points, comment_text)
select r.id, pupil.id, demo.points, demo.comment_text
from (
    values
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 1 (Comprehension)', 0, 5, 'Sehr klare Auswahl der Hauptgründe.'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 2 (Analysis)', 0, 2, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 2 (Analysis)', 1, 4, 'Mehr Textbelege wären möglich.'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 2 (Analysis)', 2, 3, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 2 (Analysis)', 3, 0, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 3 (Re-creation of text)', 0, 2, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 3 (Re-creation of text)', 1, 5, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 3 (Re-creation of text)', 2, 1, 'Schluss etwas knapp.'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Teilaufgabe 3 (Re-creation of text)', 3, 0, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Kommunikative Textgestaltung', 0, 5, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Kommunikative Textgestaltung', 1, 3, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Kommunikative Textgestaltung', 2, 4, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 2, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, 6, 'Wortschatz überwiegend sicher.'),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, 3, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachrichtigkeit', 0, 4, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachrichtigkeit', 1, 5, ''),
        ('Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachrichtigkeit', 2, 2, ''),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 0, 5, ''),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 1, 4, 'Ein Aspekt fehlt.'),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 2, 0, ''),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung', 0, 5, ''),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung', 1, 4, ''),
        ('Klausurteil B: schriftliche Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung', 2, 5, '')
) demo(part_title, task_title, requirement_sort_order, points, comment_text)
join eh_task t
    on t.title = demo.task_title
join eh_requirement r
    on r.task_id = t.id
   and r.sort_order = demo.requirement_sort_order
join eh_category cat
    on cat.id = t.category_id
join eh_part part
    on part.id = cat.part_id
   and part.title = demo.part_title
join exam e
    on e.id = part.exam_id
   and e.title = 'Klausur Nr. 4'
join course c
    on c.id = e.course_id
   and c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join pupil
    on pupil.name = 'Finn'
   and pupil.surname = 'Becker'
where not exists (
    select 1
    from eh_requirement_result result
    where result.requirement_id = r.id
      and result.pupil_id = pupil.id
);

insert into eh_criterion_result (criterion_id, pupil_id, achieved)
select cr.id, pupil.id, cr.sort_order < result.points
from eh_criterion cr
join eh_requirement r
    on r.id = cr.requirement_id
join eh_requirement_result result
    on result.requirement_id = r.id
join pupil
    on pupil.id = result.pupil_id
join eh_task t
    on t.id = r.task_id
join eh_category cat
    on cat.id = t.category_id
join eh_part part
    on part.id = cat.part_id
join exam e
    on e.id = part.exam_id
join course c
    on c.id = e.course_id
where e.title = 'Klausur Nr. 4'
  and c.school_class = 'CLS_EF'
  and c.subject = 'ENGLISH'
  and c.calendar_year = 2026
  and c.course_period = 'FULL_YEAR'
  and pupil.name = 'Finn'
  and pupil.surname = 'Becker'
  and not exists (
      select 1
      from eh_criterion_result existing_result
      where existing_result.criterion_id = cr.id
        and existing_result.pupil_id = pupil.id
  );

insert into exam_note_section (exam_id, title, description_markdown, sort_order)
select e.id, demo.title, demo.description_markdown, demo.sort_order
from (
    values
        (0, 'Hinweise / Tipps', 'Beim Überarbeiten besonders auf Belege, klare Struktur und präzise Formulierungen achten.')
) demo(sort_order, title, description_markdown)
join course c
    on c.school_class = 'CLS_EF'
   and c.subject = 'ENGLISH'
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join exam e
    on e.course_id = c.id
   and e.title = 'Klausur Nr. 4'
where not exists (
    select 1
    from exam_note_section note
    where note.exam_id = e.id
      and note.title = demo.title
);
