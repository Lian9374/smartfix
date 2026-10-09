(function () {
  'use strict';
  var scope = document.querySelector('.community-detail-page');
  if (!scope) { return; }
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
