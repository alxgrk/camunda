/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {gql} from 'apollo-boost';
import {Task} from 'modules/types';

import {
  taskWithVariables,
  taskWithoutVariables,
} from 'modules/mock-schema/mocks/variables';

interface GetVariables {
  task: {
    key: Task['key'];
    variables: Task['variables'];
  };
}

const GET_TASK_VARIABLES =
  process.env.NODE_ENV === 'test'
    ? gql`
        query GetTask($key: ID!) {
          task(key: $key) {
            variables
          }
        }
      `
    : gql`
        query GetTask($key: ID!) {
          task(key: $key) @client {
            variables
          }
        }
      `;

const mockTaskWithVariables = {
  request: {
    query: GET_TASK_VARIABLES,
    variables: {key: '0'},
  },
  result: {
    data: {
      task: taskWithVariables,
    },
  },
};

const mockTaskWithoutVariables = {
  request: {
    query: GET_TASK_VARIABLES,
    variables: {key: '1'},
  },
  result: {
    data: {
      task: taskWithoutVariables,
    },
  },
};

export type {GetVariables};
export {GET_TASK_VARIABLES, mockTaskWithVariables, mockTaskWithoutVariables};
