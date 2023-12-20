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

import { Component, OnDestroy, OnInit } from "@angular/core";
import { ActivatedRoute, Router } from "@angular/router";
import { FormBuilder, FormGroup } from "@angular/forms";
import { Subscription } from "rxjs";
import { take } from "rxjs/operators";

import { AddIntegrationService } from "../../services/add-integration.service";
import { AddIntegrationPostData, Providers } from "../../model/integrations.model";

@Component({
  selector: "app-add-integration",
  templateUrl: "./add-integration.component.html",
  styleUrls: ["./add-integration.component.scss"]
})
export class AddIntegrationComponent implements OnInit, OnDestroy {
  protected readonly Providers = Providers;
  private subscription: Subscription;
  public configurationFields;
  public provider: Providers;
  public serviceName = "";
  public addIntegrationForm: FormGroup;

  constructor(
    private router: Router,
    private route: ActivatedRoute,
    private formBuilder: FormBuilder,
    public addIntegrationService: AddIntegrationService
  ) {
  }

  ngOnDestroy() {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
  }

  ngOnInit() {
    this.provider = this.route.snapshot.paramMap.get("provider") as Providers;
    this.serviceName = this.providerToServiceName(this.provider);
    this.addIntegrationForm = this.formBuilder.group({
      name: [""],
      description: [""],
      configuration: this.buildConfigurationForm()
    });
  }

  private buildConfigurationForm() {
    this.configurationFields = this.addIntegrationService.getConfiguration(this.provider);
    return this.formBuilder.group({ ...this.configurationFields.controls });
  }

  public providerToServiceName(provider: Providers): string {
    const titles = {
      aws: "Amazon API Gateway",
      solace: "Solace"
    };
    return titles[provider];
  }

  onSubmit(form: FormGroup) {
    const data: AddIntegrationPostData = {
      type: this.provider,
      name: form.value.name,
      description: form.value.description,
      configuration: {
        ...form.value.configuration
      }
    };

    this.subscription = this.addIntegrationService
      .addIntegration(data)
      .pipe(take(1))
      .subscribe(_ => {
        this.handleExit();
      });
  }

  public handleExit() {
    this.router
      .navigate(["../../"], { relativeTo: this.route });
  }
}
