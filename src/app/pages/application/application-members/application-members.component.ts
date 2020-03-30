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
import { Component, OnInit } from '@angular/core';
import {
  ApplicationService,
  Member,
  Application,
  GroupService,
  UsersService,
  User,
  PortalService,
  PermissionsService
} from '@gravitee/ng-portal-webclient';
import { ActivatedRoute, Router } from '@angular/router';
import { marker as i18n } from '@biesbjerg/ngx-translate-extract-marker';
import '@gravitee/ui-components/wc/gv-autocomplete';
import '@gravitee/ui-components/wc/gv-button';
import '@gravitee/ui-components/wc/gv-confirm';
import '@gravitee/ui-components/wc/gv-icon';
import '@gravitee/ui-components/wc/gv-identity-picture';
import '@gravitee/ui-components/wc/gv-info';
import '@gravitee/ui-components/wc/gv-input';
import '@gravitee/ui-components/wc/gv-list';
import '@gravitee/ui-components/wc/gv-select';
import '@gravitee/ui-components/wc/gv-table';
import { TranslateService } from '@ngx-translate/core';
import { FormGroup, FormControl } from '@angular/forms';
import { NotificationService } from '../../../services/notification.service';

@Component({
  selector: 'app-application-members',
  templateUrl: './application-members.component.html',
  styleUrls: ['./application-members.component.css']
})
export class ApplicationMembersComponent implements OnInit {

  constructor(
    private applicationService: ApplicationService,
    private groupService: GroupService,
    private portalService: PortalService,
    private translateService: TranslateService,
    private route: ActivatedRoute,
    private router: Router,
    private notificationService: NotificationService,
    private permissionService: PermissionsService,
    private usersService: UsersService
  ) {
    this.resetAddMember();
    this.resetTransferOwnership();
  }

  get hasNewMember() {
    return this.selectedUserToAdd !== null;
  }

  get selectedUserToAddName() {
    return this.hasNewMember ? this.selectedUserToAdd.display_name : '';
  }

  get hasUserForTransferOwnership() {
    return this.selectedUserForTransferOwnership !== null;
  }

  get selectedUserForTransferOwnershipName() {
    return this.hasUserForTransferOwnership ? this.selectedUserForTransferOwnership.display_name : '';
  }

  readonly = true;
  application: Application;
  connectedApis: Promise<any[]>;
  miscellaneous: any[];
  members: Array<Member>;
  membersOptions: any;
  roles: Array<{ label: string, value: string }>;

  groups: Array<{ groupId: string, groupName: string, groupMembers: Array<Member>, nbGroupMembers: any }>;
  groupMembersOptions: any;

  addMemberForm: FormGroup;
  userListForAddMember: Array<User> = [];
  selectedUserToAdd: User;

  transferOwnershipForm: FormGroup;
  userListForTransferOwnership: Array<User> = [];
  selectedUserForTransferOwnership: User;

  ngOnInit() {
    this.application = this.route.snapshot.data.application;
    if (this.application) {

      let memberPermissions: string[];
      this.permissionService.getCurrentUserPermissions({ applicationId: this.application.id })
        .toPromise()
        .then((permissions) => {
          if (permissions) {
            memberPermissions = permissions.MEMBER;
          }
        })
        .catch(() => (memberPermissions = []))
        .finally(() => {
          this.readonly = !memberPermissions || memberPermissions.length === 0 || !memberPermissions.includes('U');
        });

      this.portalService.getApplicationRoles()
        .toPromise()
        .then(appRoles => {
          this.roles = [];
          appRoles.data.forEach((appRole) => {
            if (appRole.name !== 'PRIMARY_OWNER') {
              this.roles.push({
                label: appRole.name,
                value: appRole.id
              });
            }
          });
        });

      if (this.application.groups && this.application.groups.length > 0) {
        this.groups = [];
        this.application.groups.forEach(grp => {
          this.groupService.getMembersByGroupId({ groupId: grp.id, size: -1 })
            .toPromise()
            .then((membersResponse) => {
              this.groups.push({
                groupId: grp.id,
                groupName: grp.name,
                groupMembers: membersResponse.data,
                nbGroupMembers: membersResponse.metadata.data.total,
              });
            });
        });
      }

      this.translateService.get([
        i18n('application.members.list.member'),
        i18n('application.members.list.role'),
        i18n('application.members.list.remove.message'),
        i18n('application.members.list.remove.title'),
      ])
        .toPromise()
        .then(translations => {
          const tableTranslsations = Object.values(translations);
          this.membersOptions = {
            paging: 5,
            data: [
              { field: 'user._links.avatar', type: 'image', alt: 'user.display_name' },
              { field: 'user.display_name', label: tableTranslsations[0] },
              {
                field: 'role', label: tableTranslsations[1], type: 'gv-select',
                attributes: {
                  options: (item) => item.role === 'PRIMARY_OWNER' ? ['PRIMARY_OWNER'] : this.roles,
                  disabled: (item) => item.role === 'PRIMARY_OWNER' || this.readonly,
                  'ongv-select:select': (item) => this.updateMember(item),
                }
              },
              {
                type: 'gv-icon',
                width: '25px',
                confirmMessage: tableTranslsations[2],
                condition: (item) => item.role !== 'PRIMARY_OWNER',
                attributes: {
                  onClick: (item) => this.removeMember(item),
                  shape: 'general:trash',
                  title: tableTranslsations[3],
                },
              },
            ],
          };
          if (this.application.groups && this.application.groups.length > 0) {
            this.groupMembersOptions = {
              paging: 5,
              data: [
                { field: 'user._links.avatar', type: 'image', alt: 'user.display_name' },
                { field: 'user.display_name', label: tableTranslsations[0] },
                { field: 'role', label: tableTranslsations[1] },
              ],
            };
          }
          this.loadMembersTable();
        });

      this.connectedApis = this.applicationService.getSubscriberApisByApplicationId({ applicationId: this.application.id })
        .toPromise()
        .then((response) => {
          return response.data.map((api) => ({
            name: api.name,
            description: api.description,
            picture: (api._links ? api._links.picture : '')
          }));
        });

      this.translateService.get([
        i18n('application.miscellaneous.owner'),
        i18n('application.miscellaneous.type'),
        i18n('application.miscellaneous.createdDate'),
        i18n('application.miscellaneous.lastUpdate'),
        'application.types'
      ], { type: this.application.applicationType })
        .toPromise()
        .then(translations => {
          const infoTranslsations = Object.values(translations);
          this.miscellaneous = [
            { key: infoTranslsations[0], value: this.application.owner.display_name },
            { key: infoTranslsations[1], value: infoTranslsations[4] },
            {
              key: infoTranslsations[2],
              value: new Date(this.application.created_at),
              date: 'short'
            },
            {
              key: infoTranslsations[3],
              value: new Date(this.application.updated_at),
              date: 'relative'
            },
          ];
        });
    }
  }

