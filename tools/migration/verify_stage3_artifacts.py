"""Negative startup checks using only an invalid sentinel endpoint, never a protected DB."""
import json, os, subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
assert Path.cwd().resolve() == root
runtime = root / '.migration-runtime'
env = {k:v for k,v in os.environ.items() if not k.startswith(('DB_','SPRING_','SMARTFIX_','POSTGRES_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'))}
results = {}
for name, jar in [('core',root/'target/smartfix-0.0.1-SNAPSHOT.jar'),('community',root/'community-service/target/smartfix-community-0.0.1-SNAPSHOT.jar')]:
    cases = {
        'wrong_database': ['--smartfix.migration.isolated=true','--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/sentinel'],
        'h2_flyway_override': ['--spring.profiles.active=test','--spring.datasource.url=jdbc:h2:mem:unit','--spring.flyway.url=jdbc:postgresql://127.0.0.1:1/sentinel'],
    }
    for case,args in cases.items():
        p = subprocess.run(['java','-jar',str(jar),'--server.port=19093',*args],cwd=root,env=env,capture_output=True,timeout=25)
        output = p.stdout+p.stderr
        (runtime/(name+'-'+case+'-rejected.log')).write_bytes(output)
        assert p.returncode != 0 and b'MIGRATION_ISOLATION_REJECTED' in output
        assert b'HikariPool' not in output and b'Tomcat started' not in output and b'JdbcUtils.openConnection' not in output
        results[name+'_'+case+'_rejected_before_initialization'] = True
(runtime/'negative-startup-verification.json').write_text(json.dumps(results,indent=2))
print(json.dumps(results))
