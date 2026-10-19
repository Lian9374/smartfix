import http from 'k6/http';
import {check, sleep} from 'k6';
import {Rate, Trend} from 'k6/metrics';
const workflowErrors=new Rate('workflow_errors');
const submissionTime=new Trend('request_submission_ms',true);
const base=__ENV.BASE_URL || 'http://gateway';
export const options={
  noCookiesReset:true,
  scenarios:{maintenance:{executor:'ramping-vus',startVUs:0,stages:[{duration:__ENV.STAGE_DURATION || '20s',target:2},{duration:__ENV.STAGE_DURATION || '20s',target:5},{duration:__ENV.STAGE_DURATION || '20s',target:10},{duration:'5s',target:0}],gracefulRampDown:'10s'}},
  thresholds:{workflow_errors:['rate<0.01'],http_req_failed:['rate<0.01']},
  summaryTrendStats:['avg','med','p(95)','max'],
};
let loggedIn=false;
function csrf(response){return response.html().find('input[name="_csrf"]').first().attr('value');}
export default function(){
  if(!loggedIn){
    let page=http.get(`${base}/login`);
    let result=http.post(`${base}/login`,{username:__ENV.LOAD_USERNAME || 'swe5001.requester',password:__ENV.LOAD_PASSWORD || 'SmartFixUser2!',accountType:'REQUESTER',_csrf:csrf(page)});
    loggedIn=check(result,{'authenticated':r=>r.status===200 && !r.url.includes('/login')});
    if(!loggedIn){workflowErrors.add(true);return;}
  }
  const form=http.get(`${base}/requests/new`);
  const location=form.html().find('select[name="locationId"] option').toArray().map(x=>x.attr('value')).find(x=>/^\d+$/.test(x));
  const response=http.post(`${base}/requests`,{locationId:location,title:`Load ${__VU}-${__ITER}-${Date.now()}`,description:'SWE5001 repeatable maintenance submission workload',category:'ELECTRICAL',urgencyLevel:'MEDIUM',_csrf:csrf(form)},{redirects:0,tags:{operation:'submit_request'}});
  submissionTime.add(response.timings.duration);
  const ok=check(response,{'request committed':r=>r.status===302 && /\/requests\/SF-/.test(r.headers.Location || '')});
  workflowErrors.add(!ok);
  sleep(0.2);
}
