package org.wandaima.web.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.wandaima.core.BaseMapper;
import org.wandaima.web.model.NewsInfo;

@Mapper
public interface NewsInfoMapper extends BaseMapper<NewsInfo> {

	List<NewsInfo> selectNewsInfo(@Param("newsList") List<NewsInfo> newsList);
	
}
