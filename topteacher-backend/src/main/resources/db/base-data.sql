insert into app_setting (setting_key, setting_value)
select base.setting_key, base.setting_value
from (
    values
        ('tt.loe.export.show_watermark', 'true'),
        ('tt.database.backup.target_folder', ''),
        ('tt.database.backup.schedule.enabled', 'false'),
        ('tt.database.backup.schedule.cron', '0 0 2 * * *')
) base(setting_key, setting_value)
where not exists (
    select 1
    from app_setting setting
    where setting.setting_key = base.setting_key
);

update grading_scale
set name = 'Standard'
where name = '100 Punkte'
  and not exists (
      select 1
      from grading_scale gs
      where gs.name = 'Standard'
  );

insert into grading_scale (name, max_points, lifecycle)
select base.name, base.max_points, base.lifecycle
from (
    values
        ('Standard', 100, 'ACTIVE')
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
        (15, 95, 100),
        (14, 90, 94),
        (13, 85, 89),
        (12, 80, 84),
        (11, 75, 79),
        (10, 70, 74),
        (9, 65, 69),
        (8, 60, 64),
        (7, 55, 59),
        (6, 50, 54),
        (5, 45, 49),
        (4, 40, 44),
        (3, 34, 39),
        (2, 27, 33),
        (1, 20, 26),
        (0, 0, 19)
) base(grade_points, min_points, max_points)
join grading_scale gs
    on gs.name = 'Standard'
where not exists (
    select 1
    from grading_scale_range gsr
    where gsr.grading_scale_id = gs.id
      and gsr.grade_points = base.grade_points
);

update course
set grading_scale_id = (select gs.id from grading_scale gs where gs.name = 'Standard')
where grading_scale_id is null;
