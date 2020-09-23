/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {
  render,
  screen,
  waitForElementToBeRemoved,
} from '@testing-library/react';

import {CollapsablePanelProvider} from 'modules/contexts/CollapsablePanelContext';
import {createMemoryHistory} from 'history';

import {mockProps} from './index.setup';
import {
  groupedWorkflowsMock,
  mockWorkflowStatistics,
  mockWorkflowInstances,
} from 'modules/testUtils';

import {DiagramPanel} from './index';
import {instancesDiagram} from 'modules/stores/instancesDiagram';
import {filters} from 'modules/stores/filters';
import PropTypes from 'prop-types';
import {rest} from 'msw';
import {mockServer} from 'modules/mockServer';

jest.mock('modules/utils/bpmn');

const Wrapper = ({children}) => {
  return <CollapsablePanelProvider>{children} </CollapsablePanelProvider>;
};
Wrapper.propTypes = {
  children: PropTypes.oneOfType([
    PropTypes.arrayOf(PropTypes.node),
    PropTypes.node,
  ]),
};

describe('DiagramPanel', () => {
  const historyMock = createMemoryHistory();

  beforeEach(() => {
    mockServer.use(
      rest.post(
        '/api/workflow-instances?firstResult=:firstResult&maxResults=:maxResults',
        (_, res, ctx) => res.once(ctx.json(mockWorkflowInstances))
      ),
      rest.get('/api/workflows/:workflowId/xml', (_, res, ctx) =>
        res.once(ctx.text(''))
      ),
      rest.get('/api/workflows/grouped', (_, res, ctx) =>
        res.once(ctx.json(groupedWorkflowsMock))
      ),
      rest.post('/api/workflow-instances/statistics', (_, res, ctx) =>
        res.once(ctx.json(mockWorkflowStatistics))
      )
    );
  });

  afterEach(() => {
    instancesDiagram.reset();
    filters.reset();
  });

  it('should render header', async () => {
    const locationMock = {
      pathname: '/instances',
      search:
        '?filter={%22active%22:true,%22incidents%22:true,%22version%22:%221%22,%22workflow%22:%22bigVarProcess%22}&name=%22Big%20variable%20process%22',
    };
    filters.setUrlParameters(historyMock, locationMock);
    await filters.init();

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });

    expect(screen.getByText('Big variable process')).toBeInTheDocument();
  });

  it('should show the loading indicator, when diagram is loading', async () => {
    const locationMock = {
      pathname: '/instances',
    };
    filters.setUrlParameters(historyMock, locationMock);
    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });
    instancesDiagram.fetchWorkflowXml(1);

    expect(screen.getByTestId('spinner')).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();

    await waitForElementToBeRemoved(screen.getByTestId('spinner'));

    expect(screen.getByTestId('diagram')).toBeInTheDocument();
  });

  it('should show an empty state message when no workflow is selected', async () => {
    const locationMock = {
      pathname: '/instances',
      search: '?filter={%22active%22:true,%22incidents%22:true}',
    };
    filters.setUrlParameters(historyMock, locationMock);

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });

    await filters.init();

    expect(
      screen.getByText('There is no Workflow selected.')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'To see a diagram, select a Workflow in the Filters panel.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });

  it('should show a message when no workflow version is selected', async () => {
    const locationMock = {
      pathname: '/instances',
      search:
        '?filter={%22active%22:true,%22incidents%22:true,%22version%22:%22all%22,%22workflow%22:%22bigVarProcess%22}&name=%22Big%20variable%20process%22',
    };
    filters.setUrlParameters(historyMock, locationMock);

    render(<DiagramPanel {...mockProps} />, {
      wrapper: Wrapper,
    });
    await filters.init();

    expect(
      screen.getByText(
        'There is more than one version selected for Workflow "Big variable process".'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('To see a diagram, select a single version.')
    ).toBeInTheDocument();

    expect(screen.queryByTestId('diagram')).not.toBeInTheDocument();
  });
});
