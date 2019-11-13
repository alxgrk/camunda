/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.job.importing;

import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.service.es.job.ElasticsearchImportJob;
import org.camunda.optimize.service.es.writer.CompletedProcessInstanceWriter;

import java.util.List;

public class CompletedProcessInstanceElasticsearchImportJob extends ElasticsearchImportJob<ProcessInstanceDto> {

  private CompletedProcessInstanceWriter completedProcessInstanceWriter;

  public CompletedProcessInstanceElasticsearchImportJob(CompletedProcessInstanceWriter
                                                          completedProcessInstanceWriter, Runnable callback) {
    super(callback);
    this.completedProcessInstanceWriter = completedProcessInstanceWriter;
  }

  @Override
  protected void persistEntities(List<ProcessInstanceDto> newOptimizeEntities) throws Exception {
    completedProcessInstanceWriter.importProcessInstances(newOptimizeEntities);
  }
}