  loadMembersTable() {
    return this.applicationService.getMembersByApplicationId({ applicationId: this.application.id }).toPromise()
      .then((membersResponse) => {
        this.members = membersResponse.data;
      });
  }

  resetAddMember() {
    this.selectedUserToAdd = null;
    this.addMemberForm = new FormGroup({
      newMemberRole: new FormControl('')
    });
  }

  resetTransferOwnership() {
    this.selectedUserForTransferOwnership = null;
    this.transferOwnershipForm = new FormGroup({
      primaryOwnerNewRole: new FormControl('USER')
    });
  }

  onSearchUserToAdd({ detail }) {
    this.searchUser(detail).then(users => this.userListForAddMember = users);
  }

  onSearchUserForTransferOwnership({ detail }) {
    this.searchUser(detail).then(users => this.userListForTransferOwnership = users);
  }

  onSelectUserToAdd({ detail }) {
    this.selectedUserToAdd = detail.data;
  }

  onSelectUserForTransferOwnership({ detail }) {
    this.selectedUserForTransferOwnership = detail.data;
  }

  searchUser(query: string): Promise<User[]> {
    return this.usersService.getUsers({ q: query })
      .toPromise()
      .then(usersResponse => {
        let result: User[] = [];
        if (usersResponse.data.length) {
          result = usersResponse.data.map((u) => {
            const row = document.createElement('gv-row');
            // @ts-ignore
            row.item = { name: u.display_name, picture: u._links ? u._links.avatar : '' };
            return { value: u.display_name, element: row, id: u.id, data: u };
          });
        }
        return result;
      });
  }

  removeMember(member: Member) {
    this.applicationService.deleteApplicationMember({
      applicationId: this.application.id,
      memberId: member.user.id
    })
      .toPromise()
      .then(() => this.loadMembersTable())
      .then(() => this.notificationService.success(i18n('application.members.list.remove.success')));
  }

  addMember() {
    this.applicationService.createApplicationMember({
      applicationId: this.application.id,
      MemberInput: {
        user: this.selectedUserToAdd.id,
        reference: this.selectedUserToAdd.reference,
        role: this.addMemberForm.controls.newMemberRole.value
      }
    }).toPromise().then(
      () => {
        this.notificationService.success(i18n('application.members.add.success'));
        this.loadMembersTable();
        this.resetAddMember();
      }
    );
  }

  toggleGroupMembers($event: any) {
    $event.target.closest('.groupMembers').classList.toggle('show');
  }

  transferOwnership() {
    this.applicationService.transferMemberOwnership(
      {
        applicationId: this.application.id,
        TransferOwnershipInput: {
          new_primary_owner_id: this.selectedUserForTransferOwnership.id,
          new_primary_owner_reference: this.selectedUserForTransferOwnership.reference,
          primary_owner_newrole: this.transferOwnershipForm.controls.primaryOwnerNewRole.value
        }
      })
      .toPromise()
      .then(() => this.router.navigate(['applications']))
      .then(() => this.notificationService.success(i18n('application.members.transferOwnership.success')));
  }

  updateMember(member: Member) {
    this.applicationService.updateApplicationMemberByApplicationIdAndMemberId({
      applicationId: this.application.id,
      memberId: member.user.id,
      MemberInput: {
        user: member.user.id,
        role: member.role,
      }
    }).toPromise().then(
      () => {
        this.notificationService.success(i18n('application.members.list.success'));
      }
    );
  }

}
