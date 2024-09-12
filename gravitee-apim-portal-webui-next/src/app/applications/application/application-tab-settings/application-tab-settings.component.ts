/*
 * Copyright (C) 2024 The Gravitee team (http://gravitee.io)
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
import { NgIf } from '@angular/common';
import { Component, Input, OnInit } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { Router } from '@angular/router';
import { EMPTY, Subject, switchMap, takeUntil, tap } from 'rxjs';

import { ApplicationTabSettingsEditComponent } from './application-tab-settings-edit/application-tab-settings-edit.component';
import { ApplicationTabSettingsReadComponent } from './application-tab-settings-read/application-tab-settings-read.component';
import { DeleteConfirmDialogComponent } from './delete-confirm-dialog/delete-confirm-dialog.component';
import { Application, ApplicationType } from '../../../../entities/application/application';
import { UserApplicationPermissions } from '../../../../entities/permission/permission';
import { ApplicationService } from '../../../../services/application.service';

@Component({
  selector: 'app-application-tab-settings',
  standalone: true,
  imports: [MatButtonModule, NgIf, ApplicationTabSettingsEditComponent, ApplicationTabSettingsReadComponent],
  templateUrl: './application-tab-settings.component.html',
})
export class ApplicationTabSettingsComponent implements OnInit {
  @Input()
  application!: Application;

  @Input()
  applicationTypeConfiguration!: ApplicationType;

  @Input()
  userApplicationPermissions!: UserApplicationPermissions;

  canUpdate: boolean = false;
  canDelete: boolean = false;

  private unsubscribe$ = new Subject();

  constructor(
    private readonly applicationService: ApplicationService,
    private matDialog: MatDialog,
    private router: Router,
  ) {}

  ngOnInit(): void {
    this.canDelete = this.userApplicationPermissions.DEFINITION?.includes('D') || false;
    this.canUpdate = this.userApplicationPermissions.DEFINITION?.includes('U') || false;
  }

  deleteApplication(): void {
    this.matDialog
      .open<DeleteConfirmDialogComponent, void, boolean>(DeleteConfirmDialogComponent, {
        role: 'alertdialog',
        id: 'confirmDialog',
      })
      .afterClosed()
      .pipe(
        switchMap(confirmed => (confirmed ? this.applicationService.delete(this.application.id) : EMPTY)),
        tap(() => this.router.navigate(['/applications'])),
        takeUntil(this.unsubscribe$),
      )
      .subscribe({ error: err => console.error(err) });
  }
}