/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.reader;

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.entities.FlowNodeState.COMPLETED;
import static io.camunda.operate.entities.FlowNodeState.INCIDENT;
import static io.camunda.operate.entities.FlowNodeState.TERMINATED;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.END_DATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.FLOW_NODE_ID;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.ID;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.INCIDENT_KEY;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.LEVEL;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.START_DATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.STATE;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TREE_PATH;
import static io.camunda.operate.schema.templates.FlowNodeInstanceTemplate.TYPE;
import static io.camunda.operate.schema.templates.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.operate.util.ElasticsearchUtil.TERMS_AGG_SIZE;
import static io.camunda.operate.util.ElasticsearchUtil.fromSearchHit;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.constantScoreQuery;
import static org.elasticsearch.index.query.QueryBuilders.existsQuery;
import static org.elasticsearch.index.query.QueryBuilders.idsQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.elasticsearch.index.query.QueryBuilders.termsQuery;
import static org.elasticsearch.search.aggregations.AggregationBuilders.filter;
import static org.elasticsearch.search.aggregations.AggregationBuilders.terms;
import static org.elasticsearch.search.aggregations.AggregationBuilders.topHits;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import io.camunda.operate.entities.EventEntity;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.EventTemplate;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchUtil;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceQueryDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceRequestDto;
import io.camunda.operate.webapp.rest.dto.activity.FlowNodeInstanceResponseDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceBreadcrumbEntryDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeInstanceMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataDto;
import io.camunda.operate.webapp.rest.dto.metadata.FlowNodeMetadataRequestDto;
import io.camunda.operate.webapp.rest.exception.NotFoundException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.ConstantScoreQueryBuilder;
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.filter.Filter;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms.Bucket;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.aggregations.metrics.TopHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceReader extends AbstractReader {

  private static final Logger logger = LoggerFactory.getLogger(FlowNodeInstanceReader.class);

  public static final String AGG_INCIDENT_PATHS = "aggIncidentPaths";
  public static final String AGG_INCIDENTS = "incidents";
  public static final String AGG_RUNNING_PARENT = "running";
  public static final String LEVELS_AGG_NAME = "levelsAgg";
  public static final String LEVELS_TOP_HITS_AGG_NAME = "levelsTopHitsAgg";

  public static final String FINISHED_FLOW_NODES_BUCKETS_AGG_NAME = "finishedFlowNodesBuckets";

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private EventTemplate eventTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  public Map<String, FlowNodeInstanceResponseDto> getFlowNodeInstances(FlowNodeInstanceRequestDto request) {
    Map<String, FlowNodeInstanceResponseDto> response = new HashMap<>();
    for (FlowNodeInstanceQueryDto query: request.getQueries()) {
      response.put(query.getTreePath(), getFlowNodeInstances(query));
    }
    return response;
  }

  private FlowNodeInstanceResponseDto getFlowNodeInstances(FlowNodeInstanceQueryDto request) {
    FlowNodeInstanceResponseDto response = queryFlowNodeInstances(request);

    //query one additional instance
    if (request.getSearchAfterOrEqual() != null || request.getSearchBeforeOrEqual() != null)  {
      adjustResponse(response, request);
    }

    return response;
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual add additional entity either at the beginning of the list,
   * or at the end, to conform with "orEqual" part.
   * @param response
   * @param request
   */
  private void adjustResponse(final FlowNodeInstanceResponseDto response,
      final FlowNodeInstanceQueryDto request) {
    String flowNodeInstanceId = null;
    if (request.getSearchAfterOrEqual() != null) {
      flowNodeInstanceId = (String) request.getSearchAfterOrEqual()[1];
    } else if (request.getSearchBeforeOrEqual() != null) {
      flowNodeInstanceId = (String) request.getSearchBeforeOrEqual()[1];
    }

    FlowNodeInstanceQueryDto newRequest = request.createCopy()
        .setSearchAfter(null).setSearchAfterOrEqual(null).setSearchBefore(null)
        .setSearchBeforeOrEqual(null);

    final List<FlowNodeInstanceDto> entities = queryFlowNodeInstances(newRequest,
        flowNodeInstanceId).getChildren();
    if (entities.size() > 0) {
      final FlowNodeInstanceDto entity = entities.get(0);
      final List<FlowNodeInstanceDto> children = response.getChildren();
      if (request.getSearchAfterOrEqual() != null) {
        //insert at the beginning of the list and remove the last element
        if (request.getPageSize() != null && children.size() == request.getPageSize()) {
          children.remove(children.size() - 1);
        }
        children.add(0, entity);
      } else if (request.getSearchBeforeOrEqual() != null) {
        //insert at the end of the list and remove the first element
        if (request.getPageSize() != null && children.size() == request.getPageSize()) {
          children.remove(0);
        }
        children.add(entity);
      }
    }
  }

  private FlowNodeInstanceResponseDto queryFlowNodeInstances(
      final FlowNodeInstanceQueryDto flowNodeInstanceRequest) {
    return queryFlowNodeInstances(flowNodeInstanceRequest, null);
  }

  private FlowNodeInstanceResponseDto queryFlowNodeInstances(
      final FlowNodeInstanceQueryDto flowNodeInstanceRequest, String flowNodeInstanceId) {

    final String parentTreePath = flowNodeInstanceRequest.getTreePath();
    final int level = parentTreePath.split("/").length;

    IdsQueryBuilder idsQuery = null;
    if (flowNodeInstanceId != null) {
      idsQuery = idsQuery().addIds(flowNodeInstanceId);
    }

    final QueryBuilder query =
        constantScoreQuery(
              termQuery(PROCESS_INSTANCE_KEY, flowNodeInstanceRequest.getProcessInstanceId()));

    final AggregationBuilder incidentAgg = getIncidentsAgg();

    AggregationBuilder runningParentsAgg =
          filter(AGG_RUNNING_PARENT,
              joinWithAnd(boolQuery().mustNot(existsQuery(END_DATE)),
                  termQuery(TREE_PATH, parentTreePath),
                  termQuery(LEVEL, level - 1)));

    final QueryBuilder postFilter =
        joinWithAnd(termQuery(LEVEL, level),
            termQuery(TREE_PATH, parentTreePath),
            idsQuery);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder()
        .query(query)
        .aggregation(incidentAgg)
        .aggregation(runningParentsAgg)
        .postFilter(postFilter);
    if (flowNodeInstanceRequest.getPageSize() != null) {
      searchSourceBuilder.size(flowNodeInstanceRequest.getPageSize());
    }

    applySorting(searchSourceBuilder, flowNodeInstanceRequest);

    final SearchRequest searchRequest = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate)
        .source(searchSourceBuilder);
    try {
      FlowNodeInstanceResponseDto response;
      if (flowNodeInstanceRequest.getPageSize() != null) {
        response = getOnePage(searchRequest);
      } else {
        response = scrollAllSearchHits(searchRequest);
      }
      //for process instance level, we don't return running flag
      if (level == 1) {
        response.setRunning(null);
      }
      if (flowNodeInstanceRequest.getSearchBefore() != null ||
          flowNodeInstanceRequest.getSearchBeforeOrEqual() != null) {
        Collections.reverse(response.getChildren());
      }

      return response;
    } catch (IOException e) {
      final String message = String
          .format("Exception occurred, while obtaining all flow node instances: %s",
              e.getMessage());
      throw new OperateRuntimeException(message, e);
    }

  }

  private AggregationBuilder getIncidentsAgg() {
    return filter(AGG_INCIDENTS, existsQuery(INCIDENT_KEY))
        .subAggregation(
            terms(AGG_INCIDENT_PATHS).field(TREE_PATH)
                .size(TERMS_AGG_SIZE));
  }

  private FlowNodeInstanceResponseDto scrollAllSearchHits(final SearchRequest searchRequest)
      throws IOException {
    Set<String> incidentPaths = new HashSet<>();
    final Boolean[] runningParent = new Boolean[]{false};
    final List<FlowNodeInstanceEntity> children =
        ElasticsearchUtil
            .scroll(searchRequest,
                FlowNodeInstanceEntity.class,
                objectMapper,
                esClient,
                getSearchHitFunction(incidentPaths),
                null,
                getAggsProcessor(incidentPaths, runningParent));
    return new FlowNodeInstanceResponseDto(runningParent[0],
        FlowNodeInstanceDto.createFrom(children));
  }

  private Function<SearchHit, FlowNodeInstanceEntity> getSearchHitFunction(
      final Set<String> incidentPaths) {
    return (sh) -> {
      FlowNodeInstanceEntity entity = fromSearchHit(sh.getSourceAsString(), objectMapper,
              FlowNodeInstanceEntity.class);
      entity.setSortValues(sh.getSortValues());
      if (incidentPaths.contains(entity.getTreePath())) {
        entity.setState(INCIDENT);
      }
      return entity;
    };
  }

  private FlowNodeInstanceResponseDto getOnePage(final SearchRequest searchRequest)
      throws IOException {
    final SearchResponse searchResponse = esClient
        .search(searchRequest, RequestOptions.DEFAULT);

    Set<String> incidentPaths = new HashSet<>();
    final Boolean[] runningParent = new Boolean[1];
    processAggregation(searchResponse.getAggregations(), incidentPaths, runningParent);
    final List<FlowNodeInstanceEntity> children =
        ElasticsearchUtil
        .mapSearchHits(searchResponse.getHits().getHits(),
            getSearchHitFunction(incidentPaths));
    return new FlowNodeInstanceResponseDto(runningParent[0],
        FlowNodeInstanceDto.createFrom(children));
  }

  private Consumer<Aggregations> getAggsProcessor(Set<String> incidentPaths,
      Boolean[] runningParent) {
    return (aggs) -> {
      Filter filterAggs = aggs.get(AGG_INCIDENTS);
      if (filterAggs != null) {
        Terms termsAggs = filterAggs.getAggregations().get(AGG_INCIDENT_PATHS);
        if (termsAggs != null) {
          incidentPaths.addAll(termsAggs.getBuckets().stream().map((b) -> b.getKeyAsString())
              .collect(Collectors.toSet()));
        }
      }
      filterAggs = aggs.get(AGG_RUNNING_PARENT);
      if (filterAggs != null && filterAggs.getDocCount() > 0) {
        runningParent[0] = true;
      }
    };
  }

  private Set<String> processAggregation(final Aggregations aggregations, Set<String> incidentPaths,
      Boolean[] runningParent) {
    getAggsProcessor(incidentPaths, runningParent).accept(aggregations);
    return incidentPaths;
  }

  /**
   * In case of searchAfterOrEqual and searchBeforeOrEqual, this method will ignore "orEqual" part.
   * @param searchSourceBuilder
   * @param request
   */
  private void applySorting(final SearchSourceBuilder searchSourceBuilder,
      final FlowNodeInstanceQueryDto request) {

    final boolean directSorting =
        request.getSearchAfter() != null || request.getSearchAfterOrEqual() != null
            || (request.getSearchBefore() == null && request.getSearchBeforeOrEqual() == null);

    if (directSorting) { //this sorting is also the default one for 1st page
      searchSourceBuilder
          .sort(START_DATE, SortOrder.ASC)
          .sort(ID, SortOrder.ASC);
      if (request.getSearchAfter() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfter());
      } else if (request.getSearchAfterOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchAfterOrEqual());
      }
    } else { //searchBefore != null
      //reverse sorting
      searchSourceBuilder
          .sort(START_DATE, SortOrder.DESC)
          .sort(ID, SortOrder.DESC);
      if (request.getSearchBefore() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBefore());
      } else if (request.getSearchBeforeOrEqual() != null) {
        searchSourceBuilder.searchAfter(request.getSearchBeforeOrEqual());
      }
    }

  }

  public FlowNodeMetadataDto getFlowNodeMetadata(String processInstanceId,
      final FlowNodeMetadataRequestDto request) {
    if (request.getFlowNodeId() != null) {
      return getMetadataByFlowNodeId(processInstanceId, request.getFlowNodeId(), request.getFlowNodeType());
    } else if (request.getFlowNodeInstanceId() != null) {
      return getMetadataByFlowNodeInstanceId(request.getFlowNodeInstanceId());
    }
    return null;
  }

  private FlowNodeMetadataDto getMetadataByFlowNodeInstanceId(final String flowNodeInstanceId) {

    final FlowNodeInstanceEntity flowNodeInstance = getFlowNodeInstanceEntity(flowNodeInstanceId);

    final FlowNodeMetadataDto result = new FlowNodeMetadataDto();
    result.setInstanceMetadata(buildInstanceMetadata(flowNodeInstance));
    result.setFlowNodeInstanceId(flowNodeInstanceId);

    //calculate breadcrumb
    result.setBreadcrumb(
        buildBreadcrumb(flowNodeInstance.getTreePath(), flowNodeInstance.getFlowNodeId(),
            flowNodeInstance.getLevel()));

    return result;
  }

  private FlowNodeInstanceEntity getFlowNodeInstanceEntity(final String flowNodeInstanceId) {
    final FlowNodeInstanceEntity flowNodeInstance;
    final TermQueryBuilder flowNodeInstanceIdQ = termQuery(ID, flowNodeInstanceId);
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(flowNodeInstanceIdQ)));
    final SearchResponse response;
    try {
      response = esClient.search(request, RequestOptions.DEFAULT);
      flowNodeInstance = getFlowNodeInstance(response);
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining metadata for flow node instance: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return flowNodeInstance;
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumb(final String treePath,
      final String flowNodeId, final int level) {

    final List<FlowNodeInstanceBreadcrumbEntryDto> result = new ArrayList<>();

    final QueryBuilder query = joinWithAnd(
        termQuery(FLOW_NODE_ID, flowNodeId),
        matchQuery(TREE_PATH, treePath),
        rangeQuery(LEVEL).lte(level)
    );
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate)
        .source(new SearchSourceBuilder()
            .query(constantScoreQuery(query))
            .fetchSource(false)
            .size(0)
            .aggregation(getLevelsAggs()));
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

      final Terms levelsAgg = response.getAggregations().get(LEVELS_AGG_NAME);
      result.addAll(buildBreadcrumbForFlowNodeId(levelsAgg.getBuckets(), level));

      return result;
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining metadata for flow node: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private FlowNodeMetadataDto getMetadataByFlowNodeId(final String processInstanceId,
      final String flowNodeId, final FlowNodeType flowNodeType) {

    final TermQueryBuilder processInstanceIdQ = termQuery(PROCESS_INSTANCE_KEY, processInstanceId);
    final TermQueryBuilder flowNodeIdQ = termQuery(FLOW_NODE_ID, flowNodeId);

    final SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
        .query(constantScoreQuery(joinWithAnd(processInstanceIdQ, flowNodeIdQ)))
        .sort(LEVEL, SortOrder.ASC)
        .aggregation(getLevelsAggs())
        .size(1);
    if (flowNodeType != null) {
      sourceBuilder.postFilter(termQuery(TYPE, flowNodeType));
    }
    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate)
        .source(sourceBuilder);

    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

      final FlowNodeMetadataDto result = new FlowNodeMetadataDto();
      final FlowNodeInstanceEntity flowNodeInstance = getFlowNodeInstance(response);

      final Terms levelsAgg = response.getAggregations().get(LEVELS_AGG_NAME);
      if (levelsAgg != null && levelsAgg.getBuckets() != null && levelsAgg.getBuckets().size() > 0) {
        final Bucket bucketCurrentLevel = getBucketFromLevel(levelsAgg.getBuckets(),
            flowNodeInstance.getLevel());
        if (bucketCurrentLevel.getDocCount() == 1) {
          result.setInstanceMetadata(buildInstanceMetadata(flowNodeInstance));
          result.setFlowNodeInstanceId(flowNodeInstance.getId());
          //scenario 1-2
          result.setBreadcrumb(buildBreadcrumbForFlowNodeId(levelsAgg.getBuckets(), flowNodeInstance.getLevel()));
        } else {
          result.setInstanceCount(bucketCurrentLevel.getDocCount());
          result.setFlowNodeId(flowNodeInstance.getFlowNodeId());
          result.setFlowNodeType(flowNodeInstance.getType());
        }
      }

      return result;
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining metadata for flow node: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Bucket getBucketFromLevel(final List<? extends Bucket> buckets, final int level) {
    return buckets.stream().filter(b -> b.getKeyAsNumber().intValue() == level).findFirst()
        .get();
  }

  private TermsAggregationBuilder getLevelsAggs() {
    return terms(LEVELS_AGG_NAME)
        .field(LEVEL)
        .size(TERMS_AGG_SIZE)
        .order(BucketOrder.key(true))  //upper level first
        .subAggregation(
            topHits(LEVELS_TOP_HITS_AGG_NAME)     //select one instance per each level
                .size(1)
        );
  }

  private FlowNodeInstanceEntity getFlowNodeInstance(final SearchResponse response) {
    if (response.getHits().getTotalHits().value == 0) {
      throw new OperateRuntimeException("No data found for flow node instance.");
    }
    return fromSearchHit(
        response.getHits().getAt(0).getSourceAsString(), objectMapper, FlowNodeInstanceEntity.class);
  }

  private List<FlowNodeInstanceBreadcrumbEntryDto> buildBreadcrumbForFlowNodeId(
      final List<? extends Bucket> buckets, final int currentInstanceLevel) {
    if (buckets.size() == 0) {
      return new ArrayList<>();
    }
    final List<FlowNodeInstanceBreadcrumbEntryDto> breadcrumb = new ArrayList<>();
    final FlowNodeType firstBucketFlowNodeType = getFirstBucketFlowNodeType(buckets);
    if ((firstBucketFlowNodeType != null && firstBucketFlowNodeType.equals(FlowNodeType.MULTI_INSTANCE_BODY))
        || getBucketFromLevel(buckets, currentInstanceLevel).getDocCount() > 1) {
      for (Bucket levelBucket : buckets) {
        final TopHits levelTopHits = levelBucket.getAggregations().get(LEVELS_TOP_HITS_AGG_NAME);
        final Map<String, Object> instanceFields = levelTopHits.getHits().getAt(0).getSourceAsMap();
        if ((int) instanceFields.get(LEVEL) <= currentInstanceLevel) {
          breadcrumb.add(new FlowNodeInstanceBreadcrumbEntryDto(
              (String) instanceFields.get(FLOW_NODE_ID),
              FlowNodeType.valueOf((String) instanceFields.get(TYPE))
          ));
        }
      }
    }
    return breadcrumb;
  }

  private FlowNodeType getFirstBucketFlowNodeType(final List<? extends Bucket> buckets) {
    final TopHits topHits = buckets.get(0).getAggregations().get(LEVELS_TOP_HITS_AGG_NAME);
    if (topHits != null && topHits.getHits().getTotalHits().value > 0) {
      final String type = (String) topHits.getHits().getAt(0).getSourceAsMap().get(TYPE);
      if (type != null) {
        return FlowNodeType.valueOf(type);
      }
    }
    return null;
  }

  private FlowNodeInstanceMetadataDto buildInstanceMetadata(final FlowNodeInstanceEntity flowNodeInstance) {
    final EventEntity eventEntity = getEventEntity(flowNodeInstance.getId());
    final String[] calledProcessInstanceId = {null};
    final String[] calledProcessDefinitionName = {null};
    if (flowNodeInstance.getType().equals(FlowNodeType.CALL_ACTIVITY)) {
      findCalledProcessInstance(flowNodeInstance.getId(),
          sh -> {
            calledProcessInstanceId[0] = sh.getId();
            final Map<String, Object> source = sh.getSourceAsMap();
            String processName = (String)source.get(ListViewTemplate.PROCESS_NAME);
            if (processName == null) {
              processName = (String)source.get(ListViewTemplate.BPMN_PROCESS_ID);
            }
            calledProcessDefinitionName[0] = processName;
          });
    }
    return FlowNodeInstanceMetadataDto
        .createFrom(flowNodeInstance, eventEntity, calledProcessInstanceId[0],
            calledProcessDefinitionName[0]);
  }

  private void findCalledProcessInstance(final String flowNodeInstanceId,
      Consumer<SearchHit> processInstanceConsumer) {
    final TermQueryBuilder parentFlowNodeInstanceQ = termQuery(PARENT_FLOW_NODE_INSTANCE_KEY,
        flowNodeInstanceId);
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(listViewTemplate)
        .source(new SearchSourceBuilder().query(parentFlowNodeInstanceQ)
            .fetchSource(
                new String[]{ListViewTemplate.PROCESS_NAME, ListViewTemplate.BPMN_PROCESS_ID},
                null));
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value >= 1) {
        processInstanceConsumer.accept(response.getHits().getAt(0));
      }
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining parent process instance id for flow node instance: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private EventEntity getEventEntity(final String flowNodeInstanceId) {
    final EventEntity eventEntity;
    //request corresponding event and build cumulative metadata
    QueryBuilder query = constantScoreQuery(
        termQuery(EventTemplate.FLOW_NODE_INSTANCE_KEY, flowNodeInstanceId));
    final SearchRequest request = ElasticsearchUtil.createSearchRequest(eventTemplate)
        .source(new SearchSourceBuilder().query(query).sort(EventTemplate.ID));
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);
      if (response.getHits().getTotalHits().value >= 1) {
        eventEntity = fromSearchHit(response.getHits().getHits()[(int) (response.getHits().getTotalHits().value - 1)]
                .getSourceAsString(), objectMapper, EventEntity.class);
      } else {
        throw new NotFoundException(
            String.format("Could not find flow node instance event with id '%s'.",
                flowNodeInstanceId));
      }
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining metadata for flow node instance: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
    return eventEntity;
  }

  public Map<String, FlowNodeState> getFlowNodeStates(String processInstanceId) {
    final String latestFlowNodeAggName = "latestFlowNode";
    final String activeFlowNodesAggName = "activeFlowNodes";
    final String activeFlowNodesBucketsAggName = "activeFlowNodesBuckets";
    final String finishedFlowNodesAggName = "finishedFlowNodes";

    final ConstantScoreQueryBuilder query = constantScoreQuery(
        termQuery(PROCESS_INSTANCE_KEY, processInstanceId));

    final AggregationBuilder notCompletedFlowNodesAggs =
        filter(activeFlowNodesAggName, termsQuery(STATE, ACTIVE.name(), INCIDENT.name(), TERMINATED.name()))
        .subAggregation(
          terms(activeFlowNodesBucketsAggName)
          .field(FLOW_NODE_ID)
          .size(TERMS_AGG_SIZE)
          .subAggregation(
              topHits(latestFlowNodeAggName)
                  .sort(START_DATE, SortOrder.DESC)
                  .size(1)
                  .fetchSource(new String[]{STATE, TREE_PATH}, null)));

    final AggregationBuilder finishedFlowNodesAggs =
        filter(finishedFlowNodesAggName, termQuery(STATE, COMPLETED))
        .subAggregation(
          terms(FINISHED_FLOW_NODES_BUCKETS_AGG_NAME)
          .field(FLOW_NODE_ID)
          .size(TERMS_AGG_SIZE));

    final SearchRequest request = ElasticsearchUtil
        .createSearchRequest(flowNodeInstanceTemplate)
        .source(new SearchSourceBuilder()
            .query(query)
            .aggregation(notCompletedFlowNodesAggs)
            .aggregation(getIncidentsAgg())
            .aggregation(finishedFlowNodesAggs)
            .size(0));
    try {
      final SearchResponse response = esClient.search(request, RequestOptions.DEFAULT);

      Set<String> incidentPaths = new HashSet<>();
      processAggregation(response.getAggregations(), incidentPaths, new Boolean[]{false});

      Set<String> finishedFlowNodes = collectFinishedFlowNodes(
          response.getAggregations().get(finishedFlowNodesAggName));

      final Filter activeFlowNodesAgg = response.getAggregations().get(activeFlowNodesAggName);
      final Terms flowNodesAgg = activeFlowNodesAgg.getAggregations()
          .get(activeFlowNodesBucketsAggName);
      final Map<String, FlowNodeState> result = new HashMap<>();
      if (flowNodesAgg != null) {
        for (Bucket flowNode : flowNodesAgg.getBuckets()) {
          final Map<String, Object> lastFlowNodeFields = ((TopHits) flowNode.getAggregations()
              .get(latestFlowNodeAggName)).getHits()
              .getAt(0).getSourceAsMap();
          FlowNodeState flowNodeState = FlowNodeState.valueOf(
              lastFlowNodeFields.get(STATE).toString());
          if (flowNodeState.equals(ACTIVE) && incidentPaths
              .contains(lastFlowNodeFields.get(TREE_PATH))) {
            flowNodeState = INCIDENT;
          }
          result.put(flowNode.getKeyAsString(), flowNodeState);
        }
      }
      //add finished when needed
      for (String finishedFlowNodeId: finishedFlowNodes) {
        if (result.get(finishedFlowNodeId) == null) {
          result.put(finishedFlowNodeId, COMPLETED);
        }
      }
      return result;
    } catch (IOException e) {
      final String message = String.format(
          "Exception occurred, while obtaining states for instance flow nodes: %s",
          e.getMessage());
      throw new OperateRuntimeException(message, e);
    }
  }

  private Set<String> collectFinishedFlowNodes(final Filter finishedFlowNodes) {
    final Set<String> result = new HashSet<>();
    final List<? extends Bucket> buckets = ((Terms) finishedFlowNodes.getAggregations()
        .get(FINISHED_FLOW_NODES_BUCKETS_AGG_NAME))
        .getBuckets();
    if (buckets != null) {
      for (Bucket bucket : buckets) {
        result.add(bucket.getKeyAsString());
      }
    }
    return result;
  }

}
