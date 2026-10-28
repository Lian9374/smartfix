(function () {
  'use strict';
  var form = document.querySelector('[data-community-search]');
  if (!form) return;
  var input = form.querySelector('input[type="search"]');
  var button = form.querySelector('button[type="submit"]');
  var wrapper = document.createElement('div');
  wrapper.className = 'community-search-input';
  input.before(wrapper);
  wrapper.append(input);
  var clear = document.createElement('button');
  clear.type = 'button';
  clear.className = 'community-search-clear';
  clear.setAttribute('aria-label', 'Clear search text');
  clear.textContent = '×';
  wrapper.append(clear);
  var sync = function () { clear.hidden = !input.value; };
  clear.addEventListener('click', function () {
    input.value = '';
    input.dispatchEvent(new Event('input', {bubbles: true}));
    input.focus();
  });
  input.addEventListener('input', sync);
  document.addEventListener('keydown', function (event) {
    var target = event.target;
    if (event.key !== '/' || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey || event.isComposing ||
        target.closest('input, textarea, select, button, [contenteditable="true"]')) return;
    event.preventDefault(); input.focus();
  });
  input.setAttribute('aria-keyshortcuts', '/');
  form.addEventListener('submit', function (event) {
    if (event.defaultPrevented) return;
    if (form.dataset.pending === 'true') { event.preventDefault(); return; }
    form.dataset.pending = 'true';
    form.setAttribute('aria-busy', 'true');
    button.disabled = true;
    button.setAttribute('aria-label', 'Searching discussions');
  });
  window.addEventListener('pageshow', function () {
    delete form.dataset.pending; form.removeAttribute('aria-busy');
    button.disabled = false; button.setAttribute('aria-label', 'Search discussions'); sync();
  });
  sync();
}());
