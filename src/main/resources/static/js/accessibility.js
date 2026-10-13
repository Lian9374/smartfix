(function () {
  'use strict';
  var main = document.getElementById('main-content');
  var skip = document.querySelector('.skip-link');
  if (!main || !skip) { return; }
  // Native anchor navigation remains available when JavaScript is disabled.
  main.setAttribute('tabindex', '-1');
  skip.addEventListener('click', function () { main.focus(); });
}());
