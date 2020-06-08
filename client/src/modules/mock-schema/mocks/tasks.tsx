/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Task} from 'modules/types';

const tasks: ReadonlyArray<Task> = [
  {
    key: '0',
    name: 'name',
    workflowName: 'workflowName',

    creationTime: '2020-05-28 10:11:12',
    completionTime: new Date().toISOString(),
    assignee: {
      username: 'Demo',
      firstname: 'Demo',
      lastname: 'user',
    },
    variables: [],
    taskState: 'COMPLETED',
  },
  {
    key: '1',
    name: 'name',
    workflowName: 'workflowName',
    creationTime: '2020-05-29 13:14:15',
    completionTime: new Date().toISOString(),
    assignee: {
      username: 'mustermann',
      firstname: 'Otto',
      lastname: 'Mustermann',
    },
    variables: [
      {name: 'myVar', value: '0001'},
      {name: 'isCool', value: 'yes'},
    ],
    taskState: 'CREATED',
  },
  {
    key: '2',
    name: 'name',
    workflowName: 'workflowName',
    creationTime: '2020-05-30 16:17:18',
    completionTime: new Date().toISOString(),
    assignee: null,
    variables: [],
    taskState: 'CREATED',
  },
];

export {tasks};
