/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ComponentPropsWithoutRef, MouseEvent, ReactNode} from 'react';

import classnames from 'classnames';
import './Labeled.scss';

export interface LabeledProps extends ComponentPropsWithoutRef<'label'> {
  label: string | ReactNode;
  appendLabel?: boolean;
  disabled?: boolean;
}

export default function Labeled({
  label,
  className,
  appendLabel,
  children,
  disabled,
  ...props
}: LabeledProps) {
  return (
    <div className={classnames('Labeled', className, {disabled})}>
      <label className={classnames({checkLabel: appendLabel})} onClick={catchClick} {...props}>
        {!appendLabel && <span className="label before">{label}</span>}
        {children}
        {appendLabel && <span className="label after">{label}</span>}
      </label>
    </div>
  );
}

function catchClick(evt: MouseEvent<HTMLElement>) {
  const eventTarget = evt.target as HTMLElement;
  if (
    !eventTarget.classList.contains('label') &&
    !eventTarget.closest('.label') &&
    !eventTarget.classList.contains('Input')
  ) {
    evt.preventDefault();
  }
}
