/* MCP credentials are created interactively; no base or demo token grants access. */
/* File-access settings inherit base-data defaults and preserve an existing installation location. */
/* Spanish demo: adapted from the supplied 2026 exam; task 3.1 only, 160 regular points. */
/* New demo exams start at revision 0; retry receipts are generated only by actual authoring requests. */
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
        ('CLS_Q2', 'Englisch', 2026, 'FULL_YEAR', 'ACTIVE', 'Qualifikationsphase ab ''25', 150),
        ('CLS_Q1', 'Spanisch', 2026, 'FULL_YEAR', 'ACTIVE', 'Qualifikationsphase ab ''25', 160)
) demo(school_class, subject_name, calendar_year, course_period, lifecycle, grading_scale_name, grading_scale_max_points)
join subject
    on subject.name = demo.subject_name
join grading_scale gs
    on gs.name = demo.grading_scale_name
   and gs.max_points = demo.grading_scale_max_points
where not exists (
    select 1
    from course c
    where c.school_class = demo.school_class
      and c.subject_id = subject.id
      and c.calendar_year = demo.calendar_year
      and c.course_period = demo.course_period
);

insert into exam (course_id, title, exam_date, grading_scale_id, footer_markdown)
select c.id, demo.title, demo.exam_date, c.grading_scale_id,
       case when demo.subject_name = 'Spanisch' then '## Zugelassene Hilfsmittel

