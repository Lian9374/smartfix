(() => {
  'use strict';
  if (!document.querySelector('.workspace-page')) return;
  const loading = document.createElement('div');
  loading.className = 'workspace-loading'; loading.hidden = true;
  loading.setAttribute('role', 'status'); loading.setAttribute('aria-live', 'polite');
  document.body.append(loading);
  document.querySelectorAll('textarea[maxlength]').forEach(input => {
    const counter = document.createElement('span');
    counter.className = 'form-field__hint workspace-counter';
    counter.id = input.id + '-count';
    const update = () => { counter.textContent = input.value.length + ' / ' + input.maxLength; };
    input.after(counter);
    input.setAttribute('aria-describedby', [input.getAttribute('aria-describedby'), counter.id].filter(Boolean).join(' '));
    input.addEventListener('input', update); update();
  });
  document.addEventListener('submit', event => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    if (form.dataset.pending === 'true') { event.preventDefault(); return; }
    if (form.dataset.confirm && !window.confirm(form.dataset.confirm)) { event.preventDefault(); return; }
    form.dataset.pending = 'true'; form.setAttribute('aria-busy', 'true');
    loading.textContent = form.method.toLowerCase() === 'get' ? 'Loading results…' : 'Saving…';
    loading.hidden = false;
    // Keep the submitter enabled: dispatch buttons carry the selected technician ID.
    if (event.submitter) event.submitter.setAttribute('aria-disabled', 'true');
  });
  window.addEventListener('pageshow', () => {
    loading.hidden = true;
    document.querySelectorAll('form[data-pending]').forEach(form => {
      delete form.dataset.pending; form.removeAttribute('aria-busy');
      form.querySelectorAll('[aria-disabled]').forEach(button => button.removeAttribute('aria-disabled'));
    });
  });
  const invalid = document.querySelector('[aria-invalid="true"]');
  const error = document.querySelector('[role="alert"]');
  if (invalid) invalid.focus();
  else if (error) { error.setAttribute('tabindex', '-1'); error.focus(); }
})();
