insert into app_setting (setting_key, setting_value)
select base.setting_key, base.setting_value
from (
    values
        ('tt.loe.export.show_watermark', 'true'),
        ('tt.database.backup.target_folder', ''),
        ('tt.database.backup.schedule.enabled', 'false'),
        ('tt.database.backup.schedule.cron', '0 0 2 * * *'),
        ('tt.database.initialization.completed', 'false')
) base(setting_key, setting_value)
where not exists (
    select 1
    from app_setting setting
    where setting.setting_key = base.setting_key
);

insert into subject (name, lifecycle)
select base.name, base.lifecycle
from (
    values
        ('Deutsch', 'ACTIVE'),
        ('Mathematik', 'ACTIVE'),
        ('Englisch', 'ACTIVE'),
        ('Französisch', 'ACTIVE'),
        ('Latein', 'ACTIVE'),
        ('Spanisch', 'ACTIVE'),
        ('Erdkunde', 'ACTIVE'),
        ('Geschichte', 'ACTIVE'),
        ('Politik', 'ACTIVE'),
        ('Biologie', 'ACTIVE'),
        ('Chemie', 'ACTIVE'),
        ('Physik', 'ACTIVE'),
        ('Informatik', 'ACTIVE'),
        ('Ev. Religionslehre', 'ACTIVE'),
        ('Kath. Religionslehre', 'ACTIVE'),
        ('Ethik', 'ACTIVE'),
        ('Philosophie', 'ACTIVE'),
        ('Kunst', 'ACTIVE'),
        ('Musik', 'ACTIVE'),
        ('Sport', 'ACTIVE')
) base(name, lifecycle)
where not exists (
    select 1
    from subject s
    where s.name = base.name
);

update grading_scale
set name = 'Einführungsphase'
where name = 'Standard'
  and not exists (
      select 1
      from grading_scale gs
      where gs.name = 'Einführungsphase'
  );

update grading_scale
set name = 'Einführungsphase'
where name = '100 Punkte'
  and not exists (
      select 1
      from grading_scale gs
      where gs.name = 'Einführungsphase'
  );

insert into grading_scale (name, max_points, lifecycle)
select base.name, base.max_points, base.lifecycle
from (
    values
        ('Einführungsphase', 100, 'ACTIVE'),
        ('Qualifikationsphase', 150, 'ACTIVE'),
        ('Qualifikationsphase', 160, 'ACTIVE')
) base(name, max_points, lifecycle)
where not exists (
    select 1
    from grading_scale gs
    where gs.name = base.name
      and gs.max_points = base.max_points
);

insert into grading_scale_range (grading_scale_id, grade_points, min_points, max_points)
select gs.id, base.grade_points, base.min_points, base.max_points
from (
    values
        ('Einführungsphase', 100, 15, 95, 100),
        ('Einführungsphase', 100, 14, 90, 94),
        ('Einführungsphase', 100, 13, 85, 89),
        ('Einführungsphase', 100, 12, 80, 84),
        ('Einführungsphase', 100, 11, 75, 79),
        ('Einführungsphase', 100, 10, 70, 74),
        ('Einführungsphase', 100, 9, 65, 69),
        ('Einführungsphase', 100, 8, 60, 64),
        ('Einführungsphase', 100, 7, 55, 59),
        ('Einführungsphase', 100, 6, 50, 54),
        ('Einführungsphase', 100, 5, 45, 49),
        ('Einführungsphase', 100, 4, 40, 44),
        ('Einführungsphase', 100, 3, 34, 39),
        ('Einführungsphase', 100, 2, 27, 33),
        ('Einführungsphase', 100, 1, 20, 26),
        ('Einführungsphase', 100, 0, 0, 19),
        ('Qualifikationsphase', 150, 15, 144, 150),
        ('Qualifikationsphase', 150, 14, 137, 143),
        ('Qualifikationsphase', 150, 13, 130, 136),
        ('Qualifikationsphase', 150, 12, 123, 129),
        ('Qualifikationsphase', 150, 11, 116, 122),
        ('Qualifikationsphase', 150, 10, 109, 115),
        ('Qualifikationsphase', 150, 9, 102, 108),
        ('Qualifikationsphase', 150, 8, 95, 101),
        ('Qualifikationsphase', 150, 7, 88, 94),
        ('Qualifikationsphase', 150, 6, 81, 87),
        ('Qualifikationsphase', 150, 5, 74, 80),
        ('Qualifikationsphase', 150, 4, 67, 73),
        ('Qualifikationsphase', 150, 3, 56, 66),
        ('Qualifikationsphase', 150, 2, 45, 55),
        ('Qualifikationsphase', 150, 1, 34, 44),
        ('Qualifikationsphase', 150, 0, 0, 33),
        ('Qualifikationsphase', 160, 15, 152, 160),
        ('Qualifikationsphase', 160, 14, 144, 151),
        ('Qualifikationsphase', 160, 13, 136, 143),
        ('Qualifikationsphase', 160, 12, 128, 135),
        ('Qualifikationsphase', 160, 11, 120, 127),
        ('Qualifikationsphase', 160, 10, 112, 119),
        ('Qualifikationsphase', 160, 9, 104, 111),
        ('Qualifikationsphase', 160, 8, 96, 103),
        ('Qualifikationsphase', 160, 7, 88, 95),
        ('Qualifikationsphase', 160, 6, 80, 87),
        ('Qualifikationsphase', 160, 5, 72, 79),
        ('Qualifikationsphase', 160, 4, 64, 71),
        ('Qualifikationsphase', 160, 3, 53, 63),
        ('Qualifikationsphase', 160, 2, 44, 52),
        ('Qualifikationsphase', 160, 1, 32, 43),
        ('Qualifikationsphase', 160, 0, 0, 31)
) base(scale_name, scale_max_points, grade_points, min_points, max_points)
join grading_scale gs
    on gs.name = base.scale_name
   and gs.max_points = base.scale_max_points
where not exists (
    select 1
    from grading_scale_range gsr
    where gsr.grading_scale_id = gs.id
      and gsr.grade_points = base.grade_points
);

update course
set grading_scale_id = (select gs.id from grading_scale gs where gs.name = 'Einführungsphase')
where grading_scale_id is null;
