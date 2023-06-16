/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {expect} from '@playwright/test';
import {test} from '../test-fixtures';

test.beforeEach(async ({page}) => {
  await page.goto('/login');
});

test.describe.parallel('login page', () => {
  test('redirect to the main page on login', async ({page}) => {
    expect(await page.getByLabel('Password').getAttribute('type')).toEqual(
      'password',
    );

    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();

    await expect(page).toHaveURL('/');
  });

  test('have no a11y violations', async ({makeAxeBuilder}) => {
    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('show error message on login failure', async ({
    page,
    makeAxeBuilder,
  }) => {
    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('wrong');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');

    await expect(
      page.getByRole('alert').getByText('Username and password do not match'),
    ).toBeVisible();

    const results = await makeAxeBuilder().analyze();

    expect(results.violations).toHaveLength(0);
    expect(results.passes.length).toBeGreaterThan(0);
  });

  test('block form submission with empty fields', async ({page}) => {
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');
    await page.getByLabel('Username').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');
    await page.getByLabel('Username').fill(' ');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/login');
  });

  test('log out redirect', async ({page}) => {
    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();
    await expect(page).toHaveURL('/login');
  });

  test('persistency of a session', async ({page}) => {
    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/');
    await page.reload();
    await expect(page).toHaveURL('/');
  });

  test('redirect to the correct URL after login', async ({page}) => {
    await page.goto('/123');
    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/123');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();

    await page.goto('/?filter=unassigned');
    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/?filter=unassigned');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();

    await page.goto('/123?filter=unassigned');
    await page.getByLabel('Username').fill('demo');
    await page.getByLabel('Password').fill('demo');
    await page.getByRole('button', {name: 'Login'}).click();
    await expect(page).toHaveURL('/123?filter=unassigned');
    await page.getByRole('button', {name: 'Open Settings'}).click();
    await page.getByRole('button', {name: 'Log out'}).click();
  });
});
