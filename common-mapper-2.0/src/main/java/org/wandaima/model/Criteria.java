package org.wandaima.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 查询条件<br/>
 * 
 * @author lmkang25@163.com
 *
 */
public class Criteria {

	private List<ConditionDefinition> conditionList = new ArrayList<ConditionDefinition>();
	private List<ConditionDefinition> orderbyList = new ArrayList<ConditionDefinition>();
	private Set<String> fkSet = new HashSet<String>();

	public Criteria eq(String name, Object value) {
		if (name != null && value != null) {
			setCondition(name, "=", value);
			setFk(name);
		}
		return this;
	}

	public Criteria le(String name, Object value) {
		if (name != null && value != null) {
			setCondition(name, "<=", value);
			setFk(name);
		}
		return this;
	}

	public Criteria lt(String name, Object value) {
		if (name != null && value != null) {
			setCondition(name, "<", value);
			setFk(name);
		}
		return this;
	}

	public Criteria ge(String name, Object value) {
		if (name != null && value != null) {
			setCondition(name, ">=", value);
			setFk(name);
		}
		return this;
	}

	public Criteria gt(String name, Object value) {
		if (name != null && value != null) {
			setCondition(name, ">", value);
			setFk(name);
		}
		return this;
	}

	public Criteria like(String name, String value) {
		if (name != null && value != null) {
			setCondition(name, "LIKE", value);
			setFk(name);
		}
		return this;
	}

	public Criteria isNull(String name) {
		if (name != null) {
			setCondition(name, "IS NULL", null);
			setFk(name);
		}
		return this;
	}

	public Criteria isNotNull(String name) {
		if (name != null) {
			setCondition(name, "IS NOT NULL", null);
			setFk(name);
		}
		return this;
	}

	public Criteria orderBy(String name, String value) {
		if (name != null && value != null) {
			setOrderby(name, "ORDER BY", value);
			setFk(name);
		}
		return this;
	}

	public List<ConditionDefinition> getConditionList() {
		return conditionList;
	}

	public List<ConditionDefinition> getOrderbyList() {
		return orderbyList;
	}

	public Set<String> getFkSet() {
		return this.fkSet;
	}

	public boolean hasJoin() {
		return this.fkSet != null && this.fkSet.size() > 0;
	}

	private void setCondition(String fieldName, String operator, Object value) {
		ConditionDefinition condition = new ConditionDefinition();
		condition.setFieldName(fieldName);
		condition.setOperator(operator);
		condition.setValue(value);
		conditionList.add(condition);
	}
	
	private void setOrderby(String fieldName, String operator, Object value) {
		ConditionDefinition condition = new ConditionDefinition();
		condition.setFieldName(fieldName);
		condition.setOperator(operator);
		condition.setValue(value);
		orderbyList.add(condition);
	}
	
	private void setFk(String fieldName) {
		String[] names = fieldName.split("\\.");
		if (names.length == 2) {
			fkSet.add(names[0]);
		}
	}
}
