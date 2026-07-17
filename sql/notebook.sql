select *,(select COUNT(*) from note where note.notebook_id=1) as total_note_cnt from notebook where id =1;

# 搜索笔记本下的笔记，同时搜索对应的tag
select * from note as t3 left join
(
    select t1.note_id as note_id,t2.id as tag_id,t2.name as tag_name,t2.color as tag_color,t2.created_at as tag_created_at from note_tag as t1 left join tag as t2 on t1.tag_id=t2.id
) as t4 on t3.id=t4.note_id where t3.notebook_id = {notebook_id};