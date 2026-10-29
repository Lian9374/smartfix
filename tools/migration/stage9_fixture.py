"""Fixed synthetic corpus, inserted only into stage9 clones. Not a performance result."""
import json
import stage9 as env

def seed():
    env.load();assert not env.state['services'],'Seed with apps stopped'
    assert env.sql('core','SELECT count(*) FROM users WHERE id BETWEEN 1000000 AND 1000999;')=='0'
    assert env.sql('community','SELECT count(*) FROM community_questions WHERE id BETWEEN 1000000 AND 1009999;')=='0'
    env.sql('core',"""INSERT INTO users(id,username,display_name,password_hash,role,account_status,security_version,password_change_required,created_at,updated_at)
      SELECT 1000000+i,'perf9_'||i,'Performance User '||i,(SELECT password_hash FROM users WHERE id=(SELECT min(id) FROM users WHERE username LIKE 's8_responder_%')),
      'REQUESTER','ACTIVE',0,false,'2026-01-01T00:00:00Z','2026-01-01T00:00:00Z' FROM generate_series(0,999) i;
      SELECT setval(pg_get_serial_sequence('users','id'),(SELECT max(id) FROM users));""")
    env.sql('community',"""INSERT INTO community_questions(id,author_id,title,body,category,status,created_at,updated_at,version)
      SELECT 1000000+i,1000000+(i%1000),'Perf9 question '||i,'Perf9 fixed synthetic discussion body number '||i,'OTHER','VISIBLE','2026-01-01T00:00:00Z'::timestamptz+i*interval '1 second','2026-01-01T00:00:00Z',0 FROM generate_series(0,9999) i;
      INSERT INTO community_answers(id,question_id,author_id,body,status,created_at,updated_at,version)
      SELECT 1000000+i,1000000+(i/3),1000000+((i+1)%1000),'Perf9 fixed synthetic answer number '||i,'VISIBLE','2026-01-02T00:00:00Z','2026-01-02T00:00:00Z',0 FROM generate_series(0,29999) i;
      SELECT setval(pg_get_serial_sequence('community_questions','id'),(SELECT max(id) FROM community_questions));
      SELECT setval(pg_get_serial_sequence('community_answers','id'),(SELECT max(id) FROM community_answers));""")
    for key in env.DBS:env.sql(key,'ANALYZE;')
    result={'syntheticUsers':1000,'syntheticQuestions':10000,'syntheticAnswers':30000,'seedVersion':1,'questionStart':1000000,'userStart':1000000}
    (env.R/'fixture.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
if __name__=='__main__':seed()
