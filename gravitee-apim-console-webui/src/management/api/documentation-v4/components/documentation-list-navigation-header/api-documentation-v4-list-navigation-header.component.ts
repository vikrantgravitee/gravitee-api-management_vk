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

import { Component, EventEmitter, Input, Output } from '@angular/core';

import { Breadcrumb, getLogoForPageType, PageType, getTitleForPageType } from '../../../../../entities/management-api-v2';

@Component({
  selector: 'api-documentation-list-navigation-header',
  templateUrl: './api-documentation-v4-list-navigation-header.component.html',
  styleUrls: ['./api-documentation-v4-list-navigation-header.component.scss'],
})
export class ApiDocumentationV4ListNavigationHeaderComponent {
  @Input()
  breadcrumbs: Breadcrumb[];
  @Input()
  hasPages: boolean;
  @Output()
  onAddFolder = new EventEmitter<void>();
  @Output()
  onAddPage = new EventEmitter<PageType>();
  @Output()
  onNavigateTo = new EventEmitter<string>();

  // expose constants
  readonly getLogoForPageType = getLogoForPageType;
  readonly getTitleForPageType = getTitleForPageType;
  pageTypes: PageType[] = ['MARKDOWN', 'SWAGGER', 'ASYNCAPI'];
}