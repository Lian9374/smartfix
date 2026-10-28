"""Run the established full community regression against stage8, never stage6 resources."""
import json
from pathlib import Path
import stage8
stage8.stage3.safety();stage8.load();assert stage8.resources['provisioned']
saved=json.loads((stage8.R/'test-accounts-private.json').read_text());accounts=saved['accounts']
(stage8.R/'stage5-accounts-private.json').write_text(json.dumps({'password':saved['password'],'author':accounts['author'],'responder':accounts['responder']}))
(stage8.R/'stage6-accounts-private.json').write_text(json.dumps({'password':saved['password'],'author':accounts['author'],'responder':accounts['responder'],'admin1':accounts['admin'],'admin2':accounts['admin2']}))
path=Path(__file__).with_name('verify_stage6.py');code=path.read_text(encoding='utf-8')
assert "R=ROOT/'.migration-runtime'" in code
code=code.replace("R=ROOT/'.migration-runtime'","R=ROOT/'.migration-runtime/stage8'").replace('127.0.0.1:19091','127.0.0.1:19101').replace('127.0.0.1:19092','127.0.0.1:19104').replace('smartfix_migration_community','smartfix_stage8_community').replace('smartfix_migration_core','smartfix_stage8_core')
old='stage3.safety();resources=stage3.load();stage3.check_container(resources)';assert old in code
code=code.replace(old,'stage8.stage3.safety();stage8.load();resources=stage8.resources;stage8.identity()')
old="return stage3.sql(resources,query,'smartfix_stage8_community' if community else 'smartfix_stage8_core')";assert old in code
code=code.replace(old,"return stage8.sql('community' if community else 'core',query)")
offset=int(stage8.sql('community','SELECT count(*) FROM community_audit;'))-int(stage8.sql('core',"SELECT count(*) FROM audit_entries WHERE source='COMMUNITY';"))
# Imported historical audit keeps its original global identity and is deliberately not replayed.
# Require exact equality for every new projection above this fixed, measured baseline.
old="==int(sql('SELECT count(*) FROM community_audit;',True))";assert old in code
code=code.replace(old,"+"+str(offset)+old)
old="def app(action,service):subprocess.run(['python',str(ROOT/'tools/migration/stage3.py'),action,service],check=True)";assert old in code
code=code.replace(old,"""def app(action,service):
    assert service=='community'
    for name in ('a','b'):
        stage8.load()
        if action=='stop':stage8.stop(name)
        else:stage8.start(name,variant=(name=='a'))
    time.sleep(2)
""")
code=code.replace('(19090,19091,19092)','(19090,19101,19104)')
exec(compile(code,str(path),'exec'),{'__name__':'__main__','__file__':str(path),'stage8':stage8})
