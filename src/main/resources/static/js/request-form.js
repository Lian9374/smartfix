(function () {
  'use strict';
  var form = document.querySelector('[data-request-form]');
  if (!form) { return; }
  document.querySelectorAll('[data-count-for]').forEach(function (counter) {
    var input = document.getElementById(counter.dataset.countFor);
    function update() { counter.textContent = input.value.length + ' / ' + input.maxLength; }
    input.addEventListener('input', update);
    update();
  });
  var files = document.getElementById('files');
  var error = document.getElementById('files-error');
  var list = document.querySelector('[data-photo-list]');
  var urls = [];
  function releasePreviews() {
    urls.forEach(function (url) { URL.revokeObjectURL(url); });
    urls = [];
    list.replaceChildren();
  }
  function validatePhotos() {
    var selected = Array.from(files.files);
    var message = '';
    if (selected.length > Number(files.dataset.maxFiles)) {
      message = 'Choose up to ' + files.dataset.maxFiles + ' photos.';
    } else if (selected.some(function (file) { return !['image/png', 'image/jpeg'].includes(file.type); })) {
      message = 'Choose PNG or JPEG images only.';
    } else if (selected.some(function (file) { return file.size > Number(files.dataset.maxSize); })) {
      message = 'A photo exceeds the per-image size limit. Choose a smaller image.';
    } else if (selected.reduce(function (total, file) { return total + file.size; }, 0) > Number(files.dataset.maxTotal)) {
      message = 'The selected photos exceed the total size limit.';
    }
    error.textContent = message;
    files.setCustomValidity(message);
    files.setAttribute('aria-invalid', message ? 'true' : 'false');
    return !message;
  }
  files.addEventListener('change', function () {
    releasePreviews();
    if (!validatePhotos()) { return; }
    Array.from(files.files).forEach(function (file, index) {
      var item = document.createElement('div');
      item.className = 'request-photo';
      var img = document.createElement('img');
      var url = URL.createObjectURL(file);
      urls.push(url);
      img.src = url;
      img.alt = 'Selected photo: ' + file.name;
      var name = document.createElement('span');
      name.textContent = file.name;
      name.title = file.name;
      var remove = document.createElement('button');
      remove.type = 'button';
      remove.className = 'link-button';
      remove.textContent = 'Remove';
      remove.setAttribute('aria-label', 'Remove ' + file.name);
      remove.addEventListener('click', function () {
        var transfer = new DataTransfer();
        Array.from(files.files).forEach(function (photo, photoIndex) {
          if (photoIndex !== index) { transfer.items.add(photo); }
        });
        files.files = transfer.files;
        files.dispatchEvent(new Event('change'));
      });
      item.append(img, name, remove);
      list.append(item);
    });
  });
  form.addEventListener('submit', function (event) {
    if (!validatePhotos()) { event.preventDefault(); files.reportValidity(); }
  });
  var summary = document.querySelector('[data-error-summary]');
  if (summary) { summary.focus(); }
  window.addEventListener('pagehide', releasePreviews);
  window.addEventListener('pageshow', function (event) {
    if (event.persisted) { files.dispatchEvent(new Event('change')); }
  });
}());
