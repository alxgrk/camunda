import processRawData from './processRawData';

const data = [
  {
    prop1: 'foo',
    prop2: 'bar',
    variables: {
      var1: 12,
      var2: null
    }
  },
  {
    prop1: 'xyz',
    prop2: 'abc',
    variables: {
      var1: null,
      var2: true
    }
  }
];

it('should transform data to table compatible format', () => {
  expect(processRawData(data)).toEqual({
    head: ['prop1', 'prop2', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['foo', 'bar', '12', ''], ['xyz', 'abc', '', 'true']]
  });
});

it('should not include columns that are hidden', () => {
  expect(processRawData(data, ['prop2'])).toEqual({
    head: ['prop1', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['foo', '12', ''], ['xyz', '', 'true']]
  });
});

it('should exclude variable columns using the var__ prefix', () => {
  expect(processRawData(data, ['var__var1'])).toEqual({
    head: ['prop1', 'prop2', {label: 'Variables', columns: ['var2']}],
    body: [['foo', 'bar', ''], ['xyz', 'abc', 'true']]
  });
});

it('should apply column order', () => {
  expect(
    processRawData(data, [], {
      processInstanceProps: ['prop2', 'prop1'],
      variables: ['var1', 'var2']
    })
  ).toEqual({
    head: ['prop2', 'prop1', {label: 'Variables', columns: ['var1', 'var2']}],
    body: [['bar', 'foo', '12', ''], ['abc', 'xyz', '', 'true']]
  });
});

it('should prepend columns without specified column position', () => {
  expect(processRawData(data, [], {processInstanceProps: ['prop1'], variables: ['var1']})).toEqual({
    head: ['prop2', 'prop1', {label: 'Variables', columns: ['var2', 'var1']}],
    body: [['bar', 'foo', '', '12'], ['abc', 'xyz', 'true', '']]
  });
});

it('should sort and hide simulateously', () => {
  expect(
    processRawData(data, ['prop2'], {
      processInstanceProps: ['prop2', 'prop1'],
      variables: ['var2', 'var1']
    })
  ).toEqual({
    head: ['prop1', {label: 'Variables', columns: ['var2', 'var1']}],
    body: [['foo', '', '12'], ['xyz', 'true', '']]
  });
});

it('should make the process instance id a link', () => {
  const cell = processRawData(
    [{processInstanceId: '123', engineName: '1', variables: {}}],
    undefined,
    undefined,
    {1: {endpoint: 'http://camunda.com', engineName: 'a'}}
  ).body[0][0];

  expect(cell.type).toBe('a');
  expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/123');
});

it('should not make the process instance id a link if no endpoint is specified', () => {
  const cell = processRawData([{processInstanceId: '123', engineName: '1', variables: {}}])
    .body[0][0];

  expect(cell).toBe('123');
});
