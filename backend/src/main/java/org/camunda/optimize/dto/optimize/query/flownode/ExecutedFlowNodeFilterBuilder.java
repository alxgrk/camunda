package org.camunda.optimize.dto.optimize.query.flownode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.camunda.optimize.service.es.filter.ExecutedFlowNodeFilter.EQUAL_OPERATOR;
import static org.camunda.optimize.service.es.filter.ExecutedFlowNodeFilter.UNEQUAL_OPERATOR;

public class ExecutedFlowNodeFilterBuilder {

  private String operator = EQUAL_OPERATOR;
  private List<String> values = new ArrayList<>();
  private List<ExecutedFlowNodeFilterDto> executedFlowNodes = new ArrayList<>();

  public static ExecutedFlowNodeFilterBuilder construct() {
    return new ExecutedFlowNodeFilterBuilder();
  }

  public ExecutedFlowNodeFilterBuilder id(String flowNodeId) {
    values.add(flowNodeId);
    return this;
  }

  public ExecutedFlowNodeFilterBuilder equalOperator() {
    operator = EQUAL_OPERATOR;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder unequalOperator() {
    operator = UNEQUAL_OPERATOR;
    return this;
  }

  public ExecutedFlowNodeFilterBuilder ids(String... flowNodeIds) {
    values.addAll(Arrays.asList(flowNodeIds));
    return this;
  }

  public ExecutedFlowNodeFilterBuilder and() {
    addNewFilter();
    return this;
  }

  private void addNewFilter() {
    ExecutedFlowNodeFilterDto executedFlowNodeFilterDto = new ExecutedFlowNodeFilterDto();
    executedFlowNodeFilterDto.setOperator(operator);
    executedFlowNodeFilterDto.setValues(new ArrayList<>(values));
    executedFlowNodes.add(executedFlowNodeFilterDto);
    values.clear();
    restoreDefaultOperator();
  }

  private void restoreDefaultOperator() {
    operator = EQUAL_OPERATOR;
  }

  public List<ExecutedFlowNodeFilterDto> build() {
    if (!values.isEmpty()) {
      addNewFilter();
    }
    List<ExecutedFlowNodeFilterDto> result = new ArrayList<>(executedFlowNodes);
    executedFlowNodes.clear();
    return result;
  }
}
