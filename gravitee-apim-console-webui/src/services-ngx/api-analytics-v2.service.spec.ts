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
import { HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ApiAnalyticsV2Service } from './api-analytics-v2.service';

import { CONSTANTS_TESTING, GioTestingModule } from '../shared/testing';
import { fakeAnalyticsRequestsCount } from '../entities/management-api-v2/analytics/analyticsRequestsCount.fixture';

describe('ApiAnalyticsV2Service', () => {
  let httpTestingController: HttpTestingController;
  let service: ApiAnalyticsV2Service;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GioTestingModule],
    });

    httpTestingController = TestBed.inject(HttpTestingController);
    service = TestBed.inject<ApiAnalyticsV2Service>(ApiAnalyticsV2Service);
  });

  afterEach(() => {
    httpTestingController.verify();
  });

  describe('getRequestsCount', () => {
    it('should call the API', (done) => {
      const apiId = 'api-id';

      service.getRequestsCount(apiId).subscribe((result) => {
        expect(result).toEqual(fakeAnalyticsRequestsCount());
        done();
      });

      httpTestingController
        .expectOne({
          url: `${CONSTANTS_TESTING.env.v2BaseURL}/apis/${apiId}/analytics/requests-count`,
          method: 'GET',
        })
        .flush(fakeAnalyticsRequestsCount());
    });
  });
});