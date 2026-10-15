/* Decorative interaction only. Account roles and login remain server-validated. */
(() => {
  const root = document.querySelector('[data-login-page]');
  if (!root) return;
  const explorer = root.querySelector('[data-login-explorer]');
  const copy = root.querySelector('#login-feature-copy');
  const descriptions = {
    report: 'A clear location and a photo help tell the story.',
    track: 'Follow your request from the first report to the final fix.',
    connect: 'Ask questions and share answers with your campus community.'
  };
  explorer.hidden = false;
  explorer.addEventListener('click', (event) => {
    const button = event.target.closest('[data-login-feature]');
    if (!button) return;
    explorer.querySelectorAll('button').forEach(item => item.setAttribute('aria-pressed', String(item === button)));
    copy.textContent = descriptions[button.dataset.loginFeature];
  });
  const scene = root.querySelector('.login-scene');
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const finePointer = matchMedia('(hover: hover) and (pointer: fine)');
  const resetScene = () => { scene.style.removeProperty('--scene-x'); scene.style.removeProperty('--scene-y'); };
  scene.addEventListener('pointermove', (event) => {
    if (motion.matches || !finePointer.matches) return;
    const rect = scene.getBoundingClientRect();
    scene.style.setProperty('--scene-x', `${(event.clientX - rect.left - rect.width / 2) / rect.width * 10}px`);
    scene.style.setProperty('--scene-y', `${(event.clientY - rect.top - rect.height / 2) / rect.height * 8}px`);
  });
  scene.addEventListener('pointerleave', resetScene);
  motion.addEventListener('change', resetScene);
  const password = root.querySelector('#password');
  const capsLock = root.querySelector('#login-caps-lock');
  const showCapsLock = event => { capsLock.hidden = !event.getModifierState('CapsLock'); };
  password.addEventListener('keydown', showCapsLock);
  password.addEventListener('keyup', showCapsLock);
  password.addEventListener('blur', () => { capsLock.hidden = true; });
  const form = root.querySelector('form');
  const submit = form.querySelector('[type=submit]');
  const label = submit.querySelector('[data-login-submit-label]');
  const status = form.querySelector('[data-login-submit-status]');
  form.addEventListener('submit', (event) => {
    if (submit.disabled) { event.preventDefault(); return; }
    submit.disabled = true;
    label.textContent = 'Signing in…';
    status.textContent = 'Signing in. Please wait.';
    form.setAttribute('aria-busy', 'true');
  });
  window.addEventListener('pageshow', () => {
    submit.disabled = false;
    label.textContent = 'Sign in';
    status.textContent = '';
    form.removeAttribute('aria-busy');
    password.type = 'password';
    const reveal = root.querySelector('[data-password-toggle]');
    reveal.textContent = 'Show';
    reveal.setAttribute('aria-pressed', 'false');
    capsLock.hidden = true;
  });
})();
