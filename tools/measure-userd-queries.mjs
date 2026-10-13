// Compare the old entity-shaped read and grouped map read in an isolated rollback transaction.
import {execFileSync} from 'node:child_process';
import {mkdirSync,writeFileSync} from 'node:fs';
import path from 'node:path';
const jdbc=process.env.ROLE_ACCEPTANCE_DB_URL||'';
if(!/^jdbc:postgresql:\/\/(127\.0\.0\.1|localhost):\d+\/smartfix_role_acceptance_test$/.test(jdbc))throw Error('Dedicated local acceptance database required.');
const url=new URL(jdbc.slice(5));
const sql=[`BEGIN; SET LOCAL statement_timeout='10s';
INSERT INTO facilities(location_id,name,status,created_at,updated_at)
SELECT l.id,'USERD-PERF-'||n,CASE ((n-1)/12)%3 WHEN 0 THEN 'OPERATIONAL' WHEN 1 THEN 'UNDER_MAINTENANCE' ELSE 'OUT_OF_SERVICE' END,now(),now()
FROM generate_series(1,600) n CROSS JOIN LATERAL (SELECT id FROM locations WHERE active=true ORDER BY id LIMIT 1 OFFSET ((n-1)%12)) l;`];
for(let i=0;i<6;i++){
  sql.push(`SELECT 'USERD_BASELINE_${i}'; EXPLAIN (ANALYZE, FORMAT JSON) SELECT f.*,l.* FROM facilities f JOIN locations l ON l.id=f.location_id WHERE l.active=true ORDER BY f.name,f.id;`);
  sql.push(`SELECT 'USERD_GROUPED_${i}'; EXPLAIN (ANALYZE, FORMAT JSON) SELECT f.location_id,f.status,count(*) FROM facilities f JOIN locations l ON l.id=f.location_id WHERE l.active=true GROUP BY f.location_id,f.status;`);
}
sql.push('ROLLBACK;');
const output=execFileSync('C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe',['-h',url.hostname,'-p',url.port,'-U',process.env.TEST_DB_USERNAME||'smartfix','-d','smartfix_role_acceptance_test','-v','ON_ERROR_STOP=1','-Atq','-c',sql.join('\n')],{encoding:'utf8',windowsHide:true,env:{...process.env,PGPASSWORD:process.env.TEST_DB_PASSWORD||''}});
const measurements=[];
for(const match of output.matchAll(/USERD_(BASELINE|GROUPED)_(\d+)\s*([\s\S]*?)(?=USERD_|$)/g)){
  const plan=JSON.parse(match[3].trim())[0];
  measurements.push({method:match[1],trial:Number(match[2]),warmup:match[2]==='0',rows:plan.Plan['Actual Rows'],planningMilliseconds:plan['Planning Time'],executionMilliseconds:plan['Execution Time']});
}
if(measurements.length!==12)throw Error('Incomplete query measurements');
const out=path.resolve(process.env.ROLE_ACCEPTANCE_OUTPUT||'docs/sprint4/evidence/userd/roles');mkdirSync(out,{recursive:true});
writeFileSync(path.join(out,'query-measurements.json'),JSON.stringify({checkedAt:new Date().toISOString(),database:'PostgreSQL 16, isolated local acceptance database',fixtureRows:600,fixtureLocations:12,cleanup:'ROLLBACK; no fixture changes persisted',method:'Alternating reads: one warmup plus five samples per query. EXPLAIN ANALYZE server times only; excludes entity hydration, network transfer and browser rendering.',measurements},null,2));
console.log('PASS: twelve PostgreSQL query measurements; fixture transaction rolled back.');
