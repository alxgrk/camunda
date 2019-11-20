/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {createRef, useState, useEffect} from 'react';
import PropTypes from 'prop-types';

import {isValidJSON} from 'modules/utils';

import * as Styled from './styled';

export default function Variables({
  variables,
  isRunning,
  editMode,
  onVariableUpdate,
  isEditable,
  setEditMode,
  Placeholder,
  Overlay,
  ...props
}) {
  const MODE = {EDIT: 'edit', ADD: 'add'};

  const [key, setKey] = useState('');
  const [value, setValue] = useState('');

  const variablesContentRef = createRef();

  function closeEdit() {
    setEditMode('');
    setKey('');
    setValue('');
  }

  function saveVariable() {
    onVariableUpdate(key, value);
    closeEdit();
  }

  function handleOpenAddVariable() {
    setEditMode(MODE.ADD);
  }

  function handleOpenEditVariable(name, value) {
    setEditMode(MODE.EDIT);
    setKey(name);
    setValue(value);
  }

  // scroll to the bottom of the table if the variables inputs got added
  useEffect(() => {
    if (editMode === MODE.ADD) {
      const scrollableElement = variablesContentRef.current.children[0];
      scrollableElement.scrollTop = scrollableElement.scrollHeight;
    }
  }, [editMode]);

  function renderEditButtons({isDisabled}) {
    return (
      <>
        <Styled.EditButton
          title="Exit edit mode"
          data-test="exit-edit-inline-btn"
          onClick={closeEdit}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CloseIcon />}
        />

        <Styled.EditButton
          data-test="save-var-inline-btn"
          title="Save variable"
          disabled={!value || !isValidJSON(value) || isDisabled}
          onClick={saveVariable}
          size="large"
          iconButtonTheme="default"
          icon={<Styled.CheckIcon />}
        />
      </>
    );
  }

  function renderInlineEdit(propValue) {
    const valueHasntChanged = propValue === value;
    return (
      <>
        <Styled.EditInputTD>
          <Styled.EditTextarea
            autoFocus
            hasAutoSize
            minRows={1}
            maxRows={4}
            data-test="edit-value"
            placeholder="Value"
            value={value}
            onChange={e => setValue(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditButtonsTD>
          {renderEditButtons({
            isDisabled: valueHasntChanged
          })}
        </Styled.EditButtonsTD>
      </>
    );
  }

  function renderInlineAdd() {
    const variableAlreadyExists =
      !!variables.map(variable => variable.name).filter(name => name === key)
        .length > 0;
    const isVariableEmpty = key.trim() === '';
    return (
      <Styled.TR data-test="add-key-row">
        <Styled.EditInputTD>
          <Styled.TextInput
            autoFocus
            type="text"
            data-test="add-key"
            placeholder="Variable"
            value={key}
            onChange={e => setKey(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.EditInputTD>
          <Styled.AddTextarea
            data-test="add-value"
            placeholder="Value"
            hasAutoSize
            minRows={1}
            maxRows={4}
            value={value}
            onChange={e => setValue(e.target.value)}
          />
        </Styled.EditInputTD>
        <Styled.AddButtonsTD>
          {renderEditButtons({
            isDisabled: variableAlreadyExists || isVariableEmpty
          })}
        </Styled.AddButtonsTD>
      </Styled.TR>
    );
  }

  function renderContent() {
    return (
      <Styled.TableScroll>
        <Styled.Table>
          <Styled.THead>
            <Styled.TR>
              <Styled.TH>Variable</Styled.TH>
              <Styled.TH>Value</Styled.TH>
              <Styled.TH />
            </Styled.TR>
          </Styled.THead>
          <tbody>
            {variables &&
              variables.map(({name, value: propValue, hasActiveOperation}) => (
                <Styled.TR
                  key={name}
                  data-test={name}
                  hasActiveOperation={hasActiveOperation}
                >
                  <Styled.TD isBold={true}>
                    <Styled.VariableName title={name}>
                      {name}
                    </Styled.VariableName>
                  </Styled.TD>
                  {key === name && editMode === MODE.EDIT && isRunning ? (
                    renderInlineEdit(propValue, name)
                  ) : (
                    <>
                      <Styled.TD>
                        <Styled.DisplayText>{propValue}</Styled.DisplayText>
                      </Styled.TD>
                      {isRunning && (
                        <Styled.EditButtonsTD>
                          {hasActiveOperation ? (
                            <Styled.Spinner />
                          ) : (
                            <Styled.EditButton
                              title="Enter edit mode"
                              data-test="enter-edit-btn"
                              onClick={() =>
                                handleOpenEditVariable(name, propValue)
                              }
                              size="large"
                              iconButtonTheme="default"
                              icon={<Styled.EditIcon />}
                            />
                          )}
                        </Styled.EditButtonsTD>
                      )}
                    </>
                  )}
                </Styled.TR>
              ))}
            {editMode === MODE.ADD && renderInlineAdd()}
          </tbody>
        </Styled.Table>
      </Styled.TableScroll>
    );
  }

  function renderPlaceholder() {
    return (
      <Styled.SkeletonTable>
        <Styled.THead>
          <Styled.TR>
            <Styled.TH>Variable</Styled.TH>
            <Styled.TH>Value</Styled.TH>
            <Styled.TH />
          </Styled.TR>
        </Styled.THead>
        <tbody>
          <Styled.SkeletonTR>
            <Styled.SkeletonTD>{Placeholder()}</Styled.SkeletonTD>
          </Styled.SkeletonTR>
        </tbody>
      </Styled.SkeletonTable>
    );
  }

  return (
    <>
      <Styled.VariablesContent ref={variablesContentRef}>
        {Overlay && Overlay()}
        {!editMode && Placeholder ? renderPlaceholder() : renderContent()}
      </Styled.VariablesContent>
      <Styled.VariablesFooter>
        <Styled.Button
          title="Add variable"
          size="small"
          data-test="enter-add-btn"
          onClick={() => handleOpenAddVariable()}
          disabled={!!editMode || !isEditable || props.isLoading}
        >
          <Styled.Plus /> Add Variable
        </Styled.Button>
      </Styled.VariablesFooter>
    </>
  );
}

Variables.propTypes = {
  isRunning: PropTypes.bool,
  variables: PropTypes.array,
  editMode: PropTypes.string.isRequired,
  isEditable: PropTypes.bool.isRequired,
  onVariableUpdate: PropTypes.func.isRequired,
  setEditMode: PropTypes.func.isRequired,
  // render props
  Placeholder: PropTypes.func,
  Overlay: PropTypes.func
};
