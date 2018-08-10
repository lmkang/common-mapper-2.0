package org.wandaima.core;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.ibatis.jdbc.SQL;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.wandaima.annotation.Table;
import org.wandaima.model.ConditionDefinition;
import org.wandaima.model.Criteria;

public class BaseSqlProvider {

	private Class<?> beanClass;// JavaBean
	
	private ResultMapping idResult;// ResultMap的<id/>标签
	
	private Map<String, String> baseResultMapping;// 非嵌套的ResultMapping
	
	private Map<String, ResultMapping> fkIdResult;// 嵌套ResultMap的<id/>标签
	
	private Map<String, String> fkResultMapping;// 嵌套的ResultMapping
	
	private Map<String, String> columnPrefixMap;// 嵌套ResultMap的columnPrefix
	
	private Map<String, String> tableNameMap;// 嵌套ResultMap的tableName
	
	private static MappedStatement mappedStmt;
	
	private static Properties properties;// properties配置
	
	public BaseSqlProvider() {
		init();
	}
	
	// 1、T model
	public String insert(Object obj) {
		if(obj == null) {
			throw new RuntimeException("The insert Object can not be null.");
		}
		SQL sql = new SQL();
		Class<?> clazz = obj.getClass();
		Table table = clazz.getAnnotation(Table.class);
		sql.INSERT_INTO(table.name());// INSERT INTO
		// VALUES
		for(Map.Entry<String, String> me : baseResultMapping.entrySet()) {
			String property = me.getKey();
			String column = me.getValue();
			String[] values = property.split("\\.");
			try {
				if(values.length == 1) {// 单表
					Field field = clazz.getDeclaredField(values[0]);
					field.setAccessible(true);
					Object value = field.get(obj);
					if(value != null) {
						sql.VALUES(String.format("`%s`", column), String.format("#{%s}", property));
					}
				} else if(values.length == 2) {// 多表
					Field field = clazz.getDeclaredField(values[0]);
					field.setAccessible(true);
					Object value = field.get(obj);
					if(value != null) {
						Class<?> clazz2 = value.getClass();
						if(!clazz2.equals(List.class) && !clazz2.equals(ArrayList.class)
								&& !clazz2.equals(LinkedList.class)) {// 多对一,不支持多对多
							Field field2 = clazz2.getDeclaredField(values[1]);
							field2.setAccessible(true);
							Object value2 = field2.get(value);
							if(value2 != null) {
								sql.VALUES(String.format("`%s`", column), String.format("#{%s}", property));
							}
						}
					}
				} else {
					throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		debugSql(sql.toString());
		return sql.toString();
	}

	// 1、T model ; 2、Criteria criteria
	public String update(Map<String, Object> paramMap) {
		if(paramMap == null) {
			throw new RuntimeException("The paramMap can not be null.");
		}
		Object model = paramMap.get("model");
		if(model == null) {
			throw new RuntimeException("The model can not be null.");
		}
		Criteria criteria = (Criteria) paramMap.get("criteria");
		SQL sql = new SQL();
		Class<?> clazz = model.getClass();
		Table table = clazz.getAnnotation(Table.class);
		sql.UPDATE(table.name());// UPDATE
		if(criteria != null && criteria.hasJoin()) {// 关联表
			Set<String> fkSet = criteria.getFkSet();
			for(String property : fkSet) {
				String tableName = tableNameMap.get(property);
				if(tableName != null && tableName.trim().length() > 0) {
					StringBuilder builder = new StringBuilder();
					builder.append(String.format("%s ON ", tableName));
					ResultMapping rm = fkIdResult.get(property);
					String fkColumn = baseResultMapping.get(property + "." + rm.getProperty());
					builder.append(String.format("%s.`%s`=%s.`%s`", table.name(), fkColumn, tableName, rm.getColumn()));
					sql.LEFT_OUTER_JOIN(builder.toString());
				}
			}
		}
		// SET
		for(Map.Entry<String, String> me : baseResultMapping.entrySet()) {
			String property = me.getKey();
			String column = me.getValue();
			if(property != null && column != null) {
				try {
					String[] values = property.split("\\.");
					if(values.length == 1) {// 单表
						Field field = clazz.getDeclaredField(values[0]);
						field.setAccessible(true);
						Object value = field.get(model);
						if(value != null) {
							if(criteria != null && criteria.hasJoin()) {// 有关联,加上表名
								column = table.name() + "." + column;
							}
							sql.SET(String.format("%s=#{%s}", column, "model." + property));
						}
					} else if(values.length == 2) {// 多表
						Field field = clazz.getDeclaredField(values[0]);
						field.setAccessible(true);
						Object value = field.get(model);
						if(value != null) {
							Class<?> clazz2 = value.getClass();
							if(!clazz2.equals(List.class) && !clazz2.equals(ArrayList.class)
									&& !clazz2.equals(LinkedList.class)) {// 多对一,不支持多对多
								Field field2 = clazz2.getDeclaredField(values[1]);
								field2.setAccessible(true);
								Object value2 = field2.get(value);
								if(value2 != null) {
									if(criteria != null && criteria.hasJoin()) {// 有关联,加上表名
										column = table.name() + "." + column;
									}
									sql.SET(String.format("%s=#{%s}", column, "model." + property));
								}
							}
						}
					} else {
						throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		// WHERE
		if(criteria == null) {// 默认按照主键更新
			String property = idResult.getProperty();
			String column = idResult.getColumn();
			if(property != null && column != null) {
				sql.WHERE(String.format("`%s`=#{%s}", column, "model." + property));
			}
		} else {// 按照Criteria条件更新
			List<ConditionDefinition> conditionList = criteria.getConditionList();
			if(conditionList.size() > 0) {
				for(int i = 0; i < conditionList.size(); i++) {
					ConditionDefinition condition = conditionList.get(i);
					String fieldName = condition.getFieldName();
					String operator = condition.getOperator();
					Object value = condition.getValue();
					String[] values = fieldName.split("\\.");
					if(values.length == 1) {
						String column = baseResultMapping.get(fieldName);
						if(column == null || column.trim().length() == 0) {// 主键
							column = idResult.getProperty();
						}
						if(criteria.hasJoin()) {
							column = table.name() + "." + column;
						}
						if(value != null) {
							if("IN".equals(operator)) {
								if(value.getClass().isArray()) {
									Object[] objArr = (Object[]) value;
									StringBuilder builder = new StringBuilder();
									builder.append(String.format("%s %s (", column, operator));
									for(int j = 0; j < objArr.length; j++) {
										builder.append(String.format("#{criteria.conditionList[%d].value[%d]},", i, j));
									}
									builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
									builder.append(")");
									sql.WHERE(builder.toString());
								}
							} else {
								sql.WHERE(String.format("%s %s #{criteria.conditionList[%d].value}", column, operator, i));
							}
						} else {// IS NULL 或者 IS NOT NULL
							column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
							sql.WHERE(String.format("%s.%s %s", table.name(), column, operator));
						}
					} else if(values.length == 2) {
						String column = fkResultMapping.get(fieldName);
						String tableName = tableNameMap.get(values[0]);
						if(tableName != null) {
							if(column == null || column.trim().length() == 0) {// 主键
								column = fkIdResult.get(values[0]).getColumn();
							}
							column = tableName + "." + column;
							if(value != null) {
								if("IN".equals(operator)) {
									if(value.getClass().isArray()) {
										if(value.getClass().isArray()) {
											Object[] objArr = (Object[]) value;
											StringBuilder builder = new StringBuilder();
											builder.append(String.format("%s %s (", column, operator));
											for(int j = 0; j < objArr.length; j++) {
												builder.append(String.format("#{criteria.conditionList[%d].value[%d]},", i, j));
											}
											builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
											builder.append(")");
											sql.WHERE(builder.toString());
										}
									}
								} else {
									sql.WHERE(String.format("%s %s #{criteria.conditionList[%d].value}", column, operator, i));
								}
							} else {// IS NULL 或者 IS NOT NULL
								column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
								sql.WHERE(String.format("%s.%s %s", table.name(), column, operator));
							}
						}
					} else {
						throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
					}
				}
			}
		}
		debugSql(sql.toString());
		return sql.toString();
	}
	
	// 1、Criteria criteria
	public String delete(Criteria criteria) {
		if(criteria == null) {
			throw new RuntimeException("The Criteria can not be null.");
		}
		StringBuilder sql = new StringBuilder();
		Table table = beanClass.getAnnotation(Table.class);
		if(criteria.hasJoin()) {// 关联表
			sql.append(String.format("DELETE %s FROM %s ", table.name(), table.name()));
			Set<String> fkSet = criteria.getFkSet();
			for(String property : fkSet) {
				String tableName = tableNameMap.get(property);
				if(tableName != null && tableName.trim().length() > 0) {
					sql.append(String.format("LEFT JOIN %s ON ", tableName));
					ResultMapping rm = fkIdResult.get(property);
					String fkColumn = baseResultMapping.get(property + "." + rm.getProperty());
					sql.append(String.format("%s.`%s`=%s.`%s` ", table.name(), fkColumn, tableName, rm.getColumn()));
				}
			}
		} else {
			sql.append(String.format("DELETE FROM %s ", table.name()));
		}
		// WHERE
		List<ConditionDefinition> conditionList = criteria.getConditionList();
		if(conditionList.size() > 0) {
			sql.append("WHERE ");
			for(int i = 0; i < conditionList.size(); i++) {
				ConditionDefinition condition = conditionList.get(i);
				String fieldName = condition.getFieldName();
				String operator = condition.getOperator();
				Object value = condition.getValue();
				String[] values = fieldName.split("\\.");
				if(values.length == 1) {
					String column = baseResultMapping.get(fieldName);
					if(column == null || column.trim().length() == 0) {// 主键
						column = idResult.getProperty();
					}
					if(criteria.hasJoin()) {// 有关联,加上表名
						column = table.name() + "." + column;
					}
					if(value != null) {
						if("IN".equals(operator)) {
							if(value.getClass().isArray()) {
								Object[] objArr = (Object[]) value;
								StringBuilder builder = new StringBuilder();
								builder.append(String.format("%s %s (", column, operator));
								for(int j = 0; j < objArr.length; j++) {
									builder.append(String.format("#{criteria.conditionList[%d].value[%d]},", i, j));
								}
								builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
								builder.append(") AND ");
								sql.append(builder.toString());
							}
						} else {
							sql.append(String.format("%s %s #{conditionList[%d].value} AND ", column, operator, i));
						}
					} else {// IS NULL 或者 IS NOT NULL
						column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
						sql.append(String.format("%s.%s %s AND ", table.name(), column, operator));
					}
				} else if(values.length == 2) {
					String column = fkResultMapping.get(fieldName);
					String tableName = tableNameMap.get(values[0]);
					if(tableName != null) {
						if(column == null || column.trim().length() == 0) {// 主键
							column = fkIdResult.get(values[0]).getColumn();
						}
						column = tableName + "." + column;
						if(value != null) {
							if("IN".equals(operator)) {
								if(value.getClass().isArray()) {
									Object[] objArr = (Object[]) value;
									StringBuilder builder = new StringBuilder();
									builder.append(String.format("%s %s (", column, operator));
									for(int j = 0; j < objArr.length; j++) {
										builder.append(String.format("#{criteria.conditionList[%d].value[%d]},", i, j));
									}
									builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
									builder.append(") AND ");
									sql.append(builder.toString());
								}
							} else {
								sql.append(String.format("%s %s #{conditionList[%d].value} AND ", tableName, column, i));
							}
						} else {// IS NULL 或者 IS NOT NULL
							column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
							sql.append(String.format("%s.%s %s AND ", table.name(), column, operator));
						}
					}
				} else {
					throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
				}
			}
		}
		sql.delete(sql.length() - 5, sql.length());// 删除末尾的" AND "
		debugSql(sql.toString());
		return sql.toString();
	}
	
	// 1、Criteria criteria ; 2、String... fieldList
	private String select(Map<String, Object> paramMap) {
		if(paramMap == null) {
			throw new RuntimeException("The paramMap can not be null.");
		}
		Criteria criteria = (Criteria) paramMap.get("criteria");
		if(criteria == null) {
			throw new RuntimeException("The Criteria can not be null.");
		}
		String[] fieldList = (String[]) paramMap.get("fieldList");
		// 判断查询字段中是否有关联查询
		if(fieldList != null && fieldList.length > 0) {
			Set<String> fkSet = criteria.getFkSet();
			for(String fieldName : fieldList) {
				if(fieldName != null && fieldName.trim().length() > 0) {
					String[] values = fieldName.split("\\.");
					if(values.length == 2) {
						fkSet.add(values[0]);
					}
				}
			}
		}
		Table table = beanClass.getAnnotation(Table.class);
		SQL sql = new SQL();
		// SELECT
		if(fieldList == null || fieldList.length == 0) {
			Field[] fields = beanClass.getDeclaredFields();
			for(Field field : fields) {
				field.setAccessible(true);
				String fieldName = field.getName();
				String column = baseResultMapping.get(fieldName);
				if(column == null || column.trim().length() == 0) {// 主键
					column = idResult.getColumn();
				}
				if(criteria.hasJoin()) {
					column = String.format("%s.%s", table.name(), column);
				}
				sql.SELECT(column);
			}
		} else {
			for(String fieldName : fieldList) {
				if(fieldName != null && fieldName.trim().length() > 0) {
					String[] values = fieldName.split("\\.");
					if(values.length == 1) {
						String column = baseResultMapping.get(fieldName);
						if(column == null || column.trim().length() == 0) {// 主键
							column = idResult.getColumn();
						}
						if(criteria.hasJoin()) {
							column = String.format("%s.%s", table.name(), column);
						}
						sql.SELECT(column);
					} else if(values.length == 2) {
						String column = fkResultMapping.get(fieldName);
						String tableName = tableNameMap.get(values[0]);
						String columnPrefix = columnPrefixMap.get(values[0]);
						if(tableName != null) {
							if(column == null || column.trim().length() == 0) {// 主键
								column = fkIdResult.get(values[0]).getColumn();
							}
							column = String.format("%s.`%s` AS %s%s", tableName, column, columnPrefix, column);
							sql.SELECT(column);
						}
					} else {
						throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
					}
				}
			}
		}
		// FROM
		sql.FROM(table.name());// FROM
		if(criteria.hasJoin()) {// LEFT OUTER JOIN
			Set<String> fkSet = criteria.getFkSet();
			for(String property : fkSet) {
				String tableName = tableNameMap.get(property);
				if(tableName != null && tableName.trim().length() > 0) {
					StringBuilder builder = new StringBuilder();
					builder.append(String.format("%s ON ", tableName));
					ResultMapping rm = fkIdResult.get(property);
					String fkColumn = baseResultMapping.get(property + "." + rm.getProperty());
					builder.append(String.format("%s.`%s`=%s.`%s`", table.name(), fkColumn, tableName, rm.getColumn()));
					sql.LEFT_OUTER_JOIN(builder.toString());
				}
			}
		}
		// WHERE
		List<ConditionDefinition> conditionList = criteria.getConditionList();
		if(conditionList.size() > 0) {
			for(int i = 0; i < conditionList.size(); i++) {
				ConditionDefinition condition = conditionList.get(i);
				String fieldName = condition.getFieldName();
				String operator = condition.getOperator();
				Object value = condition.getValue();
				String[] values = fieldName.split("\\.");
				if(values.length == 1) {
					String column = baseResultMapping.get(fieldName);
					if(column == null || column.trim().length() == 0) {
						column = idResult.getColumn();
					}
					if(criteria.hasJoin()) {
						column = String.format("%s.%s", table.name(), column);
					}
					if(value != null) {
						if("IN".equals(operator)) {
							if(value.getClass().isArray()) {
								Object[] objArr = (Object[]) value;
								StringBuilder builder = new StringBuilder();
								builder.append(String.format("%s %s (", column, operator));
								for(int j = 0; j < objArr.length; j++) {
									builder.append(String.format("#{criteria.conditionList[%d].value[%d]},", i, j));
								}
								builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
								builder.append(")");
								sql.WHERE(builder.toString());
							}
						} else {
							sql.WHERE(String.format("%s %s #{criteria.conditionList[%d].value}", column, operator, i));
						}
					} else {// IS NULL 或者 IS NOT NULL
						column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
						sql.WHERE(String.format("%s.%s %s", table.name(), column, operator));
					}
				} else if(values.length == 2) {
					String column = fkResultMapping.get(fieldName);
					String tableName = tableNameMap.get(values[0]);
					if(tableName != null) {
						if(column == null || column.trim().length() == 0) {
							column = fkIdResult.get(values[0]).getColumn();
						}
						column = tableName + "." + column;
						if(value != null) {
							if("IN".equals(operator)) {
								if(value.getClass().isArray()) {
									Object[] objArr = (Object[]) value;
									StringBuilder builder = new StringBuilder();
									builder.append(String.format("%s %s (", column, operator));
									for(int j = 0; j < objArr.length; j++) {
										builder.append(String.format("#{criteria.conditionList[%d].value[%d]},", i, j));
									}
									builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
									builder.append(")");
									sql.WHERE(builder.toString());
								}
							} else {
								sql.WHERE(String.format("%s %s #{criteria.conditionList[%d].value}", column, operator, i));
							}
						} else {// IS NULL 或者 IS NOT NULL
							column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
							sql.WHERE(String.format("%s.%s %s", table.name(), column, operator));
						}
					}
				} else {
					throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
				}
			}
		}
		return sql.toString();
	}
	
	// 1、Criteria criteria ; 2、String... fieldList
	private String selectMany(Map<String, Object> paramMap) {
		Criteria criteria = (Criteria) paramMap.get("criteria");
		if(criteria == null) {
			throw new RuntimeException("The Criteria can not be null.");
		}
		String sql = this.select(paramMap);// SELECT ... FROM ... WHERE ...
		StringBuilder builder = new StringBuilder(sql);
		List<ConditionDefinition> orderbyList = criteria.getOrderbyList();
		// ORDER BY ... ...
		if(orderbyList.size() > 0) {
			builder.append(" ORDER BY ");
			Table table = beanClass.getAnnotation(Table.class);
			for(int i = 0; i < orderbyList.size(); i++) {
				ConditionDefinition condition = orderbyList.get(i);
				String fieldName = condition.getFieldName();
				String value = (String) condition.getValue();
				String[] values = fieldName.split("\\.");
				if(values.length == 1) {
					String column = baseResultMapping.get(fieldName);
					if(column == null || column.trim().length() == 0) {// 主键
						column = idResult.getColumn();
					}
					if(criteria.hasJoin()) {
						column = String.format("%s.%s", table.name(), column);
					}
					builder.append(String.format("%s %s,", column, value));
				} else if(values.length == 2) {
					String column = fkResultMapping.get(fieldName);
					String tableName = tableNameMap.get(values[0]);
					if(tableName != null) {
						if(column == null || column.trim().length() == 0) {
							column = fkIdResult.get(values[0]).getColumn();
						}
						column = tableName + "." + column;
						builder.append(String.format("%s %s,", column, value));
					}
				} else {
					throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
				}
			}
			builder.deleteCharAt(builder.length() - 1);// 删掉末尾的","
		}
		return builder.toString();
	}
	
	// 1、Criteria criteria ; 2、String... fieldList
	public String selectOne(Map<String, Object> paramMap) {
		String sql = this.select(paramMap);
		debugSql(sql);
		return sql;
	}
	
	// 1、Criteria criteria ; 2、String... fieldList
	public String selectAll(Map<String, Object> paramMap) {
		String sql = this.selectMany(paramMap);
		debugSql(sql);
		return sql;
	}
	
	// 1、Integer pageNum ; 2、Integer pageSize
	// 3、Criteria criteria ; 4、String... fieldList
	public String selectPagination(Map<String, Object> paramMap) {
		Integer pageNum = (Integer) paramMap.get("pageNum");
		Integer pageSize = (Integer) paramMap.get("pageSize");
		if(pageNum == null || pageSize == null
				|| pageNum < 0 || pageSize < 1) {
			throw new RuntimeException("The pageNum can not less than 0 or pageSize can not less than 1.");
		}
		Map<String, Object> map = new HashMap<String, Object>(2);
		map.put("criteria", paramMap.get("criteria"));
		map.put("fieldList", paramMap.get("fieldList"));
		String sql = this.selectMany(map);// SELECT ... FROM ... WHERE ... ORDER BY ... ...
		StringBuilder builder = new StringBuilder(sql);
		builder.append(String.format(" LIMIT %s, %s", pageNum, pageSize));// LIMIT ..., ...
		debugSql(builder.toString());
		return builder.toString();
	}
	
	// 1、Criteria criteria
	public String countPagination(Criteria criteria) {
		if(criteria == null) {
			throw new RuntimeException("The Criteria can not be null.");
		}
		Table table = beanClass.getAnnotation(Table.class);
		SQL sql = new SQL();
		sql.SELECT("COUNT(*)");// SELECT
		sql.FROM(table.name());// FROM
		if(criteria.hasJoin()) {// LEFT OUTER JOIN
			Set<String> fkSet = criteria.getFkSet();
			for(String property : fkSet) {
				String tableName = tableNameMap.get(property);
				if(tableName != null && tableName.trim().length() > 0) {
					StringBuilder builder = new StringBuilder();
					builder.append(String.format("%s ON ", tableName));
					ResultMapping rm = fkIdResult.get(property);
					String fkColumn = baseResultMapping.get(property + "." + rm.getProperty());
					builder.append(String.format("%s.`%s`=%s.`%s`", table.name(), fkColumn, tableName, rm.getColumn()));
					sql.LEFT_OUTER_JOIN(builder.toString());
				}
			}
		}
		// WHERE
		List<ConditionDefinition> conditionList = criteria.getConditionList();
		if(conditionList.size() > 0) {
			for(int i = 0; i < conditionList.size(); i++) {
				ConditionDefinition condition = conditionList.get(i);
				String fieldName = condition.getFieldName();
				String operator = condition.getOperator();
				Object value = condition.getValue();
				String[] values = fieldName.split("\\.");
				if(values.length == 1) {
					String column = baseResultMapping.get(fieldName);
					if(column == null || column.trim().length() == 0) {
						column = idResult.getColumn();
					}
					if(criteria.hasJoin()) {
						column = String.format("%s.%s", table.name(), column);
					}
					if(value != null) {
						if("IN".equals(operator)) {
							if(value.getClass().isArray()) {
								Object[] objArr = (Object[]) value;
								StringBuilder builder = new StringBuilder();
								builder.append(String.format("%s %s (", column, operator));
								for(int j = 0; j < objArr.length; j++) {
									builder.append(String.format("#{conditionList[%d].value[%d]},", i, j));
								}
								builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
								builder.append(")");
								sql.WHERE(builder.toString());
							}
						} else {
							sql.WHERE(String.format("%s %s #{conditionList[%d].value}", column, operator, i));
						}
					} else {// IS NULL 或者 IS NOT NULL
						column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
						sql.WHERE(String.format("%s.%s %s", table.name(), column, operator));
					}
				} else if(values.length == 2) {
					String column = fkResultMapping.get(fieldName);
					String tableName = tableNameMap.get(values[0]);
					if(tableName != null) {
						if(column == null || column.trim().length() == 0) {
							column = fkIdResult.get(values[0]).getColumn();
						}
						column = tableName + "." + column;
						if(value != null) {
							if("IN".equals(operator)) {
								if(value.getClass().isArray()) {
									Object[] objArr = (Object[]) value;
									StringBuilder builder = new StringBuilder();
									builder.append(String.format("%s %s (", column, operator));
									for(int j = 0; j < objArr.length; j++) {
										builder.append(String.format("#{conditionList[%d].value[%d]},", i, j));
									}
									builder.delete(builder.length() - 1, builder.length());// 删除掉最后一个","
									builder.append(")");
									sql.WHERE(builder.toString());
								}
							} else {
								sql.WHERE(String.format("%s %s #{conditionList[%d].value}", column, operator, i));
							}
						} else {// IS NULL 或者 IS NOT NULL
							column = baseResultMapping.get(fieldName + "." + fkIdResult.get(fieldName).getProperty());
							sql.WHERE(String.format("%s.%s %s", table.name(), column, operator));
						}
					}
				} else {
					throw new RuntimeException(">>>>>>>>>>>>>can not support this scene.");
				}
			}
		}
		debugSql(sql.toString());
		return sql.toString();
	}
	
	public static void setMappedStmt(MappedStatement mappedStmt) {
		BaseSqlProvider.mappedStmt = mappedStmt;
	}
	
	public static void setProperties(Properties properties) {
		BaseSqlProvider.properties = properties;
	}
	
	private void init() {
		this.baseResultMapping = new HashMap<String, String>();
		this.fkIdResult = new HashMap<String, ResultMapping>();
		this.fkResultMapping = new HashMap<String, String>();
		this.columnPrefixMap = new HashMap<String, String>();
		this.tableNameMap = new HashMap<String, String>();
		String id = mappedStmt.getId();
		if(id != null) {
			int index = id.lastIndexOf(".");
			if(index != -1) {
				String prefix = id.substring(0, index);
				String resultMapId = prefix + ".BaseResultMap";// 拼接ResultMap的id
				ResultMap resultMap = mappedStmt.getConfiguration().getResultMap(resultMapId);
				if(resultMap != null) {
					parse(resultMap);
				}
			}
		}
		// debug message
		if(properties != null) {
			String value = properties.getProperty("common-mapper.debug");
			if("true".equals(value)) {// 控制台输出
				StringBuilder builder = new StringBuilder();
				builder.append("================ JavaBean ================\r\n\t");
				builder.append("JavaBean : " + beanClass.getName() + "\r\n\t");
				builder.append("================ BaseResultMap ================\r\n\t");
				builder.append(idResult.getProperty() + " : " + idResult.getColumn() + "\r\n\t");
				for(Map.Entry<String, String> me : baseResultMapping.entrySet()) {
					builder.append(me.getKey() + " : " + me.getValue() + "\r\n\t");
				}
				builder.append("================ FkResultMap ================\r\n\t");
				for(Map.Entry<String, ResultMapping> me : fkIdResult.entrySet()) {
					String prefix = me.getKey();
					builder.append(String.format("---------------- %s ----------------\r\n\t", prefix));
					builder.append(prefix + "." + me.getValue().getProperty() + " : " + me.getValue().getColumn() + "\r\n\t");
					for(Map.Entry<String, String> me2 : fkResultMapping.entrySet()) {
						String key = me2.getKey();
						if(key.startsWith(prefix)) {
							builder.append(key + " : " + me2.getValue() + "\r\n\t");
						}
					}
				}
				builder.delete(builder.length() - 3, builder.length());
				debug(builder.toString());
			}
		}
	}
	
	private void parseBeanClass(ResultMap resultMap) {
		Class<?> beanClass = resultMap.getType();
		if(beanClass != null) {// 获取JavaBean的Class
			// debug("JavaBean : " + beanClass.getName());
			this.beanClass = beanClass;
		} else {
			throw new RuntimeException(">>>>>>>>>>JavaBean can not be null.");
		}
	}
	
	private void parseIdResult(ResultMap resultMap) {
		List<ResultMapping> idList = resultMap.getIdResultMappings();
		if(idList != null && idList.size() == 1) {// 获取<id/>的ResultMapping实例
			// debug(idList.get(0).getProperty() + " : " + idList.get(0).getColumn());
			this.idResult = idList.get(0);
		} else {
			throw new RuntimeException(">>>>>>>>>>Only support one primary key.");
		}
	}
	
	private void parse(ResultMap resultMap) {
		parseBeanClass(resultMap);// 解析BeanClass
		parseIdResult(resultMap);// 解析idResult
		List<ResultMapping> list = resultMap.getResultMappings();
		if(list != null && list.size() > 0) {
			for(ResultMapping rm : list) {
				if(rm.getNestedResultMapId() != null) {
					// debug("===========关联表=========");
					// debug(rm.getProperty() + " : " + rm.getColumnPrefix());
					columnPrefixMap.put(rm.getProperty(), rm.getColumnPrefix());// columnPrefix
					ResultMap resultMap2 = mappedStmt.getConfiguration().getResultMap(rm.getNestedResultMapId());
					Table table = resultMap2.getType().getAnnotation(Table.class);
					tableNameMap.put(rm.getProperty(), table.name());
					List<ResultMapping> fkIdList = resultMap2.getIdResultMappings();
					if(fkIdList != null && fkIdList.size() == 1) {
						// debug(rm.getProperty() + "." + fkIdList.get(0).getProperty() + " : " + fkIdList.get(0).getColumn());
						this.baseResultMapping.put(rm.getProperty() + "." + fkIdList.get(0).getProperty(), rm.getColumn());// 外键
						this.fkIdResult.put(rm.getProperty(), fkIdList.get(0));// fkIdResult
					} else {
						throw new RuntimeException(">>>>>>>>>>Only support one primary key.");
					}
					List<ResultMapping> list2 = resultMap2.getResultMappings();
					for(ResultMapping rm2 : list2) {
						if(!rm2.equals(fkIdList.get(0))) {
							// debug(rm.getProperty() + "." + rm2.getProperty() + " : " + rm2.getColumn());
							this.fkResultMapping.put(rm.getProperty() + "." + rm2.getProperty(), rm2.getColumn());
						}
					}
				} else {
					if(!rm.equals(this.idResult)) {
						// debug(rm.getProperty() + " : " + rm.getColumn());
						this.baseResultMapping.put(rm.getProperty(), rm.getColumn());
					}
				}
			}
		}
	}
	
	private void debug(String message) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String debug = String.format("%s  DEBUG --- [ common-mapper ] : \r\n\t%s", sdf.format(new Date()), message);
		System.out.println(debug);
	}
	
	private void debugSql(String sql) {
		if(properties != null) {
			String value = properties.getProperty("common-mapper.debug");
			if("false".equals(value)) {// 关闭控制台输出
				return;
			}
		}
		sql = sql.replaceAll("\n", "\r\n\t");
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
		String debug = String.format("%s  DEBUG --- [ common-mapper ] : \r\n\t%s", sdf.format(new Date()), sql);
		System.out.println(debug);
	}
}