- Ein- und zweisprachiges Wörterbuch
- Herkunftssprachliches Wörterbuch, wenn Deine Herkunftssprache nicht Deutsch ist
- Wörterbuch zur deutschen Rechtschreibung' else '' end
from (
    values
        ('CLS_Q2', 'Englisch', 2026, 'FULL_YEAR', '1. Klausur Shakespeare', date '2026-11-17'),
        ('CLS_Q1', 'Spanisch', 2026, 'FULL_YEAR', 'Abiturprüfung 2026 Spanisch (Demo)', date '2026-12-08')
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
        ('Spanisch', 'Abiturprüfung 2026 Spanisch (Demo)', 0, 'Klausurteil A: Schreiben/Leseverstehen integriert'),
        ('Spanisch', 'Abiturprüfung 2026 Spanisch (Demo)', 1, 'Klausurteil B: Sprachmittlung')
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

insert into loe_material (part_id, title, description_markdown, sort_order)
select part.id, demo.title, demo.description_markdown, demo.sort_order
from (
    values
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 0, 'Ausgangstext', 'William Shakespeare prägt Literatur und Sprache bis heute. Dieser Demo-Text dient als **Material** für die Aufgaben.'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 0, 'Deutscher Ausgangstext', 'Ein kurzer deutscher Demo-Quellentext zur Bedeutung Shakespeares.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 0, 'De la calle al refugio azulgrana', '> [!NOTE]
> Junge Einwanderer gelten in Spanien bis zum 18. Lebensjahr als besonders schutzbedürftig und haben Anspruch auf Schulbildung und medizinische Versorgung. Mit Erreichen der Volljährigkeit verlieren sie diesen Schutz.
>
> Die Stiftung des spanischen Fußballklubs FC Barcelona, Fundación del Barça, fördert Projekte zur Unterstützung von Jugendlichen in schwierigen Lebensumständen und Bildungsprogramme.

Irene Guevara

## De la calle al refugio azulgrana[^azulgrana]

> [!NUMBERED]
> Eric Ebra y Jawad Ezzahidi tuvieron que vivir en la calle. Llegaron a Cataluña como
> menores no acompañados[^a1]. Al cumplir los 18 años, perdieron toda la protección. Pero la
> Fundación del Barça les cambió la vida. Entraron a formar parte de Joves Futur+[^a2], programa
> de acompañamiento a la emancipación de jóvenes extutelados[^a3] de entre 18 y 25 años para
> ayudarles a vivir de manera autónoma[^a4] […]. Eric y Jawad consiguieron formarse, encontrar
> trabajo y mejorar sus habilidades sociales y emocionales. “Muchos no tienen estudios, o los
> han acabado, pero no tienen trabajo ni una red social o familiar de apoyo”, explica Marta
> Segú, directora de la fundación. “El impacto es espectacular. Cambias la vida de una
> persona”, añade la directora.
> Esta es la tercera edición[^a5] del proyecto y en las dos primeras se ha logrado prácticamente un
> 100% de éxito de inserción laboral[^a6]. Son seis personas en el equipo de trabajo, más de 80
> empresas colaboradoras y alrededor de 100 jóvenes anualmente —la mayoría chicos
> migrados— residentes en Cataluña, sobre todo en Barcelona. En verano entrevistan a los
> jóvenes que desean participar voluntariamente, normalmente derivados de otras entidades[^a7].
> Una vez seleccionados, diseñan un programa a medida en función de[^a8] sus necesidades y
> aspiraciones[^a9] para poner en marcha los cuatro ejes[^a10] del proyecto: formación, inserción
> laboral, acompañamiento psicosocial y mentoría[^a11].
> Es el caso de Eric y de Jawad. Eric, de 23 años, llegó desde Costa de Marfil[^a12] en 2018 en
> patera, cuando aún era menor de edad. Cuando creció, quedó desamparado[^a13]. A través de una
> entidad social conoció la fundación, donde se formó en logística durante cuatro meses y,
> tras uno de prácticas, consiguió trabajo en una empresa de distribución[^a14]. “Soy muy feliz
> aquí”, explica el joven. Lo compagina[^a15] con ser monitor en un club de fútbol, y su vida la
> hace en[^a16] Barcelona. “Me gusta estar ocupado”, añade entre risas. “La Fundación del Barça
> tiene que seguir ayudando a los jóvenes a encontrar una vida. Nosotros solos no podemos”,
> comparte Eric[^a17]. “Gracias a la fundación, puedo decir que ahora soy más responsable”,
> añade.
> También Jawad es uno de los ejemplos de la fundación. Con tan solo 14 años, tras trabajar
> recogiendo fruta en Marrakech[^a18], inició una larga travesía para llegar a España en busca de
> una vida mejor. […]
> Desde la fundación le ayudan y enseñan a gestionar un salario[^a19], afrontar[^a20] días de trabajo con
> situaciones complicadas o con el idioma. “Me ayudaron a hacer un currículum, una
> entrevista de trabajo, a cómo buscarme la vida[^a21] solo”, explica Jawad. También a gestionar
> sus necesidades[^a22] emocionales o sociales. “Sientes miedo del futuro, no sabes qué pasará. Y
> tú quieres trabajar, no perder el tiempo, y te entra presión[^a23]. Ves que estás solo, aunque
> tengas gente a tu lado. Antes lo quería todo a la vez, ahora sé que hay que ir poco a poco”,
> comparte el joven, que se siente “más cómodo y relajado”.
> El futuro del proyecto es seguir perfeccionando el modelo, aún muy joven, y que otras
> entidades puedan replicarlo[^a24]. Son conscientes, también, de sus limitaciones. “Es difícil
> seguir escalando[^a25] con un proyecto tan artesanal[^a26]. […] No queremos sustituir la calidad por
> cantidad”, destaca Segú. […] Pero todos son conscientes del valor de proyectos sociales
> que cambian la vida de jóvenes. “Les das una dignidad que no tenían. Solo necesitan una
> oportunidad” […].

**Materialgrundlage:** Irene Guevara: „De la calle al refugio azulgrana“, *El País*, 25.12.2024. Wortzahl der Vorlage: 547.

Quelle: https://elpais.com/deportes/futbol/2024-12-25/de-la-calle-al-refugio-azulgrana.html (Zugriff: 12.03.2025).

[^azulgrana]: forma de llamar al FC Barcelona (por sus colores azul y rojo oscuro)

[^a1]: niños y jóvenes que llegaron a España sin familia

[^a2]: katalanischer Name eines Hilfsprogramms

[^a3]: jóvenes que pierden la protección estatal al cumplir 18 años

[^a4]: vivir independientemente

[^a5]: hier: Durchgang

[^a6]: Eingliederung in den Arbeitsmarkt

[^a7]: vermittelt durch andere Einrichtungen

[^a8]: ein auf ihre Bedürfnisse zugeschnittenes Programm entwerfen

[^a9]: aquí: deseos

[^a10]: hier: Grundpfeiler

[^a11]: Betreuung, Begleitung

[^a12]: país en África occidental

[^a13]: hier: obdachlos

[^a14]: Logistikunternehmen

[^a15]: aquí: combina

[^a16]: sein Leben spielt sich ab in

[^a17]: aquí: dice Eric

[^a18]: ciudad en Marruecos

[^a19]: hier: Geld verwalten

[^a20]: hier: bewältigen

[^a21]: seinen Lebensunterhalt bestreiten

[^a22]: seine Bedürfnisse steuern

[^a23]: du fühlst dich unter Druck gesetzt

[^a24]: hier: nachahmen

[^a25]: aquí: creciendo

[^a26]: hier: auf persönliche Bedürfnisse zugeschnitten'),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 0, 'Deutsche Kita-Krise: Spanische Erzieherinnen als Antwort', 'Oliver Pieper

## Deutsche Kita-Krise: Spanische Erzieherinnen als Antwort

> [!NUMBERED]
> An einem trüben, wolkenverhangenen Novembermorgen versuchen zwei Dutzend Kinder-
> gartenkinder in Köln lautstark, die Sonne herbeizusingen. „Sal, solecito, caliéntame un
> poquito“, „Sonne, komm’ heraus und wärme mich ein bisschen“ heißt das Lied, und das
> Besondere dabei: Nur ganz wenige der Zwei- bis Sechsjährigen können Spanisch sprechen.
> Wenn man die Kinder allerdings singen hört, denkt man, man hätte sich in eine Kindertages-
> stätte in Madrid verirrt.
> Die gebürtige Bolivianerin Jessica Rojas Flores ist vor zwei Jahren aus Spanien nach
> Deutschland gekommen, für die Erzieherin ist das bilinguale Konzept der Kindertagesstätte
> Tausendfüßler ein Erfolgsmodell. Sie sagt der DW[^dw]: „Wir sprechen nicht die ganze Zeit
> Spanisch mit den Kindern, singen aber spanische Kinderlieder und wiederholen einige
> Wörter wie Stuhl, Tisch oder Teller immer wieder. So lernen sie die Sprache spielerisch.
> Die Kinder sind auch sehr empathisch mit mir und reden langsamer, wenn ich etwas nicht
> gleich begreife.“
> Neben der Rutsche im Garten prangt auf einem Holzschild praktischerweise die spanische
> Übersetzung und auf vielen Plakaten steht die spanische Vokabel gleich mit dabei. Eltern
> und Kinder werden mit „Buenos días“ und „Adiós“ begrüßt und verabschiedet. Es ist eine
> Win-win-Geschichte: Während die Kinder spanische Vokabeln aufschnappen, lernen die
> gerade angekommenen Erzieherinnen auch Deutsch durch den Kita-Alltag. Die Spanierin
> Carmen Casares Naranjo sagt der DW: „Neulich haben mir Eltern ganz stolz erzählt, dass
> ihr Kind schon auf Spanisch zählen kann und alle Farben auf Spanisch kennt. Was das
> bilinguale Konzept so besonders macht: Die Kinder werden auf das Leben und den Alltag
> vorbereitet, weil sie Strategien entwickeln müssen für Situationen, in denen die andere
> Person sie vielleicht nicht versteht.“
> Cynthia Malca-Buchholz hat das bilinguale Konzept 2013 angestoßen […]. Die gebürtige
> Peruanerin und stellvertretende Leiterin der Tausendfüßler muss aber heute noch die Furcht
> mancher Eltern zerstreuen, dass die Kinder durch die zusätzliche Sprache vielleicht über-
> fordert seien. Im Gegenteil, erwidert sie dann, die Mehrsprachigkeit öffne ihnen weitere
> Türen. Das Erfolgsmodell spricht sich herum, vor kurzem ist wieder eine Bewerbung aus
> Spanien auf ihrem Schreibtisch gelandet.
> Doch nimmt Deutschland damit nicht Ländern die Fachkräfte weg, die diese selbst dringend
> benötigen? Malca-Buchholz sagt der DW: „Nein, weil viele ausgebildete Erzieherinnen in
> Spanien oder Lateinamerika oft keine Arbeit finden und als Kellnerinnen arbeiten müssen.
> In Deutschland können wir ihnen diese Chance bieten, das zu machen, was sie vier oder
> fünf Jahre lang studiert haben.“
> Das Tausendfüßler-Modell, auch auf Fachkräfte aus dem Ausland zu setzen, könnte eine
> kreative Lösung für das Problem sein, das in Deutschland den wenig schmeichelhaften
> Namen „Kitastrophe“ (eine Wortkreuzung aus „Kita“ und „Katastrophe“) trägt. 430.000
> Kita-Plätze fehlen in Deutschland, zudem laut einer Umfrage des Paritätischen Gesamt-
> verbandes[^paritaet] 125.000 Erzieher und Erzieherinnen, was zwei pädagogischen Fachkräften pro
> Kindertagesstätte entspricht.
> […]

**Materialgrundlage:** Oliver Pieper: „Deutsche Kita-Krise: Spanische Erzieherinnen als Antwort“, *Deutsche Welle*, 14.11.2024. Wortzahl der Vorlage: 436.

Quelle: https://www.dw.com/de/deutsche-kita-krise-spanische-erzieherinnen-als-antwort/a-70756556 (Zugriff: 04.03.2025).

[^dw]: Deutsche Welle, Auslandssender der Bundesrepublik Deutschland.

[^paritaet]: Deutscher Paritätischer Wohlfahrtsverband - Gesamtverband e. V., Dachverband deutscher Wohlfahrtseinrichtungen mit Sitz in Berlin.
')
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
    from loe_material material
    where material.part_id = part.id
      and material.title = demo.title
);

insert into eh_category (part_id, title, description_markdown, sort_order, print_tasks_on_exam_sheet)
select part.id, demo.title, demo.description_markdown, demo.sort_order, demo.print_tasks_on_exam_sheet
from (
    values
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 0, 'Inhaltliche Leistung', '', true),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 1, 'Sprachliche Leistung / Darstellungsleistung', 'Bewertung orientiert an den Referenzniveaus des Gemeinsamen europäischen Referenzrahmens (GeR).', false),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 0, 'Inhaltliche Leistung', '', true),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 1, 'Sprachliche Leistung / Darstellungsleistung', 'Bewertung orientiert an den Referenzniveaus des Gemeinsamen europäischen Referenzrahmens (GeR).', false),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 0, 'Inhaltliche Leistung', '', true),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 1, 'Sprachliche Leistung / Darstellungsleistung', 'Bewertung orientiert am Referenzniveau des Gemeinsamen europäischen Referenzrahmens (GeR), das im jeweiligen Lehrplan ausgewiesen ist.', false),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 0, 'Inhaltliche Leistung', '', true),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 1, 'Sprachliche Leistung / Darstellungsleistung', 'Bewertung orientiert am Referenzniveau des Gemeinsamen europäischen Referenzrahmens (GeR), das im jeweiligen Lehrplan ausgewiesen ist.', false)
) demo(subject_name, part_title, sort_order, title, description_markdown, print_tasks_on_exam_sheet)
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

insert into eh_task (category_id, title, sort_order, description_markdown, max_points)
select category.id, demo.title, demo.sort_order, demo.description_markdown, demo.max_points
from (
    values
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 0, 'Teilaufgabe 1 (Comprehension)', 'Bearbeite **Teilaufgabe 1 (Comprehension)**.', 12),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 1, 'Teilaufgabe 2 (Analysis)', 'Bearbeite **Teilaufgabe 2 (Analysis)**.', 16),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 2, 'Teilaufgabe 3 (Comment)', 'Bearbeite **Teilaufgabe 3 (Comment)**.', 14),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung', 'Bearbeite **Kommunikative Textgestaltung**.', 21),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 'Bearbeite **Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel**.', 21),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit', 'Bearbeite **Sprachrichtigkeit**.', 21),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 0, 'Debate statement', 'Bearbeite **Debate statement**.', 18),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung (Sprachmittlung)', 'Bearbeite **Kommunikative Textgestaltung (Sprachmittlung)**.', 9),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel (Sprachmittlung)', 'Bearbeite **Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel (Sprachmittlung)**.', 9),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit (Sprachmittlung)', 'Bearbeite **Sprachrichtigkeit (Sprachmittlung)**.', 9),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 0, 'Aufgabe 1: Resumen', 'Resume lo que llegas a saber sobre el proyecto **Joves Futur+**.', 14),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 1, 'Aufgabe 2: Análisis', 'Examina el impacto que tiene el proyecto en la vida de Eric Ebra y Jawad Ezzahidi.', 12),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 2, 'Aufgabe 3: Comentario', 'Discute, a partir del texto, las posibilidades y limitaciones de proyectos como el de la Fundación del Barça para mejorar la convivencia en una sociedad multicultural como España.', 18),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung', '', 22),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', '', 22),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit', '', 22),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Inhaltliche Leistung', 0, 'Aufgabe 1: Sprachmittlung', '**Situación:**

