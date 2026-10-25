"""Synthetic publication/provenance/privacy gates; no registry credentials."""
import argparse
import copy
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import container_release as release


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.event = self.root/'event.json'
        self.event.write_text(json.dumps(dict(inputs=dict(container_release=True,deploy=False))))
        self.environment = patch.dict(os.environ, dict(GITHUB_REPOSITORY='Lian9374/smartfix',GITHUB_REF='refs/heads/main',GITHUB_EVENT_NAME='workflow_dispatch',GITHUB_EVENT_PATH=str(self.event),GITHUB_SHA='a'*40,GITHUB_RUN_ID='999',GITHUB_RUN_ATTEMPT='1',GITHUB_WORKFLOW='SmartFix SWE5006 CI',CONTAINER_IMAGE='ghcr.io/lian9374/fixture-private'))
        self.environment.start()
        self.addCleanup(self.environment.stop)
        self.identity=release.cv.ci.identity()
        self.run=dict(head_sha='a'*40,head_branch='main',event='workflow_dispatch',run_attempt=1,path='.github/workflows/smartfix-ci.yml')
        self.jobs=dict(total_count=4,jobs=[dict(name=name,id=i,status='completed',conclusion='success',head_sha='a'*40) for i,name in enumerate(release.REQUIRED_JOBS)])

    def verified(self):
        return dict(status='PASS',build_status='PASS',runtime_status='PASS',readiness=dict(health_status='UP',http_status=200),diagnostics=dict(status='PASS'),cleanup=dict(status='PASS',remaining=dict(containers=0,networks=0,volumes=0),image_retained=True),checks=[dict(name=name,status='PASS',http_status=200,expected_status=200,assertions=dict(http_status=True)) for name in release.cv.SMOKE_CHECKS],input=dict(**{key:self.identity[key] for key in ('commit_sha','run_id','run_attempt','workflow_run','application_version')},origin='GITHUB_ACTIONS_BUILD_ARTIFACT',jar_sha256='b'*64),running_jar_sha256='b'*64)

    def receipt(self):
        repo=release.repository()
        return dict(**self.identity,status='PUBLISHED',purpose='CONTAINER_RELEASE_AND_EPHEMERAL_TEST_ONLY',image_tag=repo+':sha-'+'a'*40,image_ref=repo+'@sha256:'+'b'*64,registry_digest='sha256:'+'b'*64,jar_sha256='c'*64,required_checks=release.checked_jobs(self.run,self.jobs,self.identity),verified_image_id='sha256:'+'d'*64,verification_receipt_sha256='e'*64)

    def test_required_jobs_pass_with_exact_attempt_and_source(self):
        self.assertEqual(4,len(release.checked_jobs(self.run,self.jobs,self.identity)))

    def test_each_failed_required_job_blocks_publication(self):
        for i in range(4):
            jobs=copy.deepcopy(self.jobs)
            jobs['jobs'][i]['conclusion']='failure'
            with self.assertRaises(release.Blocked): release.checked_jobs(self.run,jobs,self.identity)

    def test_missing_skipped_duplicate_or_incomplete_job_list_fails(self):
        for jobs in (dict(total_count=0,jobs=[]),dict(total_count=101,jobs=self.jobs['jobs']),dict(total_count=5,jobs=self.jobs['jobs']+[self.jobs['jobs'][0]])):
            with self.assertRaises(release.Blocked): release.checked_jobs(self.run,jobs,self.identity)

    def test_wrong_commit_run_attempt_or_workflow_fails(self):
        for field,value in dict(head_sha='b'*40,run_attempt=2,path='.github/workflows/untrusted.yml',event='pull_request').items():
            run=dict(self.run,**{field:value})
            with self.assertRaises(release.Blocked): release.checked_jobs(run,self.jobs,self.identity)

    def test_pr_and_non_main_cannot_publish(self):
        for values in (dict(GITHUB_EVENT_NAME='pull_request'),dict(GITHUB_REF='refs/heads/feature')):
            with patch.dict(os.environ,values),self.assertRaises(release.Blocked): release.authorize()

    def test_two_deployment_modes_cannot_be_selected_together(self):
        self.event.write_text(json.dumps(dict(inputs=dict(container_release=True,deploy=True))))
        with self.assertRaises(release.Blocked): release.authorize()

    def test_existing_private_package_is_required(self):
        base=dict(id=1,name='fixture-private',package_type='container',owner=dict(login='Lian9374'))
        for visibility in ('public','internal',None):
            with patch.object(release,'api',return_value=dict(base,visibility=visibility)),self.assertRaises(release.Blocked): release.private_package(release.repository())
        with patch.object(release,'api',return_value=dict(base,visibility='private')):
            self.assertEqual('private',release.private_package(release.repository())['visibility'])

    def test_403_or_missing_package_cannot_be_reported_ready(self):
        with patch.object(release,'required_checks',return_value=[]),patch.object(release,'private_package',side_effect=release.Blocked('PERMISSION_FAILURE',403)):
            self.assertEqual(1,release.preflight(argparse.Namespace(output=self.root)))
        data=json.loads((self.root/'preflight.json').read_text())
        self.assertEqual('FAIL',data['status'])
        self.assertEqual(403,data['failure']['http_status'])

    def test_unknown_registry_or_mutable_reference_is_not_an_allowed_repository(self):
        for value in ('docker.io/user/image','ghcr.io/other/image','ghcr.io/lian9374/image:latest',''):
            with patch.dict(os.environ,CONTAINER_IMAGE=value),self.assertRaises(release.Blocked): release.repository()

    def test_local_dirty_worktree_proof_cannot_authorize_ci_publication(self):
        data=self.verified()
        data['input']['origin']='LOCAL_WORKTREE_NOT_CI'
        with self.assertRaises(release.Blocked): release.verified_image(data,self.identity)

    def test_smoke_failure_or_different_jar_fails(self):
        data=self.verified()
        data['checks'][0]['status']='FAIL'
        with self.assertRaises(release.Blocked): release.verified_image(data,self.identity)
        data=self.verified()
        data['running_jar_sha256']='c'*64
        with self.assertRaises(release.Blocked): release.verified_image(data,self.identity)

    def test_missing_duplicated_smoke_checks_or_empty_assertions_fail(self):
        for mutation in ('duplicate','empty','health','cleanup'):
            data=self.verified()
            if mutation=='duplicate': data['checks'][0]['name']=data['checks'][1]['name']
            if mutation=='empty': data['checks'][0]['assertions']={}
            if mutation=='health': data['readiness']['health_status']='DOWN'
            if mutation=='cleanup': data['cleanup']['remaining']['volumes']=1
            with self.assertRaises(release.Blocked): release.verified_image(data,self.identity)

    def test_push_digest_must_be_returned_by_the_push(self):
        self.assertEqual('sha256:'+'a'*64,release.push_digest('sha-fixture: digest: sha256:'+'a'*64+' size: 100'))
        for value in ('image build succeeded','sha256:'+'a'*64,'digest: sha256:'+'a'*64+'\ndigest: sha256:'+'b'*64):
            with self.assertRaises(release.Blocked): release.push_digest(value)

    def test_remote_digest_must_be_a_real_manifest_digest_field(self):
        h=Mock()
        h.command.return_value.stdout=json.dumps(dict(digest='sha256:'+'a'*64))
        self.assertEqual('sha256:'+'a'*64,release.remote_digest(h,'ghcr.io/lian9374/fixture@sha256:'+'a'*64))
        h.command.return_value.stdout=json.dumps(dict(id='sha256:'+'a'*64))
        with self.assertRaises(release.Blocked): release.remote_digest(h,'unused')

    def test_deployment_requires_digest_and_exact_release_identity(self):
        data=self.receipt()
        release.identity_release(data,self.identity,release.repository())
        for field,value in dict(image_ref=release.repository()+':latest',registry_digest='sha256:bad',run_attempt='2',commit_sha='b'*40,status='FAIL').items():
            bad=dict(data,**{field:value})
            with self.assertRaises((release.Blocked,ValueError)): release.identity_release(bad,self.identity,release.repository())

    def test_failed_quality_check_never_reaches_docker_login_or_push(self):
        with patch.object(release,'required_checks',side_effect=release.Blocked('REQUIRED_TEST_OR_QUALITY_JOB_NOT_SUCCESSFUL')),patch.object(release.cv,'Harness') as docker:
            self.assertEqual(1,release.publish(argparse.Namespace(output=self.root,verified=self.root)))
            docker.assert_not_called()
        self.assertFalse((self.root/'release.json').exists())
        self.assertFalse(json.loads((self.root/'publish-result.json').read_text())['push_observed'])

    def test_deploy_rejects_mutable_reference_before_docker(self):
        data=self.receipt()
        data['image_ref']=release.repository()+':sha-'+'a'*40
        path=self.root/'release.json'
        path.write_text(json.dumps(data))
        with patch.object(release.cv,'Harness') as docker:
            self.assertEqual(1,release.test_deploy(argparse.Namespace(output=self.root,release=path,readiness_timeout=180)))
            docker.assert_not_called()


if __name__ == '__main__':
    unittest.main()
