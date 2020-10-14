/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {get, post, put, del} from 'request';

export async function loadProcesses() {
  const response = await get('api/eventBasedProcess');
  return await response.json();
}

export async function loadProcess(id) {
  const response = await get('api/eventBasedProcess/' + id);
  return await response.json();
}

export async function removeProcess(id) {
  return await del(`api/eventBasedProcess/${id}`);
}

export async function publish(id) {
  return await post(`api/eventBasedProcess/${id}/_publish`);
}

export async function cancelPublish(id) {
  return await post(`api/eventBasedProcess/${id}/_cancelPublish`);
}

export async function createProcess(payload) {
  const response = await post('api/eventBasedProcess', payload);
  const json = await response.json();

  return json.id;
}

export async function updateProcess(id, name, xml, mappings, eventSources) {
  return await put('api/eventBasedProcess/' + id, {name, xml, mappings, eventSources});
}

export async function loadEvents(body, searchTerm, sorting) {
  const query = {};
  if (searchTerm) {
    query.searchTerm = searchTerm;
  }

  if (sorting) {
    query.sortBy = sorting.by;
    query.sortOrder = sorting.order;
  }

  const response = await post('api/event/count', body, {query});
  return await response.json();
}

export async function getUsers(id) {
  const response = await get(`api/eventBasedProcess/${id}/role`);
  return await response.json();
}

export async function updateUsers(id, newUsers) {
  return await put(`api/eventBasedProcess/${id}/role`, newUsers);
}

export async function getCleanedMappings(body) {
  const response = await post('api/eventBasedProcess/_mappingCleanup', body);
  return await response.json();
}

export function isNonTimerEvent(node) {
  if (!node?.$instanceOf('bpmn:Event')) {
    return false;
  }

  if (node.eventDefinitions) {
    return !node.eventDefinitions[0].$instanceOf('bpmn:TimerEventDefinition');
  }

  return true;
}
