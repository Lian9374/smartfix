(function () {
  'use strict';
  var scope = document.querySelector('.community-detail-page');
  if (!scope) { return; }
  var pendingMessage = document.createElement('p');
  pendingMessage.className = 'form-field__hint';
  pendingMessage.setAttribute('role', 'status');
  pendingMessage.hidden = true;
  scope.append(pendingMessage);
  scope.addEventListener('submit', function (event) {
    var form = event.target;
    if (!(form instanceof HTMLFormElement) || form.method.toLowerCase() !== 'post') { return; }
    if (form.dataset.pending === 'true') { event.preventDefault(); return; }
    // Confirmation handlers elsewhere in the page retain control of cancellation.
    if (event.defaultPrevented) { return; }
    form.dataset.pending = 'true';
    form.setAttribute('aria-busy', 'true');
    pendingMessage.textContent = 'Saving…';
    pendingMessage.hidden = false;
    // Keep name/value submitters enabled; do not remove fields from the POST.
  });
  window.addEventListener('pageshow', function () {
    pendingMessage.hidden = true;
    scope.querySelectorAll('form[data-pending]').forEach(function (form) {
      delete form.dataset.pending;
      form.removeAttribute('aria-busy');
    });
  });
  scope.querySelectorAll('textarea[maxlength], input[data-character-count]').forEach(function (input, index) {
    var counter = document.createElement('span');
    counter.className = 'form-character-count';
    counter.id = (input.id || 'community-field-' + index) + '-count';
    var update = function () {
      counter.textContent = input.value.length.toLocaleString('en') + ' / ' + input.maxLength.toLocaleString('en');
    };
    input.setAttribute('aria-describedby', ((input.getAttribute('aria-describedby') || '') + ' ' + counter.id).trim());
    var field = input.closest('.form-field');
    var hint = field && field.querySelector('.form-field__hint');
    if (hint) {
      var metadata = document.createElement('span');
      metadata.className = 'form-field__meta';
      input.insertAdjacentElement('afterend', metadata);
      metadata.append(hint, counter);
    } else {
      input.insertAdjacentElement('afterend', counter);
    }
    input.addEventListener('input', update);
    input.addEventListener('change', update);
    // Browsers may restore form contents on back/forward navigation.
    window.addEventListener('pageshow', update);
    update();
  });
  var invalid = scope.querySelector('[aria-invalid="true"]');
  var error = scope.querySelector('.alert--error, [role="alert"]');
  if (invalid) { invalid.focus(); }
  else if (error) { error.tabIndex = -1; error.focus(); }
}());
