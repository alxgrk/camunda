import React from 'react';
import {shallow} from 'enzyme';

import {Batch} from 'modules/components/Icon';
import {DROPDOWN_PLACEMENT} from 'modules/constants';

import Dropdown from './Dropdown';

import * as Styled from './styled';

describe('Dropdown', () => {
  let Child, node;
  beforeEach(() => {
    Child = () => <span>I am a child component</span>;
    node = shallow(
      <Dropdown label="Foo" placement={DROPDOWN_PLACEMENT.BOTTOM}>
        <Child />
      </Dropdown>
    );
    node.instance().setRef(document.createElement('div'));
  });

  it('should render Button with Icon or text label', () => {
    expect(node.find(Styled.Button)).toMatchSnapshot();

    node = shallow(
      <Dropdown label={<Batch />}>
        <Child />
      </Dropdown>
    );
    expect(Styled.Button).toMatchSnapshot();
  });

  it('should be closed initially', () => {
    expect(node.state().isOpen).toBe(false);
  });

  it('should show/hide child contents when isOpen/closed', () => {
    expect(node.find(Child)).not.toExist();

    node.setState({isOpen: true});

    expect(node.find(Child)).toExist();
  });

  it('should pass "placement" prop to children', () => {
    node.setState({isOpen: true});
    expect(node.find('Child').props().placement).toBe(
      DROPDOWN_PLACEMENT.BOTTOM
    );
  });

  it('should pass "bottom" as default placement to children', () => {
    node = shallow(
      <Dropdown label="Foo">
        <Child />
      </Dropdown>
    );

    node.setState({isOpen: true});

    expect(node.find('Child').props().placement).toBe(
      DROPDOWN_PLACEMENT.BOTTOM
    );
  });

  it('should isOpen/close on click of the button', () => {
    //given
    node.find(Styled.Button).simulate('click');
    expect(node.state().isOpen).toBe(true);
    //when
    node.find(Styled.Button).simulate('click');
    //then
    expect(node.state().isOpen).toBe(false);
  });

  it('should display its child contents if it is isOpen', () => {
    node.setState({isOpen: true});
    expect(node.find(Child)).toExist();
  });

  it('should close the dropdown when clicking anywhere', () => {
    node.setState({isOpen: true});
    document.body.click();
    expect(node.state().isOpen).toBe(false);
  });

  it('should match Snapshot', () => {
    node.setState({isOpen: true});
    expect(node).toMatchSnapshot();
  });
});
