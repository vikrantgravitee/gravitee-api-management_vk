/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { HttpTestingController } from '@angular/common/http/testing';
import { InteractivityChecker } from '@angular/cdk/a11y';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { HarnessLoader, parallel } from '@angular/cdk/testing';
import { MatTableHarness } from '@angular/material/table/testing';
import { MatIconTestingModule } from '@angular/material/icon/testing';
import { MatInputHarness } from '@angular/material/input/testing';
import { MatButtonHarness } from '@angular/material/button/testing';
import { GioSaveBarHarness } from '@gravitee/ui-particles-angular';

import { ApiPropertiesComponent } from './api-properties.component';
import { ApiPropertiesModule } from './api-properties.module';

import { CONSTANTS_TESTING, GioHttpTestingModule } from '../../../../shared/testing';
import { User } from '../../../../entities/user';
import { CurrentUserService, UIRouterStateParams } from '../../../../ajs-upgraded-providers';
import { GioUiRouterTestingModule } from '../../../../shared/testing/gio-uirouter-testing-module';
import { Api, fakeApiV4 } from '../../../../entities/management-api-v2/api';

describe('ApiPropertiesComponent', () => {
  const API_ID = 'apiId';
  let fixture: ComponentFixture<ApiPropertiesComponent>;
  let component: ApiPropertiesComponent;
  let httpTestingController: HttpTestingController;
  let loader: HarnessLoader;

  const currentUser = new User();
  currentUser.userPermissions = ['api-plan-r', 'api-plan-u'];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NoopAnimationsModule, GioHttpTestingModule, ApiPropertiesModule, GioUiRouterTestingModule, MatIconTestingModule],
      providers: [
        { provide: UIRouterStateParams, useValue: { apiId: API_ID } },
        {
          provide: CurrentUserService,
          useValue: { currentUser },
        },
      ],
    })
      .overrideProvider(InteractivityChecker, {
        useValue: {
          isFocusable: () => true, // This traps focus checks and so avoid warnings when dealing with
        },
      })
      .compileComponents();

    fixture = TestBed.createComponent(ApiPropertiesComponent);
    httpTestingController = TestBed.inject(HttpTestingController);
    loader = TestbedHarnessEnvironment.loader(fixture);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  it('should display properties', async () => {
    expect(component).toBeTruthy();

    const table = await loader.getHarness(MatTableHarness.with({ selector: '[aria-label="API Properties"]' }));

    const loadingRow = await table.getCellTextByIndex();
    expect(loadingRow).toEqual([['Loading...']]);

    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [
          { key: 'key1', value: 'value1', encrypted: false },
          { key: 'key2', value: 'value2', encrypted: true },
        ],
      }),
    );

    const cellContentByIndex = await getCellContentByIndex(table);
    expect(cellContentByIndex).toEqual([
      {
        key: 'key1',
        value: 'value1',
        isValueDisabled: false,
        encrypted: 'Not Encrypted',
      },
      {
        key: 'key2',
        value: '*************',
        isValueDisabled: true,
        encrypted: 'Encrypted',
      },
    ]);
  });

  it('should renew encrypted value', async () => {
    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [{ key: 'key2', value: 'encryptedValue', encrypted: true }],
      }),
    );

    const renewEncryptedValueButton = await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Renew encrypted value"]' }));
    await renewEncryptedValueButton.click();

    const table = await loader.getHarness(MatTableHarness.with({ selector: '[aria-label="API Properties"]' }));
    const firstRow = (await table.getRows())[0];
    const valueCell = (await firstRow.getCells())[1];
    const valueInput = await valueCell.getHarness(MatInputHarness);

    expect(await valueInput.getValue()).toEqual('');

    await valueInput.setValue('newEncryptedValue');

    const cellContentByIndex = await getCellContentByIndex(table);
    expect(cellContentByIndex).toEqual([
      {
        key: 'key2',
        value: 'newEncryptedValue',
        isValueDisabled: false,
        encrypted: 'To Encrypt',
      },
    ]);
    const saveBar = await loader.getHarness(GioSaveBarHarness);

    await saveBar.clickSubmit();

    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [{ key: 'key2', value: 'encryptedValue', encrypted: true }],
      }),
    );

    const postApiReq = httpTestingController.expectOne({
      method: 'PUT',
      url: `${CONSTANTS_TESTING.env.v2BaseURL}/apis/${API_ID}`,
    });

    expect(postApiReq.request.body.properties).toEqual([
      {
        encryptable: true,
        encrypted: false,
        key: 'key2',
        value: 'newEncryptedValue',
      },
    ]);
  });

  it('should encrypt value', async () => {
    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [{ key: 'key2', value: 'ValueToEncrypt', encrypted: false }],
      }),
    );

    const encryptValueButton = await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Encrypt value"]' }));
    await encryptValueButton.click();

    const table = await loader.getHarness(MatTableHarness.with({ selector: '[aria-label="API Properties"]' }));
    const firstRow = (await table.getRows())[0];
    const valueCell = (await firstRow.getCells())[1];
    const valueInput = await valueCell.getHarness(MatInputHarness);

    expect(await valueInput.getValue()).toEqual('ValueToEncrypt');

    const cellContentByIndex = await getCellContentByIndex(table);
    expect(cellContentByIndex).toEqual([
      {
        key: 'key2',
        value: 'ValueToEncrypt',
        isValueDisabled: false,
        encrypted: 'To Encrypt',
      },
    ]);
    const saveBar = await loader.getHarness(GioSaveBarHarness);

    await saveBar.clickSubmit();

    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [{ key: 'key2', value: 'encryptedValue', encrypted: true }],
      }),
    );

    const postApiReq = httpTestingController.expectOne({
      method: 'PUT',
      url: `${CONSTANTS_TESTING.env.v2BaseURL}/apis/${API_ID}`,
    });

    expect(postApiReq.request.body.properties).toEqual([
      {
        encryptable: true,
        encrypted: false,
        key: 'key2',
        value: 'ValueToEncrypt',
      },
    ]);
  });

  it('should remove property', async () => {
    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [{ key: 'key2', value: 'ValueToEncrypt', encrypted: false }],
      }),
    );

    const removePropertyButton = await loader.getHarness(MatButtonHarness.with({ selector: '[aria-label="Remove property"]' }));
    await removePropertyButton.click();

    const table = await loader.getHarness(MatTableHarness.with({ selector: '[aria-label="API Properties"]' }));
    const cellContentByIndex = await getCellContentByIndex(table);
    expect(cellContentByIndex).toEqual([
      {
        encrypted: 'Not Encrypted',
        isValueDisabled: false,
        key: 'key2',
        value: 'ValueToEncrypt',
      },
    ]);
    const saveBar = await loader.getHarness(GioSaveBarHarness);

    await saveBar.clickSubmit();

    expectGetApi(
      fakeApiV4({
        id: API_ID,
        properties: [{ key: 'key2', value: 'encryptedValue', encrypted: true }],
      }),
    );

    const postApiReq = httpTestingController.expectOne({
      method: 'PUT',
      url: `${CONSTANTS_TESTING.env.v2BaseURL}/apis/${API_ID}`,
    });

    expect(postApiReq.request.body.properties).toEqual([]);
  });

  async function getCellContentByIndex(table: MatTableHarness) {
    const rows = await table.getRows();
    return await parallel(() =>
      rows.map(async (row) => {
        const cells = await row.getCells();
        const keyInput = await cells[0].getHarness(MatInputHarness);
        const valueInput = await cells[1].getHarness(MatInputHarness);

        return {
          key: await keyInput.getValue(),
          value: await valueInput.getValue(),
          isValueDisabled: await valueInput.isDisabled(),
          encrypted: await cells[2].getText(),
        };
      }),
    );
  }

  function expectGetApi(api: Api) {
    const req = httpTestingController.expectOne({
      method: 'GET',
      url: `${CONSTANTS_TESTING.env.v2BaseURL}/apis/${api.id}`,
    });
    req.flush(api);
  }
});
