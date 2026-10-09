/* NUS online map: public geographic points + authenticated building-level aggregates. */
(() => {
  'use strict';
  const root = document.querySelector('.campus-atlas');
  if (!root || !window.smartfixCampusSnapshot) return;
  const element = document.getElementById('nus-online-map');
  const notice = document.getElementById('atlas-map-notice');
  const sync = document.getElementById('atlas-sync');
  const campuses = {
    KENT_RIDGE: {center: [1.2974, 103.7752], zoom: 16},
    UTOWN: {center: [1.306, 103.7732], zoom: 17},
    BUKIT_TIMAH: {center: [1.3193, 103.817], zoom: 18},
    OUTRAM: {center: [1.28107, 103.83413], zoom: 18}
  };
  let snapshot = window.smartfixCampusSnapshot;
  let map, markers, selectedCampus = 'KENT_RIDGE';
  let timer, pending, expired = false;
  const fault = b => b.faultReports > 0 || b.outOfServiceFacilities > 0;
  const repair = b => b.repairs > 0 || b.maintenanceFacilities > 0;
  const color = b => fault(b) ? '#c45a4f' : repair(b) ? '#bf852d' : '#6a8881';
  const normalize = s => s.toLowerCase().replace(/[^a-z0-9]/g, '');
  const text = (tag, value, className) => {
    const node = document.createElement(tag); node.textContent = value;
    if (className) node.className = className; return node;
  };
  function description(b) {
    return [b.faultReports && `${b.faultReports} outstanding report${b.faultReports === 1 ? '' : 's'}`,
      b.repairs && `${b.repairs} repair${b.repairs === 1 ? '' : 's'} in progress`,
      b.outOfServiceFacilities && `${b.outOfServiceFacilities} facilit${b.outOfServiceFacilities === 1 ? 'y' : 'ies'} out of service`,
      b.maintenanceFacilities && `${b.maintenanceFacilities} facilit${b.maintenanceFacilities === 1 ? 'y' : 'ies'} under maintenance`]
      .filter(Boolean).join(' · ');
  }
  function showNotice(message) { notice.textContent = message; notice.hidden = !message; }
  function draw() {
    if (!map) return;
    markers.clearLayers();
    const zoom = map.getZoom();
    const area = snapshot.buildings.filter(b => b.campus === selectedCampus);
    for (const b of area.sort((a,b) => Number(fault(a) || repair(a)) - Number(fault(b) || repair(b)))) {
      const active = fault(b) || repair(b);
      const marker = L.circleMarker([b.latitude, b.longitude], {
        radius: active ? 9 : zoom >= 18 ? 4 : 3, color: active ? '#ffffff' : '#f8fbfa',
        weight: active ? 3 : 1, fillColor: color(b), fillOpacity: active ? 1 : .75,
        interactive: false
      }).addTo(markers);
      // Status labels are permanently visible; markers have no click actions/popups.
      if (active || zoom >= 18) {
        const label = document.createElement('div');
        label.append(text('strong', b.name));
        if (active) label.append(text('span', description(b)));
        marker.bindTooltip(label, {permanent:true, direction:'top', offset:[0, active ? -10 : -4],
          className: active ? 'atlas-label atlas-label--active' : 'atlas-label', interactive:false});
      }
    }
  }
  function renderActivity() {
    document.getElementById('atlas-fault-count').textContent = snapshot.buildings.filter(fault).length;
    document.getElementById('atlas-repair-count').textContent = snapshot.buildings.filter(repair).length;
    const list = document.getElementById('atlas-activity-list');
    const active = snapshot.buildings.filter(b => fault(b) || repair(b));
    list.replaceChildren();
    if (!active.length) list.append(text('p', 'No active faults or repairs recorded on the map.', 'atlas-no-activity'));
    for (const b of active) {
      const item = text('article', '', 'atlas-activity-item');
      const dot = text('i', '', `atlas-dot atlas-dot--${fault(b) ? 'fault' : 'repair'}`);
      dot.setAttribute('aria-hidden', 'true');
      const body = document.createElement('div');
      body.append(text('h4', b.name), text('p', description(b)));
      item.append(dot, body); list.append(item);
    }
    const unmapped = document.getElementById('atlas-unmapped');
    unmapped.hidden = snapshot.unmappedRequests === 0 && snapshot.unmappedFacilities === 0;
    unmapped.textContent = `Not positioned: ${snapshot.unmappedRequests} active reports · ${snapshot.unmappedFacilities} facilities.`;
  }
  function selectCampus(campus, focus) {
    selectedCampus = campus;
    document.querySelectorAll('[data-campus]').forEach(button => button.setAttribute('aria-pressed', String(button.dataset.campus === campus)));
    if (map) { const view = campuses[campus]; map.setView(focus || view.center, focus ? 19 : view.zoom); draw(); }
  }
  if (window.L) {
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    map = L.map(element, {minZoom:11, maxZoom:19, scrollWheelZoom:false,
      maxBounds:[[1.144,103.535],[1.494,104.502]], maxBoundsViscosity:1,
      zoomAnimation:!reduceMotion, fadeAnimation:!reduceMotion}).setView(campuses.KENT_RIDGE.center, 16);
    map.attributionControl.setPrefix(false);
    const tiles = L.tileLayer('https://www.onemap.gov.sg/maps/tiles/Grey/{z}/{x}/{y}.png', {
      minZoom:11, maxZoom:19, attribution:'<a href="https://www.onemap.gov.sg/" target="_blank" rel="noopener"><img src="https://www.onemap.gov.sg/docs/maps/images/oneMap64-01.png" alt="OneMap" height="20"></a> &copy; <a href="https://www.sla.gov.sg/" target="_blank" rel="noopener">Singapore Land Authority</a>'
    }).addTo(map);
    let tileErrors = 0, loadedTiles = 0;
    tiles.on('loading', () => {tileErrors = 0;loadedTiles = 0;});
    tiles.on('tileload', () => {loadedTiles++;});
    tiles.on('tileerror', () => { if (++tileErrors >= 3) showNotice('The online basemap is unavailable. Current statuses and the building directory are still shown.'); });
    tiles.on('load', () => {
      if (loadedTiles > 0 && tileErrors === 0) showNotice('');
    });
    markers = L.layerGroup().addTo(map);
    L.control.scale({imperial:false, position:'bottomleft'}).addTo(map);
    map.on('zoomend', draw);
    new ResizeObserver(() => map.invalidateSize()).observe(element);
    draw();
  } else {
    showNotice('The online map could not load. Current statuses and the building directory are still shown.');
  }
  document.querySelectorAll('[data-campus]').forEach(button => button.addEventListener('click', () => {showNotice('');selectCampus(button.dataset.campus);}));
  document.getElementById('atlas-find-form').addEventListener('submit', event => {
    event.preventDefault();
    const term = document.getElementById('atlas-find').value.trim();
    if (!term) {selectCampus(selectedCampus);showNotice('');return;}
    const matches = snapshot.buildings.filter(b => [b.name,...b.aliases].some(name => normalize(name) === normalize(term)));
    if (matches.length !== 1) {showNotice('Choose a full building name from the suggestions to locate it.');return;}
    const building = matches[0];
    showNotice(''); selectCampus(building.campus, [building.latitude,building.longitude]);
  });
  renderActivity();
  function validSnapshot(value) {
    return value && !Number.isNaN(Date.parse(value.updatedAt)) && Array.isArray(value.buildings)
      && value.buildings.length > 0 && ['unmappedRequests','unmappedFacilities'].every(k => Number.isSafeInteger(value[k]) && value[k] >= 0)
      && value.buildings.every(b => typeof b.id === 'string' && typeof b.name === 'string' && campuses[b.campus]
        && Number.isFinite(b.latitude) && Number.isFinite(b.longitude) && Array.isArray(b.aliases)
        && ['faultReports','repairs','outOfServiceFacilities','maintenanceFacilities','operationalFacilities'].every(k => Number.isSafeInteger(b[k]) && b[k] >= 0));
  }
  function schedule() { clearTimeout(timer); if (!expired && !document.hidden) timer = setTimeout(refresh, 30000); }
  async function refresh() {
    if (pending || expired || document.hidden) return;
    pending = new AbortController();
    const timeout = setTimeout(() => pending?.abort(), 10000);
    try {
      const response = await fetch(root.dataset.statusUrl, {credentials:'same-origin', cache:'no-store',
        headers:{Accept:'application/json'}, signal:pending.signal});
      if (response.redirected || response.status === 401 || response.status === 403) {
        expired = true; sync.textContent = 'Updates paused. Sign in again to refresh.';
        root.classList.add('atlas--stale'); return;
      }
      if (!response.ok || !response.headers.get('content-type')?.includes('application/json')) throw new Error('Status unavailable');
      const next = await response.json();
      if (!validSnapshot(next)) throw new Error('Invalid status response');
      snapshot = next; draw(); renderActivity();
      sync.textContent = `Updated ${new Date(snapshot.updatedAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})} · every 30s`;
      root.classList.remove('atlas--stale');
    } catch (error) {
      if (!document.hidden) {
        sync.textContent = `Update unavailable · showing ${new Date(snapshot.updatedAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'})} data`;
        root.classList.add('atlas--stale');
      }
    } finally {clearTimeout(timeout);pending = null;schedule();}
  }
  document.addEventListener('visibilitychange', () => {
    clearTimeout(timer); if (document.hidden) pending?.abort(); else refresh();
  });
  schedule();
})();
