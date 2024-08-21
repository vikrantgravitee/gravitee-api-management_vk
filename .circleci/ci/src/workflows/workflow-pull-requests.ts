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
import { commands, Config, Job, reusable, workflow, Workflow } from '@circleci/circleci-config-sdk';

import { CircleCIEnvironment } from '../pipelines';
import { isE2EBranch, isSupportBranchOrMaster } from '../utils';
import { config } from '../config';
import { BaseExecutor } from '../executors';
import {
  BuildBackendImagesJob,
  BuildBackendJob,
  CommunityBuildBackendJob,
  ConsoleWebuiBuildJob,
  DeployOnAzureJob,
  PortalWebuiBuildJob,
  PublishJob,
  ReleaseHelmJob,
  SetupJob,
  SnykApimChartsJob,
} from '../jobs';
import { aquasec } from '../orbs/aquasec';
import { keeper } from '../orbs/keeper';

export class PullRequestsWorkflow {
  static create(dynamicConfig: Config, environment: CircleCIEnvironment): Workflow {
    let jobs: workflow.WorkflowJob[] = [];
    // Needed to publish helm chart in internal repository
    environment.isDryRun = true;
    if (isSupportBranchOrMaster(environment.branch)) {
      jobs.push(
        ...this.getCommonJobs(dynamicConfig, environment, false, false),
        ...this.getE2EJobs(dynamicConfig, environment),
        ...this.getMasterAndSupportJobs(dynamicConfig, environment),
      );
    } else if (isE2EBranch(environment.branch)) {
      jobs.push(...this.getCommonJobs(dynamicConfig, environment, false, true), ...this.getE2EJobs(dynamicConfig, environment));
    } else {
      jobs = this.getCommonJobs(dynamicConfig, environment, true, true);
    }
    return new Workflow('pull_requests', jobs);
  }

  private static getCommonJobs(
    dynamicConfig: Config,
    environment: CircleCIEnvironment,
    filterJobs: boolean,
    addValidationJob: boolean,
  ): workflow.WorkflowJob[] {
    const jobs: workflow.WorkflowJob[] = [
      new workflow.WorkflowJob(aquasec.jobs.fs_scan, {
        context: config.jobContext,
        preSteps: [
          new reusable.ReusedCommand(keeper.commands['env-export'], {
            'secret-url': config.secrets.aquaKey,
            'var-name': 'AQUA_KEY',
          }),
          new reusable.ReusedCommand(keeper.commands['env-export'], {
            'secret-url': config.secrets.aquaSecret,
            'var-name': 'AQUA_SECRET',
          }),
          new reusable.ReusedCommand(keeper.commands['env-export'], {
            'secret-url': config.secrets.githubApiToken,
            'var-name': 'GITHUB_TOKEN',
          }),
        ],
      }),
    ];
    const requires: string[] = [];

    const setupJob = SetupJob.create(dynamicConfig);
    dynamicConfig.addJob(setupJob);

    const buildBackendJob = BuildBackendJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(buildBackendJob);

    jobs.push(
      new workflow.WorkflowJob(setupJob, { name: 'Setup', context: config.jobContext }),
      new workflow.WorkflowJob(buildBackendJob, {
        name: 'Build backend',
        context: config.jobContext,
        requires: ['Setup'],
      }),
    );

    const consoleWebuiBuildJob = ConsoleWebuiBuildJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(consoleWebuiBuildJob);

    jobs.push(
      new workflow.WorkflowJob(consoleWebuiBuildJob, {
        name: 'Build APIM Console and publish image',
        context: config.jobContext,
      }),
    );

    requires.push('Build APIM Console and publish image');

    const portalWebuiBuildJob = PortalWebuiBuildJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(portalWebuiBuildJob);

    jobs.push(
      new workflow.WorkflowJob(portalWebuiBuildJob, {
        name: 'Build APIM Portal and publish image',
        context: config.jobContext,
      }),
    );
    requires.push('Build APIM Portal and publish image');

    // compute check-workflow job
    if (addValidationJob && requires.length > 0) {
      const checkWorkflowJob = new Job('job-validate-workflow-status', BaseExecutor.create('small'), [
        new commands.Run({
          name: 'Check workflow jobs',
          command: 'echo "Congratulations! If you can read this, everything is OK"',
        }),
      ]);
      dynamicConfig.addJob(checkWorkflowJob);
      jobs.push(new workflow.WorkflowJob(checkWorkflowJob, { name: 'Validate workflow status', requires }));
    }

    return jobs;
  }

  private static getE2EJobs(dynamicConfig: Config, environment: CircleCIEnvironment): workflow.WorkflowJob[] {
    const buildImagesJob = BuildBackendImagesJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(buildImagesJob);

    return [
      new workflow.WorkflowJob(buildImagesJob, {
        name: 'Build and push rest api and gateway images',
        context: config.jobContext,
        requires: ['Build backend'],
      }),
    ];
  }

  private static getMasterAndSupportJobs(dynamicConfig: Config, environment: CircleCIEnvironment): workflow.WorkflowJob[] {
    const communityBuildJob = CommunityBuildBackendJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(communityBuildJob);

    const snykApimChartsJob = SnykApimChartsJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(snykApimChartsJob);

    const publishOnArtifactoryJob = PublishJob.create(dynamicConfig, environment, 'artifactory');
    dynamicConfig.addJob(publishOnArtifactoryJob);

    const publishOnNexusJob = PublishJob.create(dynamicConfig, environment, 'nexus');
    dynamicConfig.addJob(publishOnNexusJob);

    const releaseHelmDryRunJob = ReleaseHelmJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(releaseHelmDryRunJob);

    const deployOnAzureJob = DeployOnAzureJob.create(dynamicConfig, environment);
    dynamicConfig.addJob(deployOnAzureJob);

    return [
      new workflow.WorkflowJob(communityBuildJob, { name: 'Check build as Community user', context: config.jobContext }),
      new workflow.WorkflowJob(snykApimChartsJob, { name: 'Scan snyk Helm chart', context: config.jobContext, requires: ['Setup'] }),
      new workflow.WorkflowJob(releaseHelmDryRunJob, {
        name: 'Publish Helm chart (internal repo)',
        context: config.jobContext,
        requires: ['Setup'],
      }),
      new workflow.WorkflowJob(publishOnArtifactoryJob, {
        name: 'Publish on artifactory',
        context: config.jobContext,
        requires: ['Test definition', 'Test gateway', 'Test plugins', 'Test repository', 'Test rest-api'],
      }),
      new workflow.WorkflowJob(publishOnNexusJob, {
        name: 'Publish on nexus',
        context: config.jobContext,
        requires: ['Test definition', 'Test gateway', 'Test plugins', 'Test repository', 'Test rest-api'],
      }),
      new workflow.WorkflowJob(deployOnAzureJob, {
        name: 'Deploy on Azure cluster',
        context: config.jobContext,
        requires: [
          'Test definition',
          'Test gateway',
          'Test plugins',
          'Test repository',
          'Test rest-api',
          'Build and push rest api and gateway images',
          'Build APIM Console and publish image',
          'Build APIM Portal and publish image',
        ],
      }),
    ];
  }
}
