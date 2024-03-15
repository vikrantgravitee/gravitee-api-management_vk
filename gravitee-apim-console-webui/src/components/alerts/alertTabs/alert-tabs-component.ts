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
import { StateService } from '@uirouter/core';

import UserService from '../../../services/user.service';

class AlertTabsController {
  private selectedIndex: number;
  private canViewAlerts: boolean;

  /* @ngInject */
  constructor(private $state: StateService, private UserService: UserService, private Constants) {}

  $onInit() {
    const tabs = ['management.alerts.list', 'management.alerts.activity'];
    this.canViewAlerts = this.Constants.org.settings.alert.enabled && this.UserService.isUserHasPermissions(['environment-alert-r']);
    const candidateIndex = tabs.findIndex((tab) => this.$state.is(tab));
    this.selectedIndex = candidateIndex > -1 ? candidateIndex : 0;
  }
}

export default AlertTabsController;