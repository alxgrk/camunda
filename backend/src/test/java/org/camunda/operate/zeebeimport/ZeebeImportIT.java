package org.camunda.operate.zeebeimport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.camunda.operate.entities.ActivityInstanceEntity;
import org.camunda.operate.entities.ActivityState;
import org.camunda.operate.entities.IncidentEntity;
import org.camunda.operate.entities.IncidentState;
import org.camunda.operate.entities.WorkflowInstanceEntity;
import org.camunda.operate.entities.WorkflowInstanceState;
import org.camunda.operate.es.reader.WorkflowInstanceReader;
import org.camunda.operate.es.schema.templates.WorkflowInstanceTemplate;
import org.camunda.operate.rest.dto.listview.ListViewRequestDto;
import org.camunda.operate.rest.dto.listview.ListViewResponseDto;
import org.camunda.operate.util.IdTestUtil;
import org.camunda.operate.util.MockMvcTestRule;
import org.camunda.operate.util.OperateZeebeIntegrationTest;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebeimport.cache.WorkflowCache;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.internal.util.reflection.FieldSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import io.zeebe.client.ZeebeClient;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.test.util.record.RecordingExporter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.rest.WorkflowInstanceRestService.WORKFLOW_INSTANCE_URL;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ZeebeImportIT extends OperateZeebeIntegrationTest {

  @Autowired
  private WorkflowInstanceReader workflowInstanceReader;

  @Autowired
  private WorkflowCache workflowCache;

  @Autowired
  private ZeebeESImporter zeebeESImporter;

  private ZeebeClient zeebeClient;

  private OffsetDateTime testStartTime;

  @Rule
  public MockMvcTestRule mockMvcTestRule = new MockMvcTestRule();

  private MockMvc mockMvc;

  @Before
  public void init() {
    super.before();
    testStartTime = OffsetDateTime.now();
    zeebeClient = super.getClient();
    mockMvc = mockMvcTestRule.getMockMvc();
    try {
      FieldSetter.setField(workflowCache, WorkflowCache.class.getDeclaredField("zeebeClient"), super.getClient());
    } catch (NoSuchFieldException e) {
      fail("Failed to inject ZeebeClient into some of the beans");
    }
  }

  @After
  public void after() {
    super.after();
  }

  @Test
  public void testWorkflowNameAndVersionAreLoaded() {
    // having
    String processId = "demoProcess";
    final String workflowId = ZeebeTestUtil.deployWorkflow(zeebeClient, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load workflow instance index, then deployment
    processAllEvents(10, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);
    processAllEvents(2, ZeebeESImporter.ImportValueType.DEPLOYMENT);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
  }

  protected void processAllEvents(int expectedMinEventsCount, ZeebeESImporter.ImportValueType workflowInstance) {
    elasticsearchTestRule.processAllEvents(expectedMinEventsCount, workflowInstance);
  }

  @Test
  public void testIncidentCreatesWorkflowInstance() {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load incident and then workflow instance events
    processAllEvents(1, ZeebeESImporter.ImportValueType.INCIDENT);
    processAllEvents(8, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getWorkflowId()).isEqualTo(workflowId);
    assertThat(workflowInstanceEntity.getWorkflowName()).isEqualTo("Demo process");
    assertThat(workflowInstanceEntity.getWorkflowVersion()).isEqualTo(1);
    assertThat(workflowInstanceEntity.getId()).isEqualTo(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getKey()).isEqualTo(workflowInstanceKey);
    assertThat(workflowInstanceEntity.getState()).isEqualTo(WorkflowInstanceState.ACTIVE);
    assertThat(workflowInstanceEntity.getEndDate()).isNull();
    assertThat(workflowInstanceEntity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(workflowInstanceEntity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());

    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getActivityId()).isEqualTo(activityId);
    assertThat(incidentEntity.getActivityInstanceId()).isNotEmpty();
    assertThat(incidentEntity.getErrorMessage()).isNotEmpty();
    assertThat(incidentEntity.getErrorType()).isNotEmpty();
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.ACTIVE);

    //assert activity fields
    assertThat(workflowInstanceEntity.getActivities().size()).isEqualTo(2);
    assertStartActivityCompleted(workflowInstanceEntity.getActivities().get(0));
    assertActivityIsInIncidentState(workflowInstanceEntity.getActivities().get(1), "taskA");
  }

  @Test
  public void testOnlyIncidentIsLoaded() throws Exception {
    // having
    String activityId = "taskA";
    String processId = "demoProcess";
    final String workflowId = deployWorkflow("demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    //create an incident
    ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    elasticsearchTestRule.refreshIndexesInElasticsearch();

    //when
    //1st load incident and then workflow instance events
    processAllEvents(1, ZeebeESImporter.ImportValueType.INCIDENT);

    assertListViewResponse();
    //if nothing is returned in list view - there is no way to access the workflow instance, no need to check other queries

  }

  protected void assertListViewResponse() throws Exception {
    ListViewRequestDto listViewRequest = TestUtil.createGetAllWorkflowInstancesQuery();
    MockHttpServletRequestBuilder request = post(query(0, 100))
      .content(mockMvcTestRule.json(listViewRequest))
      .contentType(mockMvcTestRule.getContentType());

    MvcResult mvcResult = mockMvc
      .perform(request)
      .andExpect(status().isOk())
      .andExpect(content().contentType(mockMvcTestRule.getContentType()))
      .andReturn();

    //check that nothing is returned
    final ListViewResponseDto listViewResponse = mockMvcTestRule.fromResponse(mvcResult, new TypeReference<ListViewResponseDto>() {
    });
    assertThat(listViewResponse.getTotalCount()).isEqualTo(0);
    assertThat(listViewResponse.getWorkflowInstances()).hasSize(0);
  }

  @Test
  public void testIncidentDeletedAfterActivityCompleted() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
      .done();
    final String workflowId = deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");
    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);

    setJobWorker(ZeebeTestUtil.completeTask(getClient(), activityId, getWorkerName(), "{}"));

    processAllEvents(20, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);
    processAllEvents(2, ZeebeESImporter.ImportValueType.INCIDENT);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);
    assertThat(workflowInstanceEntity.getActivities()).filteredOn(ai -> ai.getId().equals(incidentEntity.getActivityInstanceId()))
      .hasSize(1)
      .extracting(WorkflowInstanceTemplate.STATE).containsOnly(ActivityState.COMPLETED);

  }

  protected long getOnlyIncidentKey() {
    final List<Record<IncidentRecordValue>> incidents = RecordingExporter.incidentRecords(IncidentIntent.CREATED)
      .collect(Collectors.toList());
    assertThat(incidents).hasSize(1);
    return incidents.get(0).getKey();
  }

  @Test
  public void testIncidentDeletedAfterActivityTerminated() {
    // having
    String activityId = "taskA";


    String processId = "demoProcess";
    final BpmnModelInstance modelInstance =
      Bpmn.createExecutableProcess(processId)
        .startEvent("start")
        .serviceTask(activityId).zeebeTaskType(activityId)
        .endEvent()
        .done();
    final String workflowId = deployWorkflow(modelInstance, "demoProcess_v_1.bpmn");
    final long workflowInstanceKey = ZeebeTestUtil.startWorkflowInstance(zeebeClient, processId, "{\"a\": \"b\"}");

    //create an incident
    final Long jobKey = ZeebeTestUtil.failTask(getClient(), activityId, getWorkerName(), 3, "Some error");

    final long incidentKey = getOnlyIncidentKey();

    //when update retries
    ZeebeTestUtil.resolveIncident(zeebeClient, jobKey, incidentKey);

    ZeebeTestUtil.cancelWorkflowInstance(getClient(), workflowInstanceKey);

    processAllEvents(20, ZeebeESImporter.ImportValueType.WORKFLOW_INSTANCE);
    processAllEvents(2, ZeebeESImporter.ImportValueType.INCIDENT);

    //then
    final WorkflowInstanceEntity workflowInstanceEntity = workflowInstanceReader.getWorkflowInstanceById(IdTestUtil.getId(workflowInstanceKey));
    assertThat(workflowInstanceEntity.getIncidents().size()).isEqualTo(1);
    IncidentEntity incidentEntity = workflowInstanceEntity.getIncidents().get(0);
    assertThat(incidentEntity.getState()).isEqualTo(IncidentState.RESOLVED);
    assertThat(workflowInstanceEntity.getActivities()).filteredOn(ai -> ai.getId().equals(incidentEntity.getActivityInstanceId()))
      .hasSize(1)
      .extracting(WorkflowInstanceTemplate.STATE).containsOnly(ActivityState.TERMINATED);

  }

  @Test
  public void testPartitionIds() {
    final Set<Integer> operatePartitions = zeebeESImporter.getPartitionIds();
    final int zeebePartitionsCount = zeebeClient.newTopologyRequest().send().join().getPartitionsCount();
    assertThat(operatePartitions).hasSize(zeebePartitionsCount);
    assertThat(operatePartitions).allMatch(id -> id < zeebePartitionsCount && id >= 0);
  }

  private void assertStartActivityCompleted(ActivityInstanceEntity startActivity) {
    assertThat(startActivity.getActivityId()).isEqualTo("start");
    assertThat(startActivity.getState()).isEqualTo(ActivityState.COMPLETED);
    assertThat(startActivity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(startActivity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
    assertThat(startActivity.getEndDate()).isAfterOrEqualTo(startActivity.getStartDate());
    assertThat(startActivity.getEndDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private void assertActivityIsInIncidentState(ActivityInstanceEntity activity, String activityId) {
    assertThat(activity.getActivityId()).isEqualTo(activityId);
    assertThat(activity.getState()).isEqualTo(ActivityState.INCIDENT);
    assertThat(activity.getStartDate()).isAfterOrEqualTo(testStartTime);
    assertThat(activity.getStartDate()).isBeforeOrEqualTo(OffsetDateTime.now());
  }

  private String query(int firstResult, int maxResults) {
    return String.format("%s?firstResult=%d&maxResults=%d", WORKFLOW_INSTANCE_URL, firstResult, maxResults);
  }

}