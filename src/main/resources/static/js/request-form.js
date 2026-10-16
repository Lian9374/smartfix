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
  var progress = document.querySelector('[data-request-progress]');
  var submit = form.querySelector('button[type=submit]');
  var submitLabel = form.querySelector('[data-request-submit-label]');
  var submitStatus = form.querySelector('[data-request-submit-status]');
  var initiallyDisabled = submit.disabled;
  function updateProgress() {
    if (!progress) { return; }
    var required = Array.from(form.querySelectorAll('[required]'));
    var ready = function (input) { return input.value.trim().length > 0 && input.validity.valid; };
    var completed = required.filter(ready).length;
    progress.querySelector('progress').max = required.length;
    progress.querySelector('progress').value = completed;
    progress.querySelector('[data-required-progress]').textContent = completed + ' / ' + required.length + ' required fields';
    [['problem', ['locationId', 'title', 'description'], 'Problem details'], ['priority', ['category', 'urgencyLevel'], 'Category and urgency'], ['photos', [], 'Photos (optional)']].forEach(function (section, index) {
      var done = section[0] === 'photos' ? files.files.length > 0 && files.validity.valid : section[1].every(function (id) { return ready(document.getElementById(id)); });
      var link = progress.querySelector('[data-progress-step="' + section[0] + '"]');
      link.dataset.complete = String(done);
      link.querySelector('span').textContent = done ? '✓' : String(index + 1);
      link.setAttribute('aria-label', section[2] + (done ? ': complete' : ''));
    });
  }
  if (progress) {
    progress.hidden = false;
    form.addEventListener('input', updateProgress);
    form.addEventListener('change', updateProgress);
    updateProgress();
  }
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
        files.dispatchEvent(new Event('change', { bubbles: true }));
      });
      item.append(img, name, remove);
      list.append(item);
    });
  });
  var dropzone = form.querySelector('[data-photo-dropzone]');
  if (dropzone) {
    dropzone.addEventListener('dragover', function (event) {
      if (!Array.from(event.dataTransfer.types).includes('Files')) { return; }
      event.preventDefault();
      event.dataTransfer.dropEffect = 'copy';
      dropzone.classList.add('is-drag-over');
    });
    dropzone.addEventListener('dragleave', function (event) {
      if (!dropzone.contains(event.relatedTarget)) { dropzone.classList.remove('is-drag-over'); }
    });
    dropzone.addEventListener('drop', function (event) {
      if (!event.dataTransfer.files.length) { return; }
      event.preventDefault();
      dropzone.classList.remove('is-drag-over');
      files.files = event.dataTransfer.files;
      files.dispatchEvent(new Event('change', { bubbles: true }));
    });
  }
  form.addEventListener('submit', function (event) {
    if (!validatePhotos()) { event.preventDefault(); files.reportValidity(); return; }
    if (form.dataset.pending === 'true') { event.preventDefault(); return; }
    form.dataset.pending = 'true';
    form.setAttribute('aria-busy', 'true');
    submit.disabled = true;
    if (submitLabel) { submitLabel.textContent = 'Submitting…'; }
    if (submitStatus) { submitStatus.textContent = 'Submitting your request. Please wait.'; }
  });
  var summary = document.querySelector('[data-error-summary]');
  if (summary) { summary.focus(); }
  window.addEventListener('pagehide', releasePreviews);
  window.addEventListener('pageshow', function (event) {
    delete form.dataset.pending;
    form.removeAttribute('aria-busy');
    submit.disabled = initiallyDisabled;
    if (submitLabel) { submitLabel.textContent = 'Submit request'; }
    if (submitStatus) { submitStatus.textContent = ''; }
    if (event.persisted) { files.dispatchEvent(new Event('change')); }
    updateProgress();
  });
}());
