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
        ('Qualifikationsphase', 150, 'ACTIVE')
) base(name, max_points, lifecycle)
where not exists (
    select 1
    from grading_scale gs
    where gs.name = base.name
);

insert into grading_scale_range (grading_scale_id, grade_points, min_points, max_points)
select gs.id, base.grade_points, base.min_points, base.max_points
from (
    values
        ('Einführungsphase', 15, 95, 100),
        ('Einführungsphase', 14, 90, 94),
        ('Einführungsphase', 13, 85, 89),
        ('Einführungsphase', 12, 80, 84),
        ('Einführungsphase', 11, 75, 79),
        ('Einführungsphase', 10, 70, 74),
        ('Einführungsphase', 9, 65, 69),
        ('Einführungsphase', 8, 60, 64),
        ('Einführungsphase', 7, 55, 59),
        ('Einführungsphase', 6, 50, 54),
        ('Einführungsphase', 5, 45, 49),
        ('Einführungsphase', 4, 40, 44),
        ('Einführungsphase', 3, 34, 39),
        ('Einführungsphase', 2, 27, 33),
        ('Einführungsphase', 1, 20, 26),
        ('Einführungsphase', 0, 0, 19),
        ('Qualifikationsphase', 15, 144, 150),
        ('Qualifikationsphase', 14, 137, 143),
        ('Qualifikationsphase', 13, 130, 136),
        ('Qualifikationsphase', 12, 123, 129),
        ('Qualifikationsphase', 11, 116, 122),
        ('Qualifikationsphase', 10, 109, 115),
        ('Qualifikationsphase', 9, 102, 108),
        ('Qualifikationsphase', 8, 95, 101),
        ('Qualifikationsphase', 7, 88, 94),
        ('Qualifikationsphase', 6, 81, 87),
        ('Qualifikationsphase', 5, 74, 80),
        ('Qualifikationsphase', 4, 67, 73),
        ('Qualifikationsphase', 3, 56, 66),
        ('Qualifikationsphase', 2, 45, 55),
        ('Qualifikationsphase', 1, 34, 44),
        ('Qualifikationsphase', 0, 0, 33)
) base(scale_name, grade_points, min_points, max_points)
join grading_scale gs
    on gs.name = base.scale_name
where not exists (
    select 1
    from grading_scale_range gsr
    where gsr.grading_scale_id = gs.id
      and gsr.grade_points = base.grade_points
);

update course
set grading_scale_id = (select gs.id from grading_scale gs where gs.name = 'Einführungsphase')
where grading_scale_id is null;
