package org.camunda.optimize.service.util.configuration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Period;

@JsonIgnoreProperties(ignoreUnknown = false)
public class ProcessDefinitionCleanupConfiguration {
  @JsonProperty("ttl")
  private Period ttl;
  @JsonProperty("processDataCleanupMode")
  private CleanupMode processDataCleanupMode;

  protected ProcessDefinitionCleanupConfiguration() {
  }

  public ProcessDefinitionCleanupConfiguration(Period ttl) {
    this(ttl, null);
  }

  public ProcessDefinitionCleanupConfiguration(CleanupMode processDataCleanupMode) {
    this(null, processDataCleanupMode);
  }

  public ProcessDefinitionCleanupConfiguration(Period ttl, CleanupMode processDataCleanupMode) {
    this.ttl = ttl;
    this.processDataCleanupMode = processDataCleanupMode;
  }

  public Period getTtl() {
    return ttl;
  }

  public CleanupMode getProcessDataCleanupMode() {
    return processDataCleanupMode;
  }
}
