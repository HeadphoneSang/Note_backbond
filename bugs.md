# 已有的bug统计

## sql问题：
* 【完成】`NoteTagMapper.selectNotePageWithTagsByUserId`还有其他的和他相关的，都有limit因为链表查询导致的失效bug
    * 解决思路：先用limit分页查出来对应的note，然后在service里吧对应的note的id平成一个查询集合，**用这个作为条件去note_tag表再去查对应的tag对象，这样可以避免多表链表可能导致的索引失效问题或者是优化器驱动表抖动问题，引发的慢sql。**
* [完成]`IPage<Note> result = noteMapper.selectPage(page, new LambdaQueryWrapper<Note>().eq(Note::getUserId, userId).orderByAsc(Note::getSortOrder).orderByDesc(Note::getUpdatedAt));`的排序索引失效。

## SQL查询优化：
- 可以把下面这个sql的结果通过mapper映射为一个DTO对象【已完成】：
```SQL
EXPLAIN SELECT n.`note_id`,t.`id` AS id,t.`user_id` AS user_id, t.`name` AS NAME,t.`color` AS color, n.`updated_at` AS created_at  FROM note_tag AS n 
	LEFT JOIN tag AS t ON n.`tag_id` = t.`id` 
	WHERE n.`note_id` IN (200,201,209) ORDER BY n.`note_id` ASC, n.`updated_at` ASC;
- ```