/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';
import {
  mockBatchOperations,
  mockGroupedProcesses,
  mockProcessInstances,
  mockProcessInstancesWithOperationError,
  mockProcessXml,
  mockStatistics,
  mockResponses,
} from '../mocks/processes.mocks';

test.describe('processes page', () => {
  for (const theme of ['light', 'dark']) {
    test(`empty page - ${theme}`, async ({page, commonPage, processesPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          batchOperations: [],
          groupedProcesses: mockGroupedProcesses,
          statistics: [],
          processXml: '',
          processInstances: {
            processInstances: [],
            totalCount: 0,
          },
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`error page - ${theme}`, async ({page, commonPage, processesPage}) => {
      await commonPage.changeTheme(theme);

      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isFiltersCollapsed: true,
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          process: 'bigVarProcess',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and one flow node selected - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
          process: 'eventSubprocessProcess',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.selectFlowNode('Event Subprocess task');

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and operations panel expanded - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          completed: 'true',
          canceled: 'true',
          process: 'eventSubprocessProcess',
          version: '1',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await expect(page).toHaveScreenshot();
    });

    test(`optional filters visible (part 1) - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Variable');
      await processesPage.displayOptionalFilter('Error Message');
      await processesPage.displayOptionalFilter('Operation Id');
      await processesPage.operationIdFilter.type('aaa');
      await expect(page.getByText('Id has to be a UUID')).toBeVisible();
      await expect(page).toHaveScreenshot();
    });

    test(`optional filters visible (part 2) - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);
      await page.addInitScript(() => {
        window.localStorage.setItem(
          'panelStates',
          JSON.stringify({
            isOperationsCollapsed: false,
          }),
        );
      }, theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Parent Process Instance Key');
      await processesPage.displayOptionalFilter('Process Instance Key(s)');
      await processesPage.displayOptionalFilter('Failed job but retries left');
      await processesPage.displayOptionalFilter('End Date Range');

      await expect(page).toHaveScreenshot();
    });

    test(`data table toolbar visible - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstances,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
        },
        options: {
          waitUntil: 'networkidle',
        },
      });

      await page.getByRole('columnheader', {name: 'Select all rows'}).click();

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data and active batchOperationId filter - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstancesWithOperationError,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
        },

        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Operation Id');
      await processesPage.operationIdFilter.type(
        'bf547ac3-9a35-45b9-ab06-b80b43785153',
      );

      await expect(page.getByLabel('Sort by Operation State')).toBeInViewport();

      await expect(page).toHaveScreenshot();
    });

    test(`filled with data, active batchOperationId filter and error message expanded - ${theme}`, async ({
      page,
      commonPage,
      processesPage,
    }) => {
      await commonPage.changeTheme(theme);

      await page.route(
        /^.*\/api.*$/i,
        mockResponses({
          groupedProcesses: mockGroupedProcesses,
          batchOperations: mockBatchOperations,
          processInstances: mockProcessInstancesWithOperationError,
          statistics: mockStatistics,
          processXml: mockProcessXml,
        }),
      );

      await processesPage.navigateToProcesses({
        searchParams: {
          active: 'true',
          incidents: 'true',
          batchOperationId: 'bf547ac3-9a35-45b9-ab06-b80b43785153',
        },

        options: {
          waitUntil: 'networkidle',
        },
      });

      await processesPage.displayOptionalFilter('Operation Id');
      await processesPage.operationIdFilter.type(
        'bf547ac3-9a35-45b9-ab06-b80b43785153',
      );

      const errorRow = page.getByRole('row', {name: '6755399441062827'});

      await expect(errorRow).toBeInViewport();

      await errorRow.getByRole('button', {name: 'Expand current row'}).click();

      await expect(
        page.getByText('Batch Operation Error Message'),
      ).toBeInViewport();

      await expect(page).toHaveScreenshot();
    });
  }
});
