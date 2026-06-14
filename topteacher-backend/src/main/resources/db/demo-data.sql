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
        ('CLS_Q2', 'Englisch', 2026, 'FULL_YEAR', 'ACTIVE', 'Qualifikationsphase'),
        ('CLS_Q1', 'Spanisch', 2026, 'FULL_YEAR', 'ACTIVE', 'Qualifikationsphase')
) demo(school_class, subject_name, calendar_year, course_period, lifecycle, grading_scale_name)
join subject
    on subject.name = demo.subject_name
join grading_scale gs
    on gs.name = demo.grading_scale_name
where not exists (
    select 1
    from course c
    where c.school_class = demo.school_class
      and c.subject_id = subject.id
      and c.calendar_year = demo.calendar_year
      and c.course_period = demo.course_period
);

insert into exam (course_id, title, exam_date, grading_scale_id)
select c.id, demo.title, demo.exam_date, c.grading_scale_id
from (
    values
        ('CLS_Q2', 'Englisch', 2026, 'FULL_YEAR', '1. Klausur Shakespeare', date '2026-11-17'),
        ('CLS_Q1', 'Spanisch', 2026, 'FULL_YEAR', 'Examen No 3: Migración', date '2026-12-08')
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
from (
    values
        ('CLS_Q2', 'Englisch', 'Emma', 'Schneider'),
        ('CLS_Q2', 'Englisch', 'Mia', 'Weber'),
        ('CLS_Q2', 'Englisch', 'Hannah', 'Wagner'),
        ('CLS_Q2', 'Englisch', 'Paul', 'Schulz'),
        ('CLS_Q2', 'Englisch', 'Clara', 'Neumann'),
        ('CLS_Q1', 'Spanisch', 'Noah', 'Fischer'),
        ('CLS_Q1', 'Spanisch', 'Ben', 'Meyer'),
        ('CLS_Q1', 'Spanisch', 'Finn', 'Becker'),
        ('CLS_Q1', 'Spanisch', 'Lina', 'Hoffmann'),
        ('CLS_Q1', 'Spanisch', 'Luis', 'Bauer'),
        ('CLS_Q1', 'Spanisch', 'Sofia', 'Richter'),
        ('CLS_Q1', 'Spanisch', 'Jonas', 'Klein')
) demo(school_class, subject_name, pupil_name, pupil_surname)
join subject
    on subject.name = demo.subject_name
join course c
    on c.school_class = demo.school_class
   and c.subject_id = subject.id
   and c.calendar_year = 2026
   and c.course_period = 'FULL_YEAR'
join pupil p
    on p.name = demo.pupil_name
   and p.surname = demo.pupil_surname
where not exists (
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
        ('Englisch', '1. Klausur Shakespeare', 0, 'Klausurteil A: Schreiben mit Leseverstehen (integriert)'),
        ('Englisch', '1. Klausur Shakespeare', 1, 'Klausurteil B: Sprachmittlung D-E (isoliert)'),
        ('Spanisch', 'Examen No 3: Migración', 0, 'Klausurteil A: Leseverstehen integriert'),
        ('Spanisch', 'Examen No 3: Migración', 1, 'Klausurteil B: Hörverstehen isoliert')
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
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 0, 'Inhaltliche Leistung', ''),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 1, 'Sprachliche Leistung / Darstellungsleistung', 'Bewertung orientiert an den Referenzniveaus des Gemeinsamen europäischen Referenzrahmens (GeR).'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 0, 'Inhaltliche Leistung', ''),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 1, 'Sprachliche Leistung / Darstellungsleistung', 'Bewertung orientiert an den Referenzniveaus des Gemeinsamen europäischen Referenzrahmens (GeR).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 0, 'Inhaltliche Leistung', ''),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 1, 'Sprachliche Leistung / Darstellungsleistung', ''),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 0, 'Lösung', '')
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
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 0, 'Teilaufgabe 1 (Comprehension)'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 1, 'Teilaufgabe 2 (Analysis)'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 2, 'Teilaufgabe 3 (Comment)'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 0, 'Debate statement'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung (Sprachmittlung)'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel (Sprachmittlung)'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit (Sprachmittlung)'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 0, 'Aufgabe 1: Resumen'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 1, 'Aufgabe 2: Análisis'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 2, 'Aufgabe 3: Comentario'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 0, 'Hörverstehen')
) demo(subject_name, part_title, category_title, sort_order, title)
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
select task.id, demo.description_markdown, demo.max_points, demo.bonus, demo.sort_order
from (
    values
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 0, 4, false, 'nennt [Aspekte für Shakespeares ungebrochene Popularität](eh:1), etwa:
- Rang eines Nationalsymbols
- Sprachkunst
- Fähigkeit zur Psychologisierung
- tiefgehende Charakterdarstellungen
- durch Shakespeare geprägte Redewendungen'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 1, 4, false, 'nennt [Gründe gegen Shakespeares Eignung für den Schulunterricht](eh:1), etwa:
- Grammatik und Wortschatz gehören einer anderen Sprachstufe an
- literarisches Vorwissen fehlt bei der heutigen Schülerschaft
- die erschwerte Zugänglichkeit verhindert, Werte des Literaturunterrichts sinnvoll zu vermitteln; zeitgenössische Texte können diese Aufgabe übernehmen'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 2, 4, false, 'nennt [Schlussfolgerungen des Autors](eh:1), etwa:
- Shakespeares Werke sollten zur Wahrung seines Status nicht als Pflichtlektüre im Curriculum festgelegt werden
- Schülerinnen und Schüler könnten auf Zwang mit Ablehnung reagieren
- die Auseinandersetzung mit seinen Werken soll mit Freude verbunden sein'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 0, 4, false, 'untersucht [Robshaws Gesamtargumentation](eh:1), indem herausgearbeitet wird, dass der Autor Shakespeares Qualitäten hervorhebt und zugleich deutlich macht, dass ein fester Platz im Curriculum heute nicht mehr gerechtfertigt ist.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 1, 6, false, 'analysiert [Argumentationsgang und Funktion](eh:1) des Textes, indem erläutert wird, dass der Autor zum Beispiel:
- eine provokative Überschrift wählt, die bereits die Hauptthese enthält
- die Thematik mit der These einleitet: “isn’t it time we dropped him ...”
- Shakespeare als nationales Denkmal darstellt und dafür Beispiele anführt
- Argumente zur erschwerten Zugänglichkeit für Schülerinnen und Schüler entfaltet
- seine Argumente mit Beispielen stützt
- eine Schlussfolgerung mit Appell zieht'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 2, 6, false, 'analysiert [sprachliche Mittel und Leserlenkung](eh:1), zum Beispiel:
- rhetorische Fragen wie “isn’t it time we dropped him from the National Curriculum?” und “Surely this is a tribute our national writer deserves?”
- Aufzählungen wie “Along with a flag, an anthem and a football team ...” oder “aesthetic pleasure, understanding of character, moral sensitivity”
- Metaphern wie “The Bard is a national monument”
- Vergleiche wie “it’s like handing pupils a treasure in a locked chest”
- Personalpronomen wie “we” zur Herstellung von Gemeinsamkeit
- positiv und negativ konnotierte Wörter sowie unterschiedliche Sprachebenen'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 3, 4, true, 'erfüllt ein [weiteres aufgabenbezogenes Kriterium](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 0, 6, false, 'greift [die Position des Autors](eh:1) auf, indem zum Beispiel:
- die Aktualität des Themas problematisiert wird
- eigene Erfahrungen kritisch reflektiert werden
- der Schulkontext berücksichtigt wird'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 1, 6, false, 'positioniert sich [unter Rückgriff auf Unterrichtswissen](eh:1), indem zum Beispiel:
- auf Shakespeares Sprache verwiesen wird
- Schwierigkeiten beim Erschließen stilistischer Mittel benannt werden
- die zusätzliche Problematik für Fremdsprachenlernende erläutert wird
- der Gewinn durch die Beschäftigung mit Charakteren, Themen und Konflikten aufgezeigt wird'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 2, 2, false, 'bringt auf Grundlage der Argumentation [eine eigene begründete Sichtweise](eh:1) zum Ausdruck, indem die Argumente pointiert zusammengefasst werden.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 3, 2, true, 'erfüllt ein [weiteres aufgabenbezogenes Kriterium](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, 6, false, 'richtet seinen Text konsequent und explizit im Sinne der Aufgabenstellung auf [Intention und Adressaten](eh:1) aus.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, 4, false, 'beachtet die [Textsortenmerkmale](eh:1) der jeweils geforderten Zieltextformate.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, 4, false, 'erstellt einen [sachgerecht strukturierten Text](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, 4, false, 'gestaltet seinen Text hinreichend ausführlich, aber ohne [unnötige Wiederholungen und Umständlichkeiten](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, 3, false, 'belegt seine Aussagen durch eine [funktionale Verwendung von Verweisen und Zitaten](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 4, false, 'löst sich vom Wortlaut des Ausgangstextes und [formuliert eigenständig](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, 6, false, 'verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten [allgemeinen und thematischen Wortschatz](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, 4, false, 'verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten [Funktions- und Interpretationswortschatz](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, 7, false, 'verwendet einen variablen und dem jeweiligen Zieltextformat angemessenen [Satzbau](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, 9, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Wortschatz](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, 8, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Grammatik](eh:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, 4, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Orthographie](eh:1) (Rechtschreibung und Zeichensetzung).'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 0, 6, false, 'verfasst ein adressaten- und themenbezogenes [debate statement](eh:1), in dem auf Thematik und Motive von Shakespeares Werken eingegangen wird:
- aufgrund ihrer Zeitlosigkeit passend für jede Kultur, Nation und Epoche
- für jede Generation faszinierend'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 1, 6, false, 'verweist auf die [historische Bedeutung der Werke Shakespeares in Deutschland](eh:1):
- Grundstein der deutschen Theatertradition
- Bedeutung in der Zeit der Wiedervereinigung'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 2, 6, false, 'nimmt Bezug auf den [Unterhaltungswert des britischen Autors](eh:1):
- Eignung für Massenunterhaltung
- Vorlage für Hollywoodverfilmungen'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung (Sprachmittlung)', 0, 9, false, '[Kommunikative Textgestaltung](eh:1):
- richtet den Text konsequent und explizit auf Intention und Adressaten aus
- berücksichtigt den situativen Kontext
- beachtet Textsortenmerkmale des Zieltextformats
- erstellt einen sachgerecht strukturierten Text
- gestaltet hinreichend ausführlich, aber ohne unnötige Wiederholungen'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel (Sprachmittlung)', 0, 9, false, '[Ausdrucksvermögen und sprachliche Mittel](eh:1):
- löst sich vom Wortlaut des Ausgangstextes und formuliert eigenständig
- nutzt ggf. Kompensationsstrategien
- verwendet angemessenen und differenzierten allgemeinen, thematischen und funktionalen Wortschatz
- verwendet variablen und zieltextangemessenen Satzbau'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit (Sprachmittlung)', 0, 9, false, '[Sprachrichtigkeit](eh:1): beachtet die Normen sprachlicher Korrektheit in den Bereichen Wortschatz, Grammatik und Orthographie (Rechtschreibung und Zeichensetzung).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 0, 2, false, 'benennt [Titel, Autor, Textsorte und Erscheinungsort](eh:1) des Artikels und stellt in einem kurzen Einleitungssatz das Hauptthema dar.'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 1, 14, false, 'fasst [die relevanten Aspekte](eh:1) zusammen, zum Beispiel:
- el autor supone que los emigrantes españoles, al volver al extranjero, se llevan productos culinarios de España en sus maletas
- reconoce que algunos productos españoles también se pueden comprar en el extranjero pagando un poco más
- explica que lleva muchos años fuera de España, pero siempre ha estado muy unido a su gente y a su tierra
- siempre ha tenido curiosidad por conocer otras culturas; por eso emigró al extranjero con 20 años
- sus experiencias en Inglaterra y durante viajes al extranjero le han ayudado a ser la persona que es hoy
- reconoce que cada vez le cuesta más hacer la maleta para volver a Alemania
- preferiría hacer la maleta sólo para irse de vacaciones
- tiene que empaquetar la morriña y le pesa volver al trabajo en el extranjero
- le gustaría poder llevarse a su familia, sus amigos y la vida en Santiago
- quizás quienes hablan de la movilidad exterior saben dónde se puede comprar tal maleta'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 2, 2, true, 'erfüllt ein [weiteres aufgabenbezogenes Kriterium](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 0, 2, false, 'benennt [das Thema der Analyse](eh:1) und erstellt eine These.'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 1, 11, false, 'analysiert [die Gefühle des Autors nach seiner Rückkehr nach Deutschland](eh:1), zum Beispiel:
- orgulloso de sí mismo: gracias a su vida fuera de España y a sus numerosos viajes, es la persona que es hoy
- triste: cada vez le cuesta más hacer la maleta e irse; preferiría viajar sólo para aprender y descubrir cosas nuevas
- añoranza/nostalgia: le dificulta la vuelta al extranjero; echa de menos a su familia, sus amigos, la vida en Santiago e incluso la lluvia
- se siente mal entendido por quienes hablan de la movilidad exterior sin haberla vivido'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 0, 7, false, 'kommentiert und diskutiert [Gründe für das Verlassen der Heimat](eh:1), zum Beispiel:
- en el extranjero hay trabajo
- ganar dinero para poder vivir dignamente es existencial
- no hay perspectivas
- la situación actual'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 1, 7, false, 'kommentiert und diskutiert [Gründe für den Verbleib in der Heimat](eh:1), zum Beispiel:
- la familia y los amigos son más importantes que el dinero
- es un riesgo dejar atrás su vida sin saber qué pasará en el extranjero
- no hay garantías de que la vida en el exterior sea mejor'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 2, 5, false, 'formuliert eine [persönliche Stellungnahme](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 3, 2, true, 'erfüllt ein [weiteres aufgabenbezogenes Kriterium](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, 6, false, 'richtet seinen Text konsequent und explizit im Sinne der Aufgabenstellung auf [Intention und Adressaten](eh:1) aus.'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, 5, false, 'beachtet die [Textsortenmerkmale](eh:1) der jeweils geforderten Zieltextformate.'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, 5, false, 'erstellt einen [sachgerecht strukturierten Text](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, 5, false, 'gestaltet seinen Text hinreichend ausführlich, aber ohne [unnötige Wiederholungen und Umständlichkeiten](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, 3, false, 'belegt seine Aussagen durch [funktionale Verwendung von Verweisen und Zitaten](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 5, false, 'löst sich vom Wortlaut des Ausgangstextes und [formuliert eigenständig](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, 6, false, 'verwendet einen sachlich wie stilistisch angemessenen und differenzierten [allgemeinen und thematischen Wortschatz](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, 5, false, 'verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten [Funktions- und Interpretationswortschatz](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, 8, false, 'verwendet einen variablen und dem jeweiligen Zieltextformat angemessenen [Satzbau](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, 10, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Wortschatz](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, 10, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Grammatik](eh:1).'),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, 4, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Orthographie](eh:1) (Rechtschreibung und Zeichensetzung).'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 0, 1, false, '[Aufgabe 1a](eh:1): wählt c als Originaltitel.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 1, 2, false, '[Aufgabe 1b](eh:1): formuliert eine individuelle, inhaltlich schlüssige Schülerlösung mit logischem Bezug zum Titel.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 2, 4, false, '[Aufgabe 2](eh:1): nennt 30, 35, Madrid und Barcelona.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 3, 1, false, '[Aufgabe 3a](eh:1): nennt inspiración.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 4, 1, false, '[Aufgabe 3b](eh:1): nennt de manera objetiva.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 5, 2, false, '[Aufgabe 4](eh:1): nennt 30 años und en 2009.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 6, 4, false, '[Aufgabe 5](eh:1): nennt, dass es ihrem damaligen Freund egal war und dass sie bereits deutsche Freunde hatte.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 7, 4, false, '[Aufgabe 6](eh:1): nennt reunirse con su novio und estaba en paro.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 8, 3, false, '[Aufgabe 7](eh:1): ordnet Alberto - México, Ana - Londres und Silvia - Montreal zu.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 9, 5, false, '[Aufgabe 8](eh:1): nennt u. a. Ausstellungen, Teilnahme an wichtigen Festivals, moralische Unterstützung, wenig finanzielle Unterstützung und das Gefühl eines stagnierenden Landes.'),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 10, 3, false, '[Aufgabe 9](eh:1): nennt, dass es keine gut bezahlte Arbeit für Künstler gibt und sie nicht anerkannt werden.')
) demo(subject_name, part_title, category_title, task_title, sort_order, max_points, bonus, description_markdown)
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
join eh_category category
    on category.part_id = part.id
   and category.title = demo.category_title
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
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 0, '1', 'Aspekte für Shakespeares ungebrochene Popularität', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 1, '1', 'Gründe gegen Shakespeares Eignung für den Schulunterricht', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 2, '1', 'Schlussfolgerungen des Autors', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 0, '1', 'Robshaws Gesamtargumentation', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 1, '1', 'Argumentationsgang und Funktion', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 2, '1', 'sprachliche Mittel und Leserlenkung', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 3, '1', 'weiteres aufgabenbezogenes Kriterium', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 0, '1', 'die Position des Autors', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 1, '1', 'unter Rückgriff auf Unterrichtswissen', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 2, '1', 'eine eigene begründete Sichtweise', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 3, '1', 'weiteres aufgabenbezogenes Kriterium', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, '1', 'Intention und Adressaten', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, '1', 'Textsortenmerkmale', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, '1', 'sachgerecht strukturierten Text', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, '1', 'unnötige Wiederholungen und Umständlichkeiten', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, '1', 'funktionale Verwendung von Verweisen und Zitaten', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, '1', 'formuliert eigenständig', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '1', 'allgemeinen und thematischen Wortschatz', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '1', 'Funktions- und Interpretationswortschatz', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, '1', 'Satzbau', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, '1', 'Wortschatz', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, '1', 'Grammatik', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, '1', 'Orthographie', 0),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 0, '1', 'debate statement', 0),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 1, '1', 'historische Bedeutung der Werke Shakespeares in Deutschland', 0),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 2, '1', 'Unterhaltungswert des britischen Autors', 0),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung (Sprachmittlung)', 0, '1', 'Kommunikative Textgestaltung', 0),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel (Sprachmittlung)', 0, '1', 'Ausdrucksvermögen und sprachliche Mittel', 0),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit (Sprachmittlung)', 0, '1', 'Sprachrichtigkeit', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 0, '1', 'Titel, Autor, Textsorte und Erscheinungsort', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 1, '1', 'die relevanten Aspekte', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 2, '1', 'weiteres aufgabenbezogenes Kriterium', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 0, '1', 'das Thema der Analyse', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 1, '1', 'die Gefühle des Autors nach seiner Rückkehr nach Deutschland', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 0, '1', 'Gründe für das Verlassen der Heimat', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 1, '1', 'Gründe für den Verbleib in der Heimat', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 2, '1', 'persönliche Stellungnahme', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 3, '1', 'weiteres aufgabenbezogenes Kriterium', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, '1', 'Intention und Adressaten', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, '1', 'Textsortenmerkmale', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, '1', 'sachgerecht strukturierten Text', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, '1', 'unnötige Wiederholungen und Umständlichkeiten', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, '1', 'funktionale Verwendung von Verweisen und Zitaten', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, '1', 'formuliert eigenständig', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '1', 'allgemeinen und thematischen Wortschatz', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '1', 'Funktions- und Interpretationswortschatz', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, '1', 'Satzbau', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, '1', 'Wortschatz', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, '1', 'Grammatik', 0),
        ('Spanisch', 'Klausurteil A: Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, '1', 'Orthographie', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 0, '1', 'Aufgabe 1a', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 1, '1', 'Aufgabe 1b', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 2, '1', 'Aufgabe 2', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 3, '1', 'Aufgabe 3a', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 4, '1', 'Aufgabe 3b', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 5, '1', 'Aufgabe 4', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 6, '1', 'Aufgabe 5', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 7, '1', 'Aufgabe 6', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 8, '1', 'Aufgabe 7', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 9, '1', 'Aufgabe 8', 0),
        ('Spanisch', 'Klausurteil B: Hörverstehen isoliert', 'Lösung', 'Hörverstehen', 10, '1', 'Aufgabe 9', 0)
) demo(subject_name, part_title, category_title, task_title, requirement_sort_order, criterion_key, label, criterion_sort_order)
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
join eh_category category
    on category.part_id = part.id
   and category.title = demo.category_title
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
