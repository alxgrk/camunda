/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.es.writer;

import static org.camunda.optimize.service.db.DatabaseConstants.NUMBER_OF_RETRIES_ON_CONFLICT;
import static org.camunda.optimize.service.db.es.writer.ElasticsearchWriterUtil.createDefaultScriptWithSpecificDtoParams;
import static org.camunda.optimize.service.db.schema.index.events.EventTraceStateIndex.EVENT_TRACE;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.event.sequence.EventTraceStateDto;
import org.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import org.camunda.optimize.service.db.writer.EventTraceStateWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.context.annotation.Conditional;

@AllArgsConstructor
@Slf4j
@Conditional(ElasticSearchCondition.class)
public class EventTraceStateWriterES implements EventTraceStateWriter {

  private final String indexKey;
  private final OptimizeElasticsearchClient esClient;
  private final ObjectMapper objectMapper;

  @Override
  public void upsertEventTraceStates(final List<EventTraceStateDto> eventTraceStateDtos) {
    log.debug("Writing [{}] event trace states to elasticsearch", eventTraceStateDtos.size());

    final BulkRequest bulkRequest = new BulkRequest();
    for (EventTraceStateDto eventTraceStateDto : eventTraceStateDtos) {
      bulkRequest.add(createEventTraceStateUpsertRequest(eventTraceStateDto));
    }

    if (bulkRequest.numberOfActions() > 0) {
      try {
        final BulkResponse bulkResponse = esClient.bulk(bulkRequest);
        if (bulkResponse.hasFailures()) {
          final String errorMessage =
              String.format(
                  "There were failures while writing event trace states. Received error message: %s",
                  bulkResponse.buildFailureMessage());
          throw new OptimizeRuntimeException(errorMessage);
        }
      } catch (IOException e) {
        final String errorMessage = "There were errors while writing event trace states.";
        log.error(errorMessage, e);
        throw new OptimizeRuntimeException(errorMessage, e);
      }
    }
  }

  private UpdateRequest createEventTraceStateUpsertRequest(
      final EventTraceStateDto eventTraceStateDto) {
    try {
      return new UpdateRequest()
          .index(getIndexName(indexKey))
          .id(eventTraceStateDto.getTraceId())
          .script(createUpdateScript(eventTraceStateDto))
          .upsert(objectMapper.writeValueAsString(eventTraceStateDto), XContentType.JSON)
          .retryOnConflict(NUMBER_OF_RETRIES_ON_CONFLICT);
    } catch (JsonProcessingException ex) {
      final String errorMessage =
          String.format(
              "There was a problem creating an upsert for the event trace state with id [%s].",
              eventTraceStateDto.getTraceId());
      throw new OptimizeRuntimeException(errorMessage, ex);
    }
  }

  private Script createUpdateScript(final EventTraceStateDto eventTraceStateDto) {
    final Map<String, Object> params = new HashMap<>();
    params.put(EVENT_TRACE, eventTraceStateDto.getEventTrace());
    return createDefaultScriptWithSpecificDtoParams(updateScript(), params, objectMapper);
  }
}
