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

delete from grading_scale_range
where grading_scale_id in (
    select old_scale.id
    from grading_scale old_scale
    where old_scale.name = 'Qualifikationsphase'
      and old_scale.max_points in (150, 160)
      and not exists (
          select 1
          from grading_scale new_scale
          where new_scale.name = 'Qualifikationsphase ab ''25'
            and new_scale.max_points = old_scale.max_points
      )
);

update grading_scale
set name = 'Qualifikationsphase ab ''25'
where name = 'Qualifikationsphase'
  and max_points in (150, 160)
  and not exists (
      select 1
      from grading_scale gs
      where gs.name = 'Qualifikationsphase ab ''25'
        and gs.max_points = grading_scale.max_points
  );

insert into grading_scale (name, max_points, lifecycle)
select base.name, base.max_points, base.lifecycle
from (
    values
        ('Einführungsphase', 100, 'ACTIVE'),
        ('Qualifikationsphase ab ''25', 150, 'ACTIVE'),
        ('Qualifikationsphase ab ''25', 160, 'ACTIVE'),
        ('Qualifikationsphase ab ''25', 200, 'ACTIVE')
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
        ('Qualifikationsphase ab ''25', 150, 15, 143, 150),
        ('Qualifikationsphase ab ''25', 150, 14, 135, 142),
        ('Qualifikationsphase ab ''25', 150, 13, 128, 134),
        ('Qualifikationsphase ab ''25', 150, 12, 120, 127),
        ('Qualifikationsphase ab ''25', 150, 11, 113, 119),
        ('Qualifikationsphase ab ''25', 150, 10, 105, 112),
        ('Qualifikationsphase ab ''25', 150, 9, 98, 104),
        ('Qualifikationsphase ab ''25', 150, 8, 90, 97),
        ('Qualifikationsphase ab ''25', 150, 7, 83, 89),
        ('Qualifikationsphase ab ''25', 150, 6, 75, 82),
        ('Qualifikationsphase ab ''25', 150, 5, 68, 74),
        ('Qualifikationsphase ab ''25', 150, 4, 60, 67),
        ('Qualifikationsphase ab ''25', 150, 3, 50, 59),
        ('Qualifikationsphase ab ''25', 150, 2, 41, 49),
        ('Qualifikationsphase ab ''25', 150, 1, 30, 40),
        ('Qualifikationsphase ab ''25', 150, 0, 0, 29),
        ('Qualifikationsphase ab ''25', 160, 15, 152, 160),
        ('Qualifikationsphase ab ''25', 160, 14, 144, 151),
        ('Qualifikationsphase ab ''25', 160, 13, 136, 143),
        ('Qualifikationsphase ab ''25', 160, 12, 128, 135),
        ('Qualifikationsphase ab ''25', 160, 11, 120, 127),
        ('Qualifikationsphase ab ''25', 160, 10, 112, 119),
        ('Qualifikationsphase ab ''25', 160, 9, 104, 111),
        ('Qualifikationsphase ab ''25', 160, 8, 96, 103),
        ('Qualifikationsphase ab ''25', 160, 7, 88, 95),
        ('Qualifikationsphase ab ''25', 160, 6, 80, 87),
        ('Qualifikationsphase ab ''25', 160, 5, 72, 79),
        ('Qualifikationsphase ab ''25', 160, 4, 64, 71),
        ('Qualifikationsphase ab ''25', 160, 3, 53, 63),
        ('Qualifikationsphase ab ''25', 160, 2, 43, 52),
        ('Qualifikationsphase ab ''25', 160, 1, 32, 42),
        ('Qualifikationsphase ab ''25', 160, 0, 0, 31),
        ('Qualifikationsphase ab ''25', 200, 15, 190, 200),
        ('Qualifikationsphase ab ''25', 200, 14, 180, 189),
        ('Qualifikationsphase ab ''25', 200, 13, 170, 179),
        ('Qualifikationsphase ab ''25', 200, 12, 160, 169),
        ('Qualifikationsphase ab ''25', 200, 11, 150, 159),
        ('Qualifikationsphase ab ''25', 200, 10, 140, 149),
        ('Qualifikationsphase ab ''25', 200, 9, 130, 139),
        ('Qualifikationsphase ab ''25', 200, 8, 120, 129),
        ('Qualifikationsphase ab ''25', 200, 7, 110, 119),
        ('Qualifikationsphase ab ''25', 200, 6, 100, 109),
        ('Qualifikationsphase ab ''25', 200, 5, 90, 99),
        ('Qualifikationsphase ab ''25', 200, 4, 80, 89),
        ('Qualifikationsphase ab ''25', 200, 3, 66, 79),
        ('Qualifikationsphase ab ''25', 200, 2, 54, 65),
        ('Qualifikationsphase ab ''25', 200, 1, 40, 53),
        ('Qualifikationsphase ab ''25', 200, 0, 0, 39)
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
