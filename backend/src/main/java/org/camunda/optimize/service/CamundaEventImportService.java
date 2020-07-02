/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.DefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ImportRequestDto;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.importing.FlowNodeEventDto;
import org.camunda.optimize.dto.optimize.query.event.CamundaActivityEventDto;
import org.camunda.optimize.dto.optimize.query.variable.ProcessVariableDto;
import org.camunda.optimize.service.es.writer.BusinessKeyWriter;
import org.camunda.optimize.service.es.writer.CamundaActivityEventWriter;
import org.camunda.optimize.service.es.writer.variable.VariableUpdateInstanceWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.importing.engine.service.ProcessDefinitionResolverService;
import org.camunda.optimize.service.util.EventDtoBuilderUtil;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_END_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.PROCESS_START_TYPE;
import static org.camunda.optimize.service.events.CamundaEventService.SINGLE_MAPPED_TYPES;
import static org.camunda.optimize.service.events.CamundaEventService.SPLIT_START_END_MAPPED_TYPES;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskEndEventSuffix;
import static org.camunda.optimize.service.util.EventDtoBuilderUtil.applyCamundaTaskStartEventSuffix;

@AllArgsConstructor
@Component
@Slf4j
public class CamundaEventImportService {

  private final VariableUpdateInstanceWriter variableUpdateInstanceWriter;
  private final CamundaActivityEventWriter camundaActivityEventWriter;
  private final BusinessKeyWriter businessKeyWriter;
  private final ProcessDefinitionResolverService processDefinitionResolverService;
  private final ConfigurationService configurationService;

  public List<ImportRequestDto> generateRunningCamundaActivityEventsImports(
    List<FlowNodeEventDto> runningActivityInstances) {
    final String engineAlias = runningActivityInstances.get(0).getEngineAlias();
    if (shouldImport(engineAlias)) {
      return generateCamundaActivityEventsImports(
        runningActivityInstances,
        this::convertRunningActivityToCamundaActivityEvents
      );
    }
    return Collections.emptyList();
  }

  public List<ImportRequestDto> generateCompletedCamundaActivityEventsImports(
    List<FlowNodeEventDto> completedActivityInstances) {
    final String engineAlias = completedActivityInstances.get(0).getEngineAlias();
    if (shouldImport(engineAlias)) {
      return generateCamundaActivityEventsImports(
        completedActivityInstances,
        this::convertCompletedActivityToCamundaActivityEvents
      );
    }
    return Collections.emptyList();
  }

  public List<ImportRequestDto> generateRunningProcessInstanceImports(
    List<ProcessInstanceDto> runningProcessInstances) {
    final String engineAlias = runningProcessInstances.get(0).getEngine();
    if (shouldImport(engineAlias)) {
      final List<ImportRequestDto> imports =
        generateCamundaActivityEventsImports(
          runningProcessInstances,
          this::convertRunningProcessInstanceToCamundaActivityEvents
        );
      imports.addAll(businessKeyWriter.generateBusinessKeyImports(runningProcessInstances));
      return imports;
    }
    return Collections.emptyList();
  }

  public List<ImportRequestDto> generateCompletedProcessInstanceImports(List<ProcessInstanceDto> completedProcessInstances) {
    final String engineAlias = completedProcessInstances.get(0).getEngine();
    if (shouldImport(engineAlias)) {
      final List<ImportRequestDto> imports =
        generateCamundaActivityEventsImports(
          completedProcessInstances,
          this::convertCompletedProcessInstanceToCamundaActivityEvents
        );
      imports.addAll(businessKeyWriter.generateBusinessKeyImports(completedProcessInstances));
      return imports;
    }
    return Collections.emptyList();
  }

  public List<ImportRequestDto> generateVariableUpdateImports(final List<ProcessVariableDto> variableUpdates) {
    final String engineAlias = variableUpdates.get(0).getEngineAlias();
    if (shouldImport(engineAlias)) {
      return variableUpdateInstanceWriter.generateVariableUpdateImports(variableUpdates);
    }
    return Collections.emptyList();
  }

  private <T extends OptimizeDto> List<ImportRequestDto> generateCamundaActivityEventsImports(
    final List<T> importedEntities,
    final Function<T, Stream<CamundaActivityEventDto>> activityEventExtractor) {

    final List<CamundaActivityEventDto> camundaActivityEventDtos = importedEntities
      .stream()
      .flatMap(activityEventExtractor)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
    return camundaActivityEventWriter.generateImportRequests(camundaActivityEventDtos);
  }

  private boolean shouldImport(final String engineAlias) {
    return configurationService.getConfiguredEngines().get(engineAlias).isEventImportEnabled();
  }

  private Stream<CamundaActivityEventDto> convertRunningActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (SPLIT_START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toFlowNodeActivityStartEvent(flowNodeEventDto));
    }
    return Stream.empty();
  }

  private Stream<CamundaActivityEventDto> convertCompletedActivityToCamundaActivityEvents(FlowNodeEventDto flowNodeEventDto) {
    if (SPLIT_START_END_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(
        toFlowNodeActivityStartEvent(flowNodeEventDto),
        toCamundaActivityEvent(flowNodeEventDto).toBuilder()
          .activityId(applyCamundaTaskEndEventSuffix(flowNodeEventDto.getActivityId()))
          .activityName(applyCamundaTaskEndEventSuffix(flowNodeEventDto.getActivityName()))
          .activityInstanceId(applyCamundaTaskEndEventSuffix(flowNodeEventDto.getId()))
          // the end event of a split task should have a higher counter than the start, so we convert it and
          // increment by one.
          .orderCounter(
            Optional.ofNullable(flowNodeEventDto.getOrderCounter())
              .map(counter -> convertToOptimizeCounter(counter) + 1)
              .orElse(null)
          )
          .timestamp(flowNodeEventDto.getEndDate())
          .build()
      );
    } else if (SINGLE_MAPPED_TYPES.contains(flowNodeEventDto.getActivityType())) {
      return Stream.of(toCamundaActivityEvent(flowNodeEventDto).toBuilder()
                         .timestamp(flowNodeEventDto.getStartDate())
                         .build());
    }
    return Stream.empty();
  }

  private CamundaActivityEventDto toFlowNodeActivityStartEvent(final FlowNodeEventDto flowNodeEventDto) {
    return toCamundaActivityEvent(flowNodeEventDto).toBuilder()
      .activityId(applyCamundaTaskStartEventSuffix(flowNodeEventDto.getActivityId()))
      .activityName(applyCamundaTaskStartEventSuffix(flowNodeEventDto.getActivityName()))
      .activityInstanceId(applyCamundaTaskStartEventSuffix(flowNodeEventDto.getId()))
      .build();
  }

  private CamundaActivityEventDto toCamundaActivityEvent(final FlowNodeEventDto flowNodeEventDto) {
    final ProcessDefinitionOptimizeDto processDefinition =
      processDefinitionResolverService.getDefinitionForProcessDefinitionId(flowNodeEventDto.getProcessDefinitionId())
        .orElseThrow(() -> new OptimizeRuntimeException(
          "Could not resolve version for process definition: " + flowNodeEventDto.getProcessDefinitionId()
        ));
    return CamundaActivityEventDto.builder()
      .activityId(flowNodeEventDto.getActivityId())
      .activityName(flowNodeEventDto.getActivityName())
      .activityType(flowNodeEventDto.getActivityType())
      .activityInstanceId(flowNodeEventDto.getId())
      .processDefinitionKey(flowNodeEventDto.getProcessDefinitionKey())
      .processInstanceId(flowNodeEventDto.getProcessInstanceId())
      .processDefinitionVersion(processDefinition.getVersion())
      .processDefinitionName(processDefinition.getName())
      .engine(flowNodeEventDto.getEngineAlias())
      .tenantId(flowNodeEventDto.getTenantId())
      .timestamp(flowNodeEventDto.getStartDate())
      .orderCounter(
        Optional.ofNullable(flowNodeEventDto.getOrderCounter()).map(this::convertToOptimizeCounter).orElse(null)
      )
      .build();
  }

  private Long convertToOptimizeCounter(final Long counter) {
    // We have to double the counters from the engine because we split activities, creating more than originally
    // imported
    return counter * 2;
  }

  private Stream<CamundaActivityEventDto> convertRunningProcessInstanceToCamundaActivityEvents(
    final ProcessInstanceDto processInstanceDto) {
    String processDefinitionName = processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      processInstanceDto.getProcessDefinitionId()).map(DefinitionOptimizeDto::getName).orElse(null);
    return Stream.of(toProcessInstanceStartEvent(
      processInstanceDto, processDefinitionName, processInstanceDto.getStartDate()
    ));
  }

  private Stream<CamundaActivityEventDto> convertCompletedProcessInstanceToCamundaActivityEvents(
    final ProcessInstanceDto processInstanceDto) {
    String processDefinitionName = processDefinitionResolverService.getDefinitionForProcessDefinitionId(
      processInstanceDto.getProcessDefinitionId()).map(DefinitionOptimizeDto::getName).orElse(null);
    return Stream.of(
      toProcessInstanceStartEvent(processInstanceDto, processDefinitionName, processInstanceDto.getStartDate()),
      toProcessInstanceEndEvent(processInstanceDto, processDefinitionName, processInstanceDto.getEndDate())
    );
  }

  private CamundaActivityEventDto toProcessInstanceStartEvent(final ProcessInstanceDto processInstanceDto,
                                                              final String processDefinitionName,
                                                              final OffsetDateTime startDate) {
    return toProcessInstanceEvent(
      processInstanceDto,
      processDefinitionName,
      PROCESS_START_TYPE,
      EventDtoBuilderUtil::applyCamundaProcessInstanceStartEventSuffix,
      startDate
    );
  }

  private CamundaActivityEventDto toProcessInstanceEndEvent(final ProcessInstanceDto processInstanceDto,
                                                            final String processDefinitionName,
                                                            final OffsetDateTime startDate) {
    return toProcessInstanceEvent(
      processInstanceDto,
      processDefinitionName,
      PROCESS_END_TYPE,
      EventDtoBuilderUtil::applyCamundaProcessInstanceEndEventSuffix,
      startDate
    );
  }

  private CamundaActivityEventDto toProcessInstanceEvent(final ProcessInstanceDto processInstanceDto,
                                                         final String processDefinitionName,
                                                         final String processEventType,
                                                         final Function<String, String> idSuffixerFunction,
                                                         final OffsetDateTime startDate) {
    return CamundaActivityEventDto.builder()
      .activityId(idSuffixerFunction.apply(processInstanceDto.getProcessDefinitionKey()))
      .activityName(processEventType)
      .activityType(processEventType)
      .activityInstanceId(idSuffixerFunction.apply(processInstanceDto.getProcessInstanceId()))
      .processDefinitionKey(processInstanceDto.getProcessDefinitionKey())
      .processInstanceId(processInstanceDto.getProcessInstanceId())
      .processDefinitionVersion(processInstanceDto.getProcessDefinitionVersion())
      .processDefinitionName(processDefinitionName)
      .engine(processInstanceDto.getEngine())
      .tenantId(processInstanceDto.getTenantId())
      .timestamp(startDate)
      // process instance start/end events should not have an order counter
      .orderCounter(null)
      .build();
  }

}
