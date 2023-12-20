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

import { Inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { BehaviorSubject, EMPTY, Observable } from "rxjs";
import { catchError, map } from "rxjs/operators";

import { AddIntegrationPostData, Providers } from "../model/integrations.model";
import { Constants } from "../../../entities/Constants";
import { SnackBarService } from "../../../services-ngx/snack-bar.service";

@Injectable({
  providedIn: "root"
})
export class AddIntegrationService {
  private isLoading$$ = new BehaviorSubject<boolean>(false);
  private isError$$ = new BehaviorSubject<boolean>(false);

  constructor(
    private httpClient: HttpClient,
    private snackBarService: SnackBarService,
    @Inject("Constants") private readonly constants: Constants
  ) {
  }

  public isLoading$(): Observable<boolean> {
    return this.isLoading$$.asObservable();
  }

  public isError$(): Observable<boolean> {
    return this.isError$$.asObservable();
  }

  public getConfiguration(provider: Providers) {
    const config = {
      aws: {
        controls: {
          region: [""],
          accessKey: [""],
          secretAccessKey: [""]
        },
        inputs: [
          {
            label: 'Region',
            formControlName: 'region',
            type: 'text'
          },
          {
            label: 'Access Key ID',
            formControlName: 'accessKey',
            type: 'text'
          },
          {
            label: 'Secret Access Key',
            formControlName: 'secretAccessKey',
            type: 'text'
          },
        ]

      },
      solace: {
        controls: {
          host: [""],
          port: [""],
          basePath: [""],
          token: [""]
        },
        inputs: [
          {
            label: 'Host',
            formControlName: 'host',
            type: 'text'
          },
          {
            label: 'Port',
            formControlName: 'port',
            type: 'text'
          },
          {
            label: 'Base Path',
            formControlName: 'basePath',
            type: 'text'
          },
          {
            label: 'Token',
            formControlName: 'token',
            type: 'text'
          },

        ]
      }

    };

    if (!config[provider]) {
      this.snackBarService.error(`Error: No configuration fields found`);
    }

    return config[provider] || {};
  }


  public addIntegration(data: AddIntegrationPostData) {
    this.isLoading$$.next(true);
    this.isError$$.next(false);

    return this.httpClient.post(`${this.constants.env.baseURL}/integrations`, data, {})
      .pipe(
        map((res) => {
          this.snackBarService.success(`The integration has been created`);
          this.isLoading$$.next(false);
          return res;
        }),
        catchError(_ => {
          this.isLoading$$.next(false);
          this.isError$$.next(true);
          this.snackBarService.error(`Error: No integration has been created`);
          return EMPTY;
        })
      );
  }
}
