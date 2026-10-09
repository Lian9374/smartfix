// A dependency-free Chrome DevTools Protocol driver.
//
// It exists so the browser checks in this directory drive a real Edge (real
// mouse events, real key events, real text insertion) instead of asserting on
// MockMvc output. Node's built-in fetch and WebSocket are the only transport.
//
// Nothing here knows anything about SmartFix: it is a page, a pointer and a
// keyboard, plus the measurements the checks need (layout overflow, response
// status, screenshots).

import { spawn } from 'node:child_process';
import { mkdirSync, writeFileSync, rmSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';

const EDGE = process.env.BROWSER_BINARY || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

async function waitForJson(url, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
      if (response.ok) {
        return await response.json();
      }
      lastError = new Error(`HTTP ${response.status} from ${url}`);
    } catch (error) {
      lastError = error;
    }
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  throw new Error(`timed out waiting for ${url}: ${lastError}`);
}

export async function launch({ port = 9333, width = 1440, height = 960 } = {}) {
  const profile = path.join(os.tmpdir(), `ui-check-profile-${Date.now()}`);
  mkdirSync(profile, { recursive: true });

  const child = spawn(
    EDGE,
    [
      '--headless=new',
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profile}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-gpu',
      '--disable-extensions',
      '--disable-background-networking',
      '--disable-component-update',
      `--window-size=${width},${height}`,
      'about:blank',
    ],
    { stdio: 'ignore', windowsHide: true },
  );

  await waitForJson(`http://127.0.0.1:${port}/json/version`, 30000);
  const targets = await waitForJson(`http://127.0.0.1:${port}/json/list`, 10000);
  const page = targets.find((target) => target.type === 'page');
  if (!page) {
    throw new Error('Edge started but exposed no page target');
  }

  const socket = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => {
    socket.addEventListener('open', resolve, { once: true });
    socket.addEventListener('error', reject, { once: true });
  });

  let nextId = 1;
  const pending = new Map();
  const listeners = [];
  const events = [];

  socket.addEventListener('message', (message) => {
    const frame = JSON.parse(message.data);
    if (frame.id) {
      const entry = pending.get(frame.id);
      pending.delete(frame.id);
      if (!entry) return;
      if (frame.error) entry.reject(new Error(`${frame.error.message} (${JSON.stringify(frame.error.data ?? '')})`));
      else entry.resolve(frame.result);
      return;
    }
    events.push(frame);
    for (const listener of listeners) listener(frame);
  });

  const send = (method, params = {}) =>
    new Promise((resolve, reject) => {
      const id = nextId++;
      pending.set(id, { resolve, reject });
      socket.send(JSON.stringify({ id, method, params }));
    });

  await send('Page.enable');
  await send('Runtime.enable');
  await send('Network.enable');

  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

  const evaluate = async (expression) => {
    const result = await send('Runtime.evaluate', {
      expression: `(() => { ${expression} })()`,
      returnByValue: true,
      awaitPromise: true,
    });
    if (result.exceptionDetails) {
      throw new Error(
        `page threw: ${result.exceptionDetails.exception?.description ?? result.exceptionDetails.text}`,
      );
    }
    return result.result.value;
  };

  /** Waits until the document has loaded and stopped moving. */
  const settle = async (timeoutMs = 15000) => {
    const deadline = Date.now() + timeoutMs;
    let stable = 0;
    let previous = null;
    while (Date.now() < deadline) {
      let state;
      try {
        state = await evaluate(
          'return { ready: document.readyState, url: location.href };',
        );
      } catch {
        await sleep(100);
        continue;
      }
      if (state.ready === 'complete' && state.url === previous) {
        stable += 1;
        if (stable >= 2) return state.url;
      } else {
        stable = 0;
      }
      previous = state.url;
      await sleep(100);
    }
    throw new Error('the page never settled');
  };

  const goto = async (url) => {
    await send('Page.navigate', { url });
    await settle();
    return url;
  };

  const rectOf = async (selector) =>
    evaluate(`
      const element = document.querySelector(${JSON.stringify(selector)});
      if (!element) return null;
      element.scrollIntoView({ block: 'center', inline: 'center' });
      const rect = element.getBoundingClientRect();
      return { x: rect.x, y: rect.y, width: rect.width, height: rect.height };
    `);

  const click = async (selector, { required = true } = {}) => {
    const rect = await rectOf(selector);
    if (!rect || rect.width === 0 || rect.height === 0) {
      if (required) throw new Error(`nothing clickable matched ${selector}`);
      return false;
    }
    const x = rect.x + rect.width / 2;
    const y = rect.y + rect.height / 2;
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x, y, button: 'none', clickCount: 0 });
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', x, y, button: 'left', clickCount: 1 });
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', x, y, button: 'left', clickCount: 1 });
    return true;
  };

  const clickAndSettle = async (selector, options) => {
    const clicked = await click(selector, options);
    if (clicked) await settle();
    return clicked;
  };

  /** Focuses a field with a real mouse click, then inserts text as a person's. */
  const typeInto = async (selector, text) => {
    await click(selector);
    if (text) await send('Input.insertText', { text });
  };

  const press = async (key) => {
    const codes = {
      Tab: { windowsVirtualKeyCode: 9, code: 'Tab', key: 'Tab' },
      Enter: { windowsVirtualKeyCode: 13, code: 'Enter', key: 'Enter' },
      ArrowDown: { windowsVirtualKeyCode: 40, code: 'ArrowDown', key: 'ArrowDown' },
      Space: { windowsVirtualKeyCode: 32, code: 'Space', key: ' ' },
    };
    const descriptor = codes[key];
    if (!descriptor) throw new Error(`unmapped key ${key}`);
    await send('Input.dispatchKeyEvent', { type: 'rawKeyDown', ...descriptor });
    if (key === 'Enter' || key === 'Space') {
      await send('Input.dispatchKeyEvent', { type: 'char', text: key === 'Enter' ? '\r' : ' ', ...descriptor });
    }
    await send('Input.dispatchKeyEvent', { type: 'keyUp', ...descriptor });
  };

  const setViewport = async (width, height = 900) => {
    await send('Emulation.setDeviceMetricsOverride', {
      width,
      height,
      deviceScaleFactor: 1,
      mobile: false,
    });
    await sleep(150);
  };

  const screenshot = async (file) => {
    const result = await send('Page.captureScreenshot', { format: 'png' });
    writeFileSync(file, Buffer.from(result.data, 'base64'));
  };

  const clearCookies = async () => {
    await send('Network.clearBrowserCookies');
  };

  /** The status of the most recent main-document response. */
  const lastDocumentStatus = () => {
    for (let index = events.length - 1; index >= 0; index -= 1) {
      const event = events[index];
      if (event.method === 'Network.responseReceived' && event.params.type === 'Document') {
        return event.params.response.status;
      }
    }
    return null;
  };

  const resetEvents = () => {
    events.length = 0;
  };

  const close = async () => {
    try {
      await send('Browser.close');
    } catch {
      /* the browser may already be gone */
    }
    try {
      socket.close();
    } catch {
      /* already closed */
    }
    child.kill();
    await sleep(500);
    for (let attempt = 0; attempt < 5; attempt += 1) {
      if (path.dirname(path.resolve(profile)) !== path.resolve(os.tmpdir())
          || !path.basename(profile).startsWith('ui-check-profile-')) {
        throw new Error('Refusing to remove a browser profile outside the temporary directory');
      }
      try {
        rmSync(profile, { recursive: true, force: true });
        break;
      } catch {
        await sleep(300);
      }
    }
  };

  return {
    send,
    evaluate,
    goto,
    settle,
    click,
    clickAndSettle,
    typeInto,
    press,
    rectOf,
    setViewport,
    screenshot,
    clearCookies,
    lastDocumentStatus,
    resetEvents,
    close,
    sleep,
  };
}
