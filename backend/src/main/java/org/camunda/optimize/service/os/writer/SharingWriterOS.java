/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.os.writer;

import jakarta.ws.rs.NotFoundException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.query.sharing.DashboardShareRestDto;
import org.camunda.optimize.dto.optimize.query.sharing.ReportShareRestDto;
import org.camunda.optimize.service.db.writer.SharingWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.os.OptimizeOpensearchClient;
import org.camunda.optimize.service.util.IdGenerator;
import org.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch.core.DeleteResponse;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.opensearch.client.opensearch.core.IndexResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DASHBOARD_SHARE_INDEX_NAME;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.REPORT_SHARE_INDEX_NAME;


@AllArgsConstructor
@Component
@Slf4j
@Conditional(OpenSearchCondition.class)
public class SharingWriterOS implements SharingWriter {

  private final OptimizeOpensearchClient osClient;

  public ReportShareRestDto saveReportShare(final ReportShareRestDto createSharingDto) {
    log.debug("Writing new report share to Opensearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);

    final IndexRequest.Builder<ReportShareRestDto> indexRequestBuilder =
      new IndexRequest.Builder<ReportShareRestDto>()
      .index(REPORT_SHARE_INDEX_NAME) // TODO Check that prefix works
      .id(createSharingDto.getId())
      .document(createSharingDto);

    IndexResponse indexResponse = osClient.index(indexRequestBuilder);

    if (!indexResponse.result().equals(Result.Created)) {
      String message = "Could not write report share to Opensearch.";
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }

    log.debug("report share with id [{}] for resource [{}] has been created", id, createSharingDto.getReportId());
    return createSharingDto;
  }

  public DashboardShareRestDto saveDashboardShare(final DashboardShareRestDto createSharingDto) {
    log.debug("Writing new dashboard share to Opensearch");
    String id = IdGenerator.getNextId();
    createSharingDto.setId(id);

    final IndexRequest.Builder<DashboardShareRestDto> indexRequestBuilder =
      new IndexRequest.Builder<DashboardShareRestDto>()
        .index(DASHBOARD_SHARE_INDEX_NAME) // TODO Check that prefix works
        .id(createSharingDto.getId())
        .document(createSharingDto);

    IndexResponse indexResponse = osClient.index(indexRequestBuilder);

    if (!indexResponse.result().equals(Result.Created)) {
      String message = "Could not write dashboard share to Opensearch";
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }

    log.debug(
      "dashboard share with id [{}] for resource [{}] has been created",
      id,
      createSharingDto.getDashboardId()
    );
    return createSharingDto;
  }

  public void updateDashboardShare(final DashboardShareRestDto updatedShare) {
    String id = updatedShare.getId();

    final IndexRequest.Builder<DashboardShareRestDto> indexRequestBuilder =
      new IndexRequest.Builder<DashboardShareRestDto>()
        .index(DASHBOARD_SHARE_INDEX_NAME) // TODO Check that prefix works
        .id(updatedShare.getId())
        .document(updatedShare);

    IndexResponse indexResponse = osClient.index(indexRequestBuilder);

    if (!indexResponse.result().equals(Result.Created) &&
        !indexResponse.result().equals(Result.Updated)) {
      String message = String.format(
        "Was not able to update dashboard share with id [%s] for resource [%s].",
        id,
        updatedShare.getDashboardId()
      );
      log.error(message);
      throw new OptimizeRuntimeException(message);
    }
    log.debug("dashboard share with id [{}] for resource [{}] has been updated", id, updatedShare.getDashboardId());
  }

  public void deleteReportShare(final String shareId) {
    log.debug("Deleting report share with id [{}]", shareId);
    final DeleteResponse deleteResponse = osClient.delete(
      REPORT_SHARE_INDEX_NAME,
      shareId
    );

    if (!deleteResponse.result().equals(Result.Deleted)) {
      String message =
        String.format("Could not delete report share with id [%s]. Report share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }

  public void deleteDashboardShare(final String shareId) {
    log.debug("Deleting dashboard share with id [{}]", shareId);
    final DeleteResponse deleteResponse = osClient.delete(
      DASHBOARD_SHARE_INDEX_NAME,
      shareId
    );

    if (!deleteResponse.result().equals(Result.Deleted)) {
      String message =
        String.format("Could not delete dashboard share with id [%s]. Dashboard share does not exist." +
                        "Maybe it was already deleted by someone else?", shareId);
      log.error(message);
      throw new NotFoundException(message);
    }
  }
}
