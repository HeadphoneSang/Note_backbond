package org.template.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.template.model.Tag;

/**
 * 标签数据访问层
 */
@Mapper
public interface TagMapper extends BaseMapper<Tag> {

}
