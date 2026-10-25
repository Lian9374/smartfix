"""Opt-in LOCAL registry protocol integration check, NOT a SmartFix release.

Uses a public Java base plus a constant fixture marker; never copies a JAR or
private project data. Temporary registry binds only loopback and uses tmpfs.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import secrets
import tempfile
import time
from urllib.request import ProxyHandler, build_opener

import container_verify as cv
from container_release import push_digest, remote_digest


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    output=args.output.resolve()
    output.mkdir(parents=True,exist_ok=True)
    suffix=secrets.token_hex(6)
    registry='sf-registry-fixture-'+suffix
    network=registry+'-net'
    runner=registry+'-check'
    source='smartfix-registry-fixture:'+suffix
    refs=[source]
    h=cv.Harness(output,'sf-verify-'+suffix,'smartfix-verification:sha-'+'a'*40+'-'+suffix,[])
    report=dict(status='FAIL',scope='SYNTHETIC_LOCAL_REGISTRY_PROTOCOL_ONLY_NOT_SMARTFIX_RELEASE',contains_smartfix_jar=False,workflow_run=None)
    phase='fixture-build-and-local-test'
    network_created=registry_created=False
    try:
        with tempfile.TemporaryDirectory(prefix='registry-fixture-context-') as folder:
            context=Path(folder)
            dockerfile='FROM eclipse-temurin:21-jre\nRUN printf registry-protocol-fixture > /fixture-marker && test "$(cat /fixture-marker)" = registry-protocol-fixture\nLABEL com.smartfix.registry-fixture="true"\n'
            (context/'Dockerfile').write_text(dockerfile,encoding='utf-8')
            report['fixture_source_sha256']=hashlib.sha256(dockerfile.encode()).hexdigest()
            h.command(['docker','build','-t',source,str(context)],'fixture-build',timeout=180)
        check=['sh','-c','test "$(cat /fixture-marker)" = registry-protocol-fixture']
        h.command(['docker','run','--rm','--network','none','--name',runner,source,*check],'fixture-check-before-push')
        report['fixture_test_before_push']='PASS'
        phase='start-loopback-disposable-registry'
        h.command(['docker','network','create',network],'registry-network')
        network_created=True
        h.command(['docker','run','-d','--name',registry,'--network',network,'--label','com.smartfix.registry-fixture='+suffix,'--publish','127.0.0.1::5000','--tmpfs','/var/lib/registry:rw,mode=1777','registry:3'],'registry-start',timeout=180)
        registry_created=True
        binding=h.command(['docker','port',registry,'5000/tcp'],'registry-port').stdout.strip()
        if not re.fullmatch(r'127\.0\.0\.1:\d+',binding): raise ValueError('Registry is not loopback-only')
        opener=build_opener(ProxyHandler({}))
        deadline=time.monotonic()+60
        while True:
            try:
                with opener.open('http://'+binding+'/v2/',timeout=2) as response:
                    if response.status==200: break
            except OSError: pass
            if time.monotonic()>=deadline: raise TimeoutError('Registry readiness timed out')
            time.sleep(1)
        repo=binding+'/protocol-fixture'
        tag=repo+':fixture-'+suffix
        refs.append(tag)
        phase='push-fixture-and-confirm-registry-digest'
        h.command(['docker','tag',source,tag],'fixture-tag')
        pushed=h.command(['docker','push',tag],'fixture-push',timeout=180)
        digest=push_digest(pushed.stdout+pushed.stderr)
        reference=repo+'@'+digest
        refs.append(reference)
        if remote_digest(h,reference)!=digest: raise ValueError('Remote digest mismatch')
        report.update(fixture_image_tag=tag,registry_digest=digest,digest_reference=reference,push_and_remote_manifest='PASS')
        # Remove the local names before pull: a local image ID/tag is not evidence
        # of a registry round trip. Base-layer cache may still be reused by Docker.
        h.command(['docker','image','rm',tag,source],'remove-local-fixture-before-pull')
        phase='pull-by-digest-and-run-fixture-without-rebuild'
        h.command(['docker','pull',reference],'fixture-pull-by-digest',timeout=180)
        h.command(['docker','run','--rm','--network','none','--name',runner,reference,*check],'fixture-check-after-digest-pull')
        report.update(pull_by_digest='PASS',fixture_execution_after_pull='PASS',rebuild_after_push=False,status='PASS')
    except Exception as error:
        report['failure']=dict(phase=phase,error_type=type(error).__name__)
    finally:
        clean=True
        if registry_created:
            logs=h.command(['docker','logs',registry],'registry-containers',required=False)
            clean=logs.returncode==0 and clean
            clean=h.command(['docker','rm','--force','--volumes',registry],'remove-registry',required=False).returncode==0 and clean
        # Remove only this invocation's exact fixture references.
        for index,reference in enumerate(refs):
            try:
                if h.has_image(reference):
                    clean=h.command(['docker','image','rm',reference],'remove-fixture-'+str(index),required=False).returncode==0 and clean
            except Exception: clean=False
        if network_created:
            clean=h.command(['docker','network','rm',network],'remove-registry-network',required=False).returncode==0 and clean
        remaining=h.command(['docker','ps','-aq','--filter','label=com.smartfix.registry-fixture='+suffix],'remaining-registry-containers',required=False)
        clean=remaining.returncode==0 and not remaining.stdout.strip() and clean
        report['cleanup']=dict(status='PASS' if clean else 'FAIL',registry_tmpfs_data_removed=clean,registry_network_removed=clean,fixture_image_references_removed=clean)
        if not clean: report['status']='FAIL'
        h.temp.cleanup()
        cv.save(output/'result.json',report)
    print('LOCAL FIXTURE registry protocol: '+report['status'])
    return 0 if report['status']=='PASS' else 1


if __name__=='__main__':
    raise SystemExit(main())
