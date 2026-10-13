// Offline DOM regression: a basemap failure must survive unrelated search/campus actions.
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
const elements = new Map();
function element(id) {
  if (!elements.has(id)) elements.set(id, {textContent:'', hidden:false, value:'', dataset:{},
    handlers:{}, append(){}, replaceChildren(){}, setAttribute(){},
    addEventListener(name, fn){this.handlers[name]=fn;}});
  return elements.get(id);
}
const campus = element('campus'); campus.dataset.campus='UTOWN';
const root = element('root'); root.dataset.statusUrl='/campus-map/status';
const context = {window:{smartfixCampusSnapshot:{updatedAt:'2026-10-13T00:00:00Z',buildings:[],unmappedRequests:0,unmappedFacilities:0}},
  document:{hidden:false,querySelector:()=>root,querySelectorAll:()=>[campus],getElementById:element,
    createElement:()=>element(Math.random()),addEventListener(){}},
  setTimeout:()=>1,clearTimeout(){},AbortController,Date,console};
vm.runInNewContext(readFileSync('src/main/resources/static/js/campus-map.js','utf8'),context);
const notice=element('atlas-map-notice');
assert.match(notice.textContent,/online map could not load/);
campus.handlers.click();
assert.match(notice.textContent,/online map could not load/);
element('atlas-find-form').handlers.submit({preventDefault(){}});
assert.match(notice.textContent,/online map could not load/);
element('atlas-find').value='Unknown';
element('atlas-find-form').handlers.submit({preventDefault(){}});
assert.match(notice.textContent,/Choose a full building name/);
assert.match(notice.textContent,/online map could not load/);
assert.equal(notice.hidden,false);
console.log('PASS: missing library fallback persists through campus change, empty search and failed search.');
