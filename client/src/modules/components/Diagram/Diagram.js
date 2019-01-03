import React from 'react';
import PropTypes from 'prop-types';
import BPMNViewer from 'bpmn-js/lib/NavigatedViewer';
import {isEqual} from 'lodash';
import {themed} from 'modules/theme';
import {
  ACTIVITY_STATE,
  FLOW_NODE_STATE_OVERLAY_ID,
  STATISTICS_OVERLAY_ID
} from 'modules/constants';
import incidentIcon from 'modules/components/Icon/diagram-badge-single-instance-incident.svg';
import activeIcon from 'modules/components/Icon/diagram-badge-single-instance-active.svg';
import completedLightIcon from 'modules/components/Icon/diagram-badge-single-instance-completed-light.svg';
import completedDarkIcon from 'modules/components/Icon/diagram-badge-single-instance-completed-dark.svg';
import canceledLightIcon from 'modules/components/Icon/diagram-badge-single-instance-canceled-light.svg';
import canceledDarkIcon from 'modules/components/Icon/diagram-badge-single-instance-canceled-dark.svg';
import * as Styled from './styled';
import DiagramControls from './DiagramControls';
import {getDiagramColors} from './service';

const iconMap = {
  [ACTIVITY_STATE.INCIDENT]: {
    light: incidentIcon,
    dark: incidentIcon
  },
  [ACTIVITY_STATE.ACTIVE]: {
    light: activeIcon,
    dark: activeIcon
  },
  [ACTIVITY_STATE.COMPLETED]: {
    light: completedLightIcon,
    dark: completedDarkIcon
  },
  [ACTIVITY_STATE.TERMINATED]: {
    light: canceledLightIcon,
    dark: canceledDarkIcon
  }
};

class Diagram extends React.Component {
  static propTypes = {
    theme: PropTypes.string.isRequired,
    definitions: PropTypes.object.isRequired,
    // callback function called when flowNodesDetails is ready
    onDiagramLoaded: PropTypes.func,
    clickableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectableFlowNodes: PropTypes.arrayOf(PropTypes.string),
    selectedFlowNode: PropTypes.string,
    onFlowNodeSelected: PropTypes.func,
    flowNodeStateOverlays: PropTypes.arrayOf(
      PropTypes.shape({
        id: PropTypes.string.isRequired,
        state: PropTypes.oneOf(Object.keys(ACTIVITY_STATE)).isRequired
      })
    ),
    flowNodesStatistics: PropTypes.arrayOf(
      PropTypes.shape({
        activityId: PropTypes.string.isRequired,
        active: PropTypes.number,
        incidents: PropTypes.number,
        completed: PropTypes.number,
        canceled: PropTypes.number
      })
    )
  };

  constructor(props) {
    super(props);
    this.Viewer = null;
    this.myRef = React.createRef();
  }

  state = {
    isViewerLoaded: false
  };

  componentDidMount() {
    this.initViewer();
  }

  componentDidUpdate({
    theme: prevTheme,
    definitions: prevDefinitions,
    selectedFlowNode,
    flowNodeStateOverlays: prevFlowNodeStateOverlays,
    flowNodesStatistics: prevflowNodesStatistics
  }) {
    const hasNewDefinitions = this.props.definitions !== prevDefinitions;
    const hasNewTheme = this.props.theme !== prevTheme;

    if (hasNewTheme || hasNewDefinitions) {
      return this.resetViewer();
    }

    const hasSelectedFlowNodeChanged =
      this.props.selectedFlowNode !== selectedFlowNode;

    // In case only the selectedFlowNode changed.
    // This also means that the Viewer is already initiated so we can safely
    // call this.handleSelectedFlowNode.
    if (this.state.isViewerLoaded && hasSelectedFlowNodeChanged) {
      this.handleSelectedFlowNode(
        this.props.selectedFlowNode,
        selectedFlowNode
      );
    }

    // Clear overlays of type flow-node-state and add new ones
    if (!isEqual(this.props.flowNodeStateOverlays, prevFlowNodeStateOverlays)) {
      this.clearOverlaysByType(FLOW_NODE_STATE_OVERLAY_ID);
      this.props.flowNodeStateOverlays.forEach(this.addFlowNodeStateOverlay);
    }

    // Clear overlays for statistics
    if (!isEqual(prevflowNodesStatistics, this.props.flowNodesStatistics)) {
      this.clearOverlaysByType(STATISTICS_OVERLAY_ID);
      this.props.flowNodesStatistics.forEach(this.addSatisticOverlays);
    }
  }

  // 1- state.isViewerLoaded => true
  // 2- canvas.resized() && canvas.zoom('fit-viewport', 'auto')
  handleDiagramLoad = e => {
    if (e) {
      return console.log('Error rendering diagram:', e);
    }
    this.setState({
      isViewerLoaded: true
    });
    this.handleZoomReset();

    // in case onDiagramLoaded callback function is provided
    // call it with flowNodesDetails
    if (typeof this.props.onDiagramLoaded === 'function') {
      this.props.onDiagramLoaded();
    }

    if (this.props.selectableFlowNodes) {
      this.props.onFlowNodeSelected && this.addElementClickListeners();
      this.handleSelectableFlowNodes(this.props.selectableFlowNodes);
    }

    if (this.props.selectedFlowNode) {
      this.handleSelectedFlowNode(this.props.selectedFlowNode);
    }

    if (this.props.flowNodeStateOverlays) {
      this.props.flowNodeStateOverlays.forEach(this.addFlowNodeStateOverlay);
    }
  };

