package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.template.model.Notebook;

/**
 * 笔记本数据访问层
 */
@Mapper
public interface NotebookMapper extends BaseMapper<Notebook> {

}