La hermana mayor de tu amiga Paula, de Madrid, es educadora. Está pensando en venirse a Alemania para trabajar. Has leído el siguiente artículo en el portal Deutsche Welle y le escribes a Paula porque crees que puede ser interesante para su hermana.

**Tarea:**

Redacta un correo electrónico a Paula y presenta el concepto de la guardería infantil “Tausendfüßler” y sus ventajas, según el artículo.', 20),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 0, 'Kommunikative Textgestaltung', '', 10),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 1, 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', '', 10),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 2, 'Sprachrichtigkeit', '', 10)
) demo(subject_name, part_title, category_title, sort_order, title, description_markdown, max_points)
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
select task.id,
       replace(demo.description_markdown, '(crit:1)',
               '(crit:1?points=' || cast(demo.max_points as varchar) || ')'),
       demo.max_points,
       demo.bonus,
       demo.sort_order
from (
    values
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 0, 4, false, 'nennt [Aspekte für Shakespeares ungebrochene Popularität](crit:1?points=0,5), etwa:
- Rang eines Nationalsymbols
- [Sprachkunst](crit:2?points=3,5)
- Fähigkeit zur Psychologisierung
- tiefgehende Charakterdarstellungen
- durch Shakespeare geprägte Redewendungen'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 1, 4, false, 'nennt [Gründe gegen Shakespeares Eignung für den Schulunterricht](crit:1), etwa:
- Grammatik und Wortschatz gehören einer anderen Sprachstufe an
- literarisches Vorwissen fehlt bei der heutigen Schülerschaft
- die erschwerte Zugänglichkeit verhindert, Werte des Literaturunterrichts sinnvoll zu vermitteln; zeitgenössische Texte können diese Aufgabe übernehmen'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 2, 4, false, 'nennt [Schlussfolgerungen des Autors](crit:1), etwa:
- Shakespeares Werke sollten zur Wahrung seines Status nicht als Pflichtlektüre im Curriculum festgelegt werden
- Schülerinnen und Schüler könnten auf Zwang mit Ablehnung reagieren
- die Auseinandersetzung mit seinen Werken soll mit Freude verbunden sein'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 0, 4, false, 'untersucht [Robshaws Gesamtargumentation](crit:1), indem herausgearbeitet wird, dass der Autor Shakespeares Qualitäten hervorhebt und zugleich deutlich macht, dass ein fester Platz im Curriculum heute nicht mehr gerechtfertigt ist.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 1, 6, false, 'analysiert [Argumentationsgang und Funktion](crit:1) des Textes, indem erläutert wird, dass der Autor zum Beispiel:
- eine provokative Überschrift wählt, die bereits die Hauptthese enthält
- die Thematik mit der These einleitet: “isn’t it time we dropped him ...”
- Shakespeare als nationales Denkmal darstellt und dafür Beispiele anführt
- Argumente zur erschwerten Zugänglichkeit für Schülerinnen und Schüler entfaltet
- seine Argumente mit Beispielen stützt
- eine Schlussfolgerung mit Appell zieht'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 2, 6, false, 'analysiert [sprachliche Mittel und Leserlenkung](crit:1), zum Beispiel:
- rhetorische Fragen wie “isn’t it time we dropped him from the National Curriculum?” und “Surely this is a tribute our national writer deserves?”
- Aufzählungen wie “Along with a flag, an anthem and a football team ...” oder “aesthetic pleasure, understanding of character, moral sensitivity”
- Metaphern wie “The Bard is a national monument”
- Vergleiche wie “it’s like handing pupils a treasure in a locked chest”
- Personalpronomen wie “we” zur Herstellung von Gemeinsamkeit
- positiv und negativ konnotierte Wörter sowie unterschiedliche Sprachebenen'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 2 (Analysis)', 3, 4, true, 'erfüllt ein [weiteres aufgabenbezogenes Kriterium](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 0, 6, false, 'greift [die Position des Autors](crit:1) auf, indem zum Beispiel:
- die Aktualität des Themas problematisiert wird
- eigene Erfahrungen kritisch reflektiert werden
- der Schulkontext berücksichtigt wird'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 1, 6, false, 'positioniert sich [unter Rückgriff auf Unterrichtswissen](crit:1), indem zum Beispiel:
- auf Shakespeares Sprache verwiesen wird
- Schwierigkeiten beim Erschließen stilistischer Mittel benannt werden
- die zusätzliche Problematik für Fremdsprachenlernende erläutert wird
- der Gewinn durch die Beschäftigung mit Charakteren, Themen und Konflikten aufgezeigt wird'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 2, 2, false, 'bringt auf Grundlage der Argumentation [eine eigene begründete Sichtweise](crit:1) zum Ausdruck, indem die Argumente pointiert zusammengefasst werden.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 3 (Comment)', 3, 2, true, 'erfüllt ein [weiteres aufgabenbezogenes Kriterium](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, 6, false, 'richtet seinen Text konsequent und explizit im Sinne der Aufgabenstellung auf [Intention und Adressaten](crit:1) aus.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, 4, false, 'beachtet die [Textsortenmerkmale](crit:1) der jeweils geforderten Zieltextformate.'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, 4, false, 'erstellt einen [sachgerecht strukturierten Text](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, 4, false, 'gestaltet seinen Text hinreichend ausführlich, aber ohne [unnötige Wiederholungen und Umständlichkeiten](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, 3, false, 'belegt seine Aussagen durch eine [funktionale Verwendung von Verweisen und Zitaten](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 4, false, 'löst sich vom Wortlaut des Ausgangstextes und [formuliert eigenständig](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, 6, false, 'verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten [allgemeinen und thematischen Wortschatz](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, 4, false, 'verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten [Funktions- und Interpretationswortschatz](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, 7, false, 'verwendet einen variablen und dem jeweiligen Zieltextformat angemessenen [Satzbau](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, 9, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Wortschatz](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, 8, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Grammatik](crit:1).'),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, 4, false, 'beachtet die Normen der sprachlichen Korrektheit im Bereich [Orthographie](crit:1) (Rechtschreibung und Zeichensetzung).'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 0, 6, false, 'verfasst ein adressaten- und themenbezogenes [debate statement](crit:1), in dem auf Thematik und Motive von Shakespeares Werken eingegangen wird:
- aufgrund ihrer Zeitlosigkeit passend für jede Kultur, Nation und Epoche
- für jede Generation faszinierend'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 1, 6, false, 'verweist auf die [historische Bedeutung der Werke Shakespeares in Deutschland](crit:1):
- Grundstein der deutschen Theatertradition
- Bedeutung in der Zeit der Wiedervereinigung'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Inhaltliche Leistung', 'Debate statement', 2, 6, false, 'nimmt Bezug auf den [Unterhaltungswert des britischen Autors](crit:1):
- Eignung für Massenunterhaltung
- Vorlage für Hollywoodverfilmungen'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung (Sprachmittlung)', 0, 9, false, '[Kommunikative Textgestaltung](crit:1):
- richtet den Text konsequent und explizit auf Intention und Adressaten aus
- berücksichtigt den situativen Kontext
- beachtet Textsortenmerkmale des Zieltextformats
- erstellt einen sachgerecht strukturierten Text
- gestaltet hinreichend ausführlich, aber ohne unnötige Wiederholungen'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel (Sprachmittlung)', 0, 9, false, '[Ausdrucksvermögen und sprachliche Mittel](crit:1):
- löst sich vom Wortlaut des Ausgangstextes und formuliert eigenständig
- nutzt ggf. Kompensationsstrategien
- verwendet angemessenen und differenzierten allgemeinen, thematischen und funktionalen Wortschatz
- verwendet variablen und zieltextangemessenen Satzbau'),
        ('Englisch', 'Klausurteil B: Sprachmittlung D-E (isoliert)', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit (Sprachmittlung)', 0, 9, false, '[Sprachrichtigkeit](crit:1): beachtet die Normen sprachlicher Korrektheit in den Bereichen Wortschatz, Grammatik und Orthographie (Rechtschreibung und Zeichensetzung).'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 0, 4, false, '[nennt die bibliografischen Angaben und als Thematik die Vorstellung des erfolgreichen Integrationsprogramms Joves Futur+ für benachteiligte Jugendliche.](crit:1?points=4)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 1, 5, false, '[macht Angaben zu den Rahmenbedingungen des Projekts:](crit:1?points=5)
- organización ejecutora: Fundación del Barça,
- oferta para jóvenes que viven en Cataluña, especialmente en Barcelona,
- acompañamiento de jóvenes entre 18 y 25 años, sobre todo inmigrantes sin familia,
- tercera edición del proyecto, participación de unos 100 jóvenes al año,
- cooperación con empresas y otras fundaciones,
- alto porcentaje de éxito.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 2, 5, false, '[fasst den Ablauf und die Zielsetzung des Projektes zusammen:](crit:1?points=5)
- participación voluntaria, entrevistas de selección,
- adaptación a las necesidades individuales, concepto personalizado,
- apoyo psicosocial,
- ayuda concreta a la formación y la búsqueda de empleo,
- integración en el mercado laboral,
- meta: autonomía de los participantes.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 0, 3, false, '[erarbeitet die schwierigen Lebensbedingungen für Eric und Jawad vor der Teilnahme am Projekt Joves Futur+, hinsichtlich z. B.:](crit:1?points=3)
- vida sin familiares en un país extranjero, pérdida del estatuto de protección,
- sobrecarga con la vida cotidiana, inseguridad y miedo.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 1, 4, false, '[erarbeitet die positive Wirkung des Projekts auf die berufliche Entwicklung der beiden jungen Männer, hinsichtlich z. B.:](crit:1?points=4)
- ayuda para formación y búsqueda de empleo con éxito,
- creación de perspectivas,
- vinculación entre fútbol y educación en el caso de Eric.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 2, 5, false, '[untersucht den positiven Effekt des Projekts auf die persönliche Entwicklung der beiden, hinsichtlich z. B.:](crit:1?points=5)
- sentido de la responsabilidad, voluntad de rendimiento y de trabajo,
- independencia, confianza en las propias fuerzas,
- mejora de conocimientos lingüísticos,
- satisfacción con la situación actual, serenidad,
- visión reflexiva de las competencias adquiridas.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 3, 2, true, '[erfüllt ein weiteres aufgabenbezogenes Kriterium.](crit:1?points=2)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 0, 4, false, '[erörtert kommentierend ausgehend vom Text die Möglichkeiten von Hilfsorganisationen wie der Fundación del Barça, positive Impulse für ein gelingendes Zusammenleben von Menschen mit unterschiedlichem kulturellem Hintergrund in Spanien zu geben, hinsichtlich z. B.:](crit:1?points=4)
- influencia decisiva en la vida futura de los adolescentes,
- creación de oportunidades,
- apoyo lingüístico para inmigrantes,
- creación de un clima de aprecio,
- gran reputación del patrocinador como puerta de acceso,
- aprovechamiento del potencial de los jóvenes,
- fomento de la cohesión social.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 1, 4, false, '[erörtert kommentierend ausgehend vom Text die Grenzen für Hilfsorganisationen wie der Fundación del Barça, positive Impulse für ein gelingendes Zusammenleben von Menschen mit unterschiedlichem kulturellem Hintergrund in Spanien zu geben, hinsichtlich z. B.:](crit:1?points=4)
- alcance limitado,
- dependencia de la situación en el mercado laboral,
- falta de apoyo político,
- asistencia individual en lugar de cambios estructurales,
- desorientación de muchos jóvenes,
- obstáculos financieros.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 2, 6, false, '[diskutiert ausgehend vom Text die besondere Bedeutung von Projekten wie dem der Fundación del Barça angesichts der Vielschichtigkeit der Herausforderungen des Zusammenlebens in der multikulturellen spanischen Gesellschaft, hinsichtlich z. B.:](crit:1?points=6)
- desventajas sociales y laborales de muchos inmigrantes,
- desconocimiento de la lengua, la cultura y las costumbres españolas,
- existencia de prejuicios y estereotipos,
- falta de perspectivas para muchos jóvenes,
- discriminación en situaciones cotidianas,
- falta de contactos entre autóctonos y recién llegados.'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 3, 4, false, '[formuliert mit Blick auf die Reichweite von Projekten wie dem der Fundación del Barça ein begründetes Fazit zu wirksamen Maßnahmen zur Gestaltung eines gelingenden Zusammenlebens in einer multikulturellen Gesellschaft.](crit:1?points=4)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 4, 2, true, '[erfüllt ein weiteres aufgabenbezogenes Kriterium.](crit:1?points=2)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, 6, false, '[richtet seinen Text konsequent und explizit im Sinne der Aufgabenstellung auf die Intention und den Adressaten aus.](crit:1?points=6)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, 4, false, '[beachtet die Textsortenmerkmale der jeweils geforderten Zieltextformate.](crit:1?points=4)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, 5, false, '[erstellt einen sachgerecht strukturierten Text.](crit:1?points=5)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, 4, false, '[gestaltet seinen Text hinreichend ausführlich, aber ohne unnötige Wiederholungen und Umständlichkeiten.](crit:1?points=4)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, 3, false, '[belegt seine Aussagen durch eine funktionale Verwendung von Verweisen und Zitaten.](crit:1?points=3)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 5, false, '[löst sich vom Wortlaut des Ausgangstextes und formuliert eigenständig.](crit:1?points=5)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, 6, false, '[verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten allgemeinen und thematischen Wortschatz.](crit:1?points=6)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, 4, false, '[verwendet einen funktional angemessenen Wortschatz zur Textproduktion und Textbesprechung.](crit:1?points=4)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, 7, false, '[verwendet einen variablen und dem jeweiligen Zieltextformat angemessenen Satzbau.](crit:1?points=7)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, 9, false, 'beachtet die Normen der sprachlichen Korrektheit im Sinne einer gelingenden Kommunikation.

[Wortschatz](crit:1?points=9)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, 9, false, 'beachtet die Normen der sprachlichen Korrektheit im Sinne einer gelingenden Kommunikation.

[Grammatik](crit:1?points=9)'),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, 4, false, 'beachtet die Normen der sprachlichen Korrektheit im Sinne einer gelingenden Kommunikation.

[Orthografie](crit:1?points=4)'),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Inhaltliche Leistung', 'Aufgabe 1: Sprachmittlung', 0, 20, false, '[gibt die wesentlichen Inhalte im Sinne der Aufgabenstellung sinngemäß zusammenfassend wieder.](crit:1?points=20)

1. macht grundlegende Angaben zur Einrichtung „Tausendfüßler“:
   - guardería en Colonia,
   - educación bilingüe en alemán y español,
   - educadoras de Alemania, España y Latinoamérica.
2. stellt das bilinguale Konzept vor:
   - presentación de palabras en las dos lenguas,
   - aprendizaje del idioma jugando,
   - despedirse, saludarse, contar, cantar en español,
   - intercambio lingüístico entre niños y educadoras.
3. nennt die positiven Effekte des Konzeptes:
   - para las educadoras extranjeras: oportunidad de trabajar en su profesión,
   - para los niños: desarrollo de estrategias para situaciones de contacto bilingües en la vida diaria,
   - mejor oferta de asistencia en la guardería,
   - percibido mayoritariamente como modelo de éxito.
4. konzentriert sich dabei - bezogen auf den situativen Kontext - auf wesentliche Inhalte.'),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, 10, false, '[richtet seinen Text konsequent und explizit auf die Intention und den Adressatenkreis im Sinne der Aufgabenstellung aus.](crit:1?points=10)

- berücksichtigt den situativen Kontext.
- beachtet die Textsortenmerkmale des geforderten Zieltextformats.
- erstellt einen sachgerecht strukturierten Text.
- gestaltet seinen Text hinreichend ausführlich, aber ohne unnötige Wiederholungen und Umständlichkeiten.'),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, 10, false, '[löst sich vom Wortlaut des Ausgangstextes und formuliert eigenständig, ggf. unter Verwendung von Kompensationsstrategien.](crit:1?points=10)

- verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten allgemeinen und thematischen Wortschatz.
- verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten Funktionswortschatz.
- verwendet einen variablen und dem jeweiligen Zieltextformat angemessenen Satzbau.'),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, 10, false, '[beachtet die Normen der sprachlichen Korrektheit im Sinne einer gelingenden Kommunikation.](crit:1?points=10)

- Wortschatz
- Grammatik
- Orthografie')
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

insert into eh_criterion (requirement_id, criterion_key, label, point_units, sort_order, active)
select requirement.id,
       demo.criterion_key,
       demo.label,
       case
           when demo.subject_name = 'Englisch'
               and demo.task_title = 'Teilaufgabe 1 (Comprehension)'
               and demo.requirement_sort_order = 0
               and demo.criterion_key = '1' then 1
           when demo.subject_name = 'Englisch'
               and demo.task_title = 'Teilaufgabe 1 (Comprehension)'
               and demo.requirement_sort_order = 0
               and demo.criterion_key = '2' then 7
           else requirement.max_points * 2
       end,
       demo.criterion_sort_order,
       true
from (
    values
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 0, '1', 'Aspekte für Shakespeares ungebrochene Popularität', 0),
        ('Englisch', 'Klausurteil A: Schreiben mit Leseverstehen (integriert)', 'Inhaltliche Leistung', 'Teilaufgabe 1 (Comprehension)', 0, '2', 'Sprachkunst', 1),
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
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 0, '1', 'nennt die bibliografischen Angaben und als Thematik die Vorstellung des erfolgreichen Integrationsprogramms Joves Futur+ für benachteiligte Jugendliche.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 1, '1', 'macht Angaben zu den Rahmenbedingungen des Projekts:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 1: Resumen', 2, '1', 'fasst den Ablauf und die Zielsetzung des Projektes zusammen:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 0, '1', 'erarbeitet die schwierigen Lebensbedingungen für Eric und Jawad vor der Teilnahme am Projekt Joves Futur+, hinsichtlich z. B.:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 1, '1', 'erarbeitet die positive Wirkung des Projekts auf die berufliche Entwicklung der beiden jungen Männer, hinsichtlich z. B.:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 2, '1', 'untersucht den positiven Effekt des Projekts auf die persönliche Entwicklung der beiden, hinsichtlich z. B.:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 2: Análisis', 3, '1', 'erfüllt ein weiteres aufgabenbezogenes Kriterium.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 0, '1', 'erörtert kommentierend ausgehend vom Text die Möglichkeiten von Hilfsorganisationen wie der Fundación del Barça, positive Impulse für ein gelingendes Zusammenleben von Menschen mit unterschiedlichem kulturellem Hintergrund in Spanien zu geben, hinsichtlich z. B.:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 1, '1', 'erörtert kommentierend ausgehend vom Text die Grenzen für Hilfsorganisationen wie der Fundación del Barça, positive Impulse für ein gelingendes Zusammenleben von Menschen mit unterschiedlichem kulturellem Hintergrund in Spanien zu geben, hinsichtlich z. B.:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 2, '1', 'diskutiert ausgehend vom Text die besondere Bedeutung von Projekten wie dem der Fundación del Barça angesichts der Vielschichtigkeit der Herausforderungen des Zusammenlebens in der multikulturellen spanischen Gesellschaft, hinsichtlich z. B.:', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 3, '1', 'formuliert mit Blick auf die Reichweite von Projekten wie dem der Fundación del Barça ein begründetes Fazit zu wirksamen Maßnahmen zur Gestaltung eines gelingenden Zusammenlebens in einer multikulturellen Gesellschaft.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Inhaltliche Leistung', 'Aufgabe 3: Comentario', 4, '1', 'erfüllt ein weiteres aufgabenbezogenes Kriterium.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, '1', 'richtet seinen Text konsequent und explizit im Sinne der Aufgabenstellung auf die Intention und den Adressaten aus.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 1, '1', 'beachtet die Textsortenmerkmale der jeweils geforderten Zieltextformate.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 2, '1', 'erstellt einen sachgerecht strukturierten Text.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 3, '1', 'gestaltet seinen Text hinreichend ausführlich, aber ohne unnötige Wiederholungen und Umständlichkeiten.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 4, '1', 'belegt seine Aussagen durch eine funktionale Verwendung von Verweisen und Zitaten.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, '1', 'löst sich vom Wortlaut des Ausgangstextes und formuliert eigenständig.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 1, '1', 'verwendet funktional einen sachlich wie stilistisch angemessenen und differenzierten allgemeinen und thematischen Wortschatz.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 2, '1', 'verwendet einen funktional angemessenen Wortschatz zur Textproduktion und Textbesprechung.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 3, '1', 'verwendet einen variablen und dem jeweiligen Zieltextformat angemessenen Satzbau.', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, '1', 'Wortschatz', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 1, '1', 'Grammatik', 0),
        ('Spanisch', 'Klausurteil A: Schreiben/Leseverstehen integriert', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 2, '1', 'Orthografie', 0),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Inhaltliche Leistung', 'Aufgabe 1: Sprachmittlung', 0, '1', 'gibt die wesentlichen Inhalte im Sinne der Aufgabenstellung sinngemäß zusammenfassend wieder.', 0),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 'Kommunikative Textgestaltung', 0, '1', 'richtet seinen Text konsequent und explizit auf die Intention und den Adressatenkreis im Sinne der Aufgabenstellung aus.', 0),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 'Ausdrucksvermögen / Verfügbarkeit sprachlicher Mittel', 0, '1', 'löst sich vom Wortlaut des Ausgangstextes und formuliert eigenständig, ggf. unter Verwendung von Kompensationsstrategien.', 0),
        ('Spanisch', 'Klausurteil B: Sprachmittlung', 'Sprachliche Leistung / Darstellungsleistung', 'Sprachrichtigkeit', 0, '1', 'beachtet die Normen der sprachlichen Korrektheit im Sinne einer gelingenden Kommunikation.', 0)
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

/* Demo exams do not have MCP creation retry receipts. */
