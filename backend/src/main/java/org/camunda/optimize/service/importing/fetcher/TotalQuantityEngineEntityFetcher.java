package org.camunda.optimize.service.importing.fetcher;

import org.camunda.optimize.dto.engine.CountDto;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionXmlEngineDto;
import org.camunda.optimize.service.exceptions.OptimizeException;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.ws.rs.client.Client;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.camunda.optimize.service.util.EngineConstantsUtil.INCLUDE_ONLY_FINISHED_INSTANCES;
import static org.camunda.optimize.service.util.EngineConstantsUtil.INDEX_OF_FIRST_RESULT;
import static org.camunda.optimize.service.util.EngineConstantsUtil.MAX_RESULTS_TO_RETURN;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_BY;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_ORDER_TYPE_ASCENDING;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_END_TIME;
import static org.camunda.optimize.service.util.EngineConstantsUtil.SORT_TYPE_ID;
import static org.camunda.optimize.service.util.EngineConstantsUtil.TRUE;

@Component
public class TotalQuantityEngineEntityFetcher extends EngineEntityFetcher {

  private Logger logger = LoggerFactory.getLogger(TotalQuantityEngineEntityFetcher.class);

  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private Client client;

  public List<HistoricActivityInstanceEngineDto> fetchHistoricActivityInstances(int indexOfFirstResult, int maxPageSize) {
    List<HistoricActivityInstanceEngineDto> entries;
    long requestStart = System.currentTimeMillis();
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getHistoricActivityInstanceEndpoint())
          .queryParam(SORT_BY, SORT_TYPE_END_TIME)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get(new GenericType<List<HistoricActivityInstanceEngineDto>>() {
          });
      long requestEnd = System.currentTimeMillis();
      logger.debug("Fetch of [HAI] took [{}] ms", requestEnd - requestStart);
    } catch (RuntimeException e) {
      logError("Could not fetch historic activity instances from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }

    return entries;
  }

  public Integer fetchHistoricActivityInstanceCount() throws OptimizeException {
    CountDto count;
    try {
      count = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getHistoricActivityInstanceCountEndpoint())
          .queryParam(INCLUDE_ONLY_FINISHED_INSTANCES, TRUE)
          .request()
          .acceptEncoding(UTF8)
          .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch historic activity instance count from engine. Please check the connection!", e);
    }

    return count.getCount();
  }

  public List<ProcessDefinitionXmlEngineDto> fetchProcessDefinitionXmls(int indexOfFirstResult, int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries = fetchProcessDefinitions(indexOfFirstResult, maxPageSize);
    return fetchAllXmls(entries);
  }

  private List<ProcessDefinitionXmlEngineDto> fetchAllXmls(List<ProcessDefinitionEngineDto> entries) {
    List<ProcessDefinitionXmlEngineDto> xmls;
    try {
      xmls = new ArrayList<>(entries.size());
      for (ProcessDefinitionEngineDto engineDto : entries) {
        ProcessDefinitionXmlEngineDto xml = client
            .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
            .path(configurationService.getProcessDefinitionXmlEndpoint(engineDto.getId()))
            .request(MediaType.APPLICATION_JSON)
            .acceptEncoding(UTF8)
            .get(ProcessDefinitionXmlEngineDto.class);
        xmls.add(xml);
      }
    } catch (RuntimeException e) {
      logError("Could not fetch process definition xmls from engine. Please check the connection!", e);
      xmls = Collections.emptyList();
    }
    return xmls;
  }

  public List<ProcessDefinitionEngineDto> fetchProcessDefinitions(int indexOfFirstResult, int maxPageSize) {
    List<ProcessDefinitionEngineDto> entries;
    try {
      entries = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getProcessDefinitionEndpoint())
          .queryParam(INDEX_OF_FIRST_RESULT, indexOfFirstResult)
          .queryParam(MAX_RESULTS_TO_RETURN, maxPageSize)
          .queryParam(SORT_BY, SORT_TYPE_ID)
          .queryParam(SORT_ORDER, SORT_ORDER_TYPE_ASCENDING)
          .request(MediaType.APPLICATION_JSON)
          .acceptEncoding(UTF8)
          .get(new GenericType<List<ProcessDefinitionEngineDto>>() {
          });
    } catch (RuntimeException e) {
      logError("Could not fetch process definitions from engine. Please check the connection!", e);
      entries = Collections.emptyList();
    }

    return entries;
  }

  public Integer fetchProcessDefinitionCount() throws OptimizeException {
    CountDto count;
    try {
      count = client
          .target(configurationService.getEngineRestApiEndpointOfCustomEngine())
          .path(configurationService.getProcessDefinitionCountEndpoint())
          .request()
          .acceptEncoding(UTF8)
          .get(CountDto.class);
    } catch (RuntimeException e) {
      throw new OptimizeException("Could not fetch process definition count from engine. Please check the connection!", e);
    }
    return count.getCount();
  }

}
