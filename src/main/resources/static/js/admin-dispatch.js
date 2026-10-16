(() => {
  'use strict';
  const form = document.querySelector('[data-dispatch-form]');
  if (!form) return;
  const radios = [...form.querySelectorAll('input[name="technicianId"]')];
  const submit = form.querySelector('[data-dispatch-submit]');
  const status = form.querySelector('[data-dispatch-selection]');
  const conflict = submit.disabled;
  const update = () => {
    const selected = radios.find(radio => radio.checked && !radio.disabled);
    submit.disabled = conflict || !selected;
    status.textContent = selected
      ? `Selected: ${selected.closest('[data-engineer-name]').dataset.engineerName}`
      : 'Select an engineer above.';
  };
  form.addEventListener('change', update);
  window.addEventListener('pageshow', update);
  update();
  const search = document.querySelector('[data-engineer-search]');
  const result = document.querySelector('[data-engineer-search-result]');
  if (!search) return;
  const options = [...form.querySelectorAll('[data-engineer-search-text]')];
  search.addEventListener('input', () => {
    const term = search.value.trim().toLocaleLowerCase();
    let visible = 0;
    options.forEach(option => {
      option.hidden = !option.dataset.engineerSearchText.toLocaleLowerCase().includes(term);
      if (!option.hidden) visible++;
    });
    result.textContent = term ? `${visible} matching engineers` : '';
  });
})();
