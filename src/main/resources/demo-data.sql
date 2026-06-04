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

insert into course (school_class, subject, calendar_year, course_period, lifecycle)
select demo.school_class, demo.subject, demo.calendar_year, demo.course_period, demo.lifecycle
from (
    values
        ('CLS_5A', 'ENGLISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_5A', 'SPANISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_6A', 'ENGLISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_EF', 'ENGLISH', 2026, 'FULL_YEAR', 'ACTIVE'),
        ('CLS_Q1', 'SPANISH', 2025, 'FULL_YEAR', 'INACTIVE')
) demo(school_class, subject, calendar_year, course_period, lifecycle)
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