  initViewer = () => {
    // colors config for bpmnRenderer
    this.Viewer = new BPMNViewer({
      container: this.myRef.current,
      bpmnRenderer: getDiagramColors(this.props.theme)
    });

    this.Viewer.importDefinitions(
      this.props.definitions,
      this.handleDiagramLoad
    );
  };

  detachViewer = () => {
    // detach Viewer
    if (this.Viewer) {
      this.Viewer.detach();
      this.setState({
        isViewerLoaded: false
      });
    }
  };

  resetViewer = () => {
    this.detachViewer();
    this.initViewer();
  };

  containerRef = node => {
    this.containerNode = node;
  };

  handleZoom = step => {
    this.Viewer.get('zoomScroll').stepZoom(step);
  };

  handleZoomIn = () => {
    this.handleZoom(0.1);
  };

  handleZoomOut = () => {
    this.handleZoom(-0.1);
  };

  handleZoomReset = () => {
    const canvas = this.Viewer.get('canvas');
    canvas.resized();
    canvas.zoom('fit-viewport', 'auto');
  };

  addMarker = (id, className) => {
    const canvas = this.Viewer.get('canvas');
    const elementRegistry = this.Viewer.get('elementRegistry');
    canvas.addMarker(id, className);
    const gfx = elementRegistry.getGraphics(id).querySelector('.djs-outline');
    gfx.setAttribute('rx', '14px');
    gfx.setAttribute('ry', '14px');
  };

  removeMarker = (id, className) => {
    const canvas = this.Viewer.get('canvas');
    canvas.removeMarker(id, className);
  };

  handleSelectableFlowNodes = selectableFlowNodes => {
    selectableFlowNodes.forEach(id => {
      this.addMarker(id, 'op-selectable');
    });
  };

  handleSelectedFlowNode = (selectedFlowNode, prevSelectedFlowNode) => {
    // clear previously selected flow node marker is there is one
    if (prevSelectedFlowNode) {
      this.removeMarker(prevSelectedFlowNode, 'op-selected');
    }

    // add marker for newly selected flow node if there is one
    if (selectedFlowNode) {
      this.addMarker(selectedFlowNode, 'op-selected');
    }
  };

  handleElementClick = ({element = {}}) => {
    const {selectedFlowNode} = this.props;
    const isSelectableElement =
      this.props.selectableFlowNodes.filter(id => id === element.id).length > 0;

    // Only select the flownode if it's selectable and if it's not already selected.
    if (isSelectableElement && element.id !== selectedFlowNode) {
      return this.props.onFlowNodeSelected(element.id);
    } else if (selectedFlowNode) {
      this.props.onFlowNodeSelected(null);
    }
  };

  addElementClickListeners = () => {
    const eventBus = this.Viewer.get('eventBus');
    eventBus.on('element.click', this.handleElementClick);
  };

  addFlowNodeStateOverlay = ({id, state}) => {
    // Create an overlay dom element as an img
    const img = document.createElement('img');
    Object.assign(img, {
      src: iconMap[state][this.props.theme],
      width: 24,
      height: 24,
      // makes the icon non dragable and blocks click listener
      style: 'pointer-events: none;'
    });

    // Add the created overlay to the diagram.
    // Note that we also pass the type 'flow-node-state' to
    // the overlay to be able to clear all overlays of such type. (cf. clearOverlaysByType)
    this.Viewer.get('overlays').add(id, FLOW_NODE_STATE_OVERLAY_ID, {
      position: {
        bottom: 17,
        left: -7
      },
      html: img
    });
  };

  addSatisticOverlays = statistic => {
    const states = ['active', 'incidents', 'canceled', 'completed'];

    const positions = {
      active: {
        bottom: 9,
        left: 0
      },
      incidents: {
        bottom: 9,
        right: 0
      },
      canceled: {
        top: -16,
        left: 0
      },
      completed: {
        bottom: 1,
        left: 17
      }
    };

    const icons = {
      active: iconMap[ACTIVITY_STATE.ACTIVE],
      incidents: iconMap[ACTIVITY_STATE.INCIDENT],
      canceled: iconMap[ACTIVITY_STATE.TERMINATED],
      completed: iconMap[ACTIVITY_STATE.COMPLETED]
    };

    states.forEach(state => {
      if (!statistic[state]) {
        return;
      }

      const img = document.createElement('img');
      const span = document.createElement('span');
      const div = document.createElement('div');

      Object.assign(img, {
        src: icons[state][this.props.theme],
        width: 24,
        height: 24
      });

      Object.assign(span, {
        style: Styled.span
      });
      Object.assign(div, {
        style: Styled.getInlineStyle(state, this.props.theme)
      });

      var newContent = document.createTextNode(statistic[state]);
      span.appendChild(newContent);
      div.appendChild(img);
      div.appendChild(span);

      this.Viewer.get('overlays').add(
        statistic.activityId,
        STATISTICS_OVERLAY_ID,
        {
          position: positions[state],
          html: div
        }
      );
    });
  };

  clearOverlaysByType = type => {
    this.Viewer.get('overlays').remove({
      type
    });
  };

  render() {
    return (
      <Styled.Diagram>
        <Styled.DiagramCanvas ref={this.myRef} />
        <DiagramControls
          handleZoomIn={this.handleZoomIn}
          handleZoomOut={this.handleZoomOut}
          handleZoomReset={this.handleZoomReset}
        />{' '}
      </Styled.Diagram>
    );
  }
}

export default themed(Diagram);
