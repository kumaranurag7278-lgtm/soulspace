/* SoulSpace front end: vanilla JS served by Spring Boot. */
(() => {
'use strict';

/* ================= helpers ================= */
const $ = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const rupee = n => '\u20B9' + n;
const uid = () => (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : 'c' + Math.random().toString(36).slice(2, 12) + Date.now().toString(36);
const fmtSlot = iso => new Date(iso).toLocaleString(undefined, { weekday: 'short', day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const HELPLINE_HTML = 'Tele-MANAS <a href="tel:14416">14416</a> \u00B7 KIRAN <a href="tel:18005990019">1800-599-0019</a> \u00B7 US <a href="tel:988">988</a>';

const state = {
  me: null, csrf: null, doctors: [], appts: [], posts: [],
  clientId: uid(), stomp: null, stompReady: false, afterLogin: null,
  room: null
};

function toast(msg, isErr) {
  const t = document.createElement('div');
  t.className = 'toast' + (isErr ? ' err' : '');
  t.textContent = msg;
  $('#toasts').appendChild(t);
  setTimeout(() => t.remove(), 4800);
}

function timeAgo(iso) {
  const s = Math.max(0, (Date.now() - new Date(iso).getTime()) / 1000);
  if (s < 60) return 'just now';
  if (s < 3600) return Math.floor(s / 60) + 'm ago';
  if (s < 86400) return Math.floor(s / 3600) + 'h ago';
  return Math.floor(s / 86400) + 'd ago';
}

function fmtCountdown(ms) {
  const t = Math.floor(ms / 1000);
  const d = Math.floor(t / 86400), h = Math.floor((t % 86400) / 3600), m = Math.floor((t % 3600) / 60), s = t % 60;
  if (d > 0) return d + 'd ' + h + 'h';
  return h + 'h ' + String(m).padStart(2, '0') + 'm ' + String(s).padStart(2, '0') + 's';
}

function openDlg(d) { if (!d.open) d.showModal(); }

/* ================= API with CSRF ================= */
async function csrf(force) {
  if (force || !state.csrf) {
    const r = await fetch('/api/csrf', { credentials: 'same-origin' });
    if (!r.ok) throw new Error('Could not start a secure session. Please refresh.');
    state.csrf = await r.json();
  }
  return state.csrf;
}

async function api(path, opts = {}) {
  const method = opts.method || 'GET';
  const attempt = async retry => {
    const headers = { Accept: 'application/json' };
    if (method !== 'GET') {
      const t = await csrf(retry);
      headers['Content-Type'] = 'application/json';
      headers[t.header] = t.token;
    }
    const res = await fetch(path, {
      method, headers, credentials: 'same-origin',
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined
    });
    let data = null;
    try { data = await res.json(); } catch (_) { /* empty body */ }
    const bareForbidden = res.status === 403 && (!data || !data.message || data.message === 'Forbidden');
    if (bareForbidden && method !== 'GET' && !retry) return attempt(true);
    if (!res.ok) {
      const e = new Error((data && data.message) || 'Something went wrong. Please try again.');
      e.status = res.status;
      throw e;
    }
    return data;
  };
  return attempt(false);
}

/* ================= Three.js cosmos ================= */
function initCosmos() {
  const canvas = $('#cosmos');
  if (!window.THREE) return;
  let renderer;
  try { renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true }); } catch (e) { return; }
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
  renderer.setClearColor(0x000000, 0);
  const scene = new THREE.Scene();
  scene.fog = new THREE.FogExp2(0x05060d, 0.0065);
  const camera = new THREE.PerspectiveCamera(60, 1, 0.1, 500);
  camera.position.set(0, 0, 40);

  // round star sprite
  const sc = document.createElement('canvas'); sc.width = sc.height = 64;
  const g = sc.getContext('2d');
  const grad = g.createRadialGradient(32, 32, 0, 32, 32, 32);
  grad.addColorStop(0, 'rgba(255,255,255,1)'); grad.addColorStop(0.25, 'rgba(255,255,255,.8)'); grad.addColorStop(1, 'rgba(255,255,255,0)');
  g.fillStyle = grad; g.fillRect(0, 0, 64, 64);
  const sprite = new THREE.CanvasTexture(sc);

  const N = 1500;
  const pos = new Float32Array(N * 3), col = new Float32Array(N * 3);
  const palette = [0xffffff, 0xffffff, 0xffffff, 0xffd28a, 0x9fb4ff, 0x6ee7d8, 0xf4a6c8].map(c => new THREE.Color(c));
  for (let i = 0; i < N; i++) {
    const r = 45 + Math.random() * 170, th = Math.random() * Math.PI * 2, ph = Math.acos(2 * Math.random() - 1);
    pos[i * 3] = r * Math.sin(ph) * Math.cos(th);
    pos[i * 3 + 1] = r * Math.sin(ph) * Math.sin(th);
    pos[i * 3 + 2] = r * Math.cos(ph);
    const c = palette[Math.floor(Math.random() * palette.length)];
    col[i * 3] = c.r; col[i * 3 + 1] = c.g; col[i * 3 + 2] = c.b;
  }
  const geo = new THREE.BufferGeometry();
  geo.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  geo.setAttribute('color', new THREE.BufferAttribute(col, 3));
  const stars = new THREE.Points(geo, new THREE.PointsMaterial({
    size: 2.4, map: sprite, vertexColors: true, transparent: true, opacity: 0.95,
    depthWrite: false, blending: THREE.AdditiveBlending, sizeAttenuation: true
  }));
  scene.add(stars);

  const planets = [];
  function planet(radius, color, x, y, z, ringIn, ringOut, spin) {
    const grp = new THREE.Group();
    grp.position.set(x, y, z);
    grp.add(new THREE.Mesh(new THREE.SphereGeometry(radius, 26, 18),
      new THREE.MeshBasicMaterial({ color, wireframe: true, transparent: true, opacity: 0.32 })));
    if (ringIn) {
      const ring = new THREE.Mesh(new THREE.RingGeometry(ringIn, ringOut, 72),
        new THREE.MeshBasicMaterial({ color, wireframe: true, transparent: true, opacity: 0.22, side: THREE.DoubleSide }));
      ring.rotation.x = Math.PI / 2.35;
      grp.add(ring);
    }
    grp.userData.spin = spin;
    scene.add(grp); planets.push(grp);
  }
  planet(6, 0xffd28a, -26, 11, -22, 9.5, 14, 0.12);
  planet(3.6, 0x6ee7d8, 30, -13, -34, 0, 0, -0.18);
  planet(9, 0xb7a6ff, 14, 24, -95, 13, 19, 0.07);

  let mx = 0, my = 0, scrollP = 0;
  window.addEventListener('pointermove', e => {
    mx = (e.clientX / window.innerWidth) * 2 - 1;
    my = (e.clientY / window.innerHeight) * 2 - 1;
  }, { passive: true });
  const onScroll = () => {
    const max = Math.max(1, document.documentElement.scrollHeight - window.innerHeight);
    scrollP = Math.min(1, window.scrollY / max);
  };
  window.addEventListener('scroll', onScroll, { passive: true }); onScroll();
  const resize = () => {
    const w = window.innerWidth, h = window.innerHeight;
    renderer.setSize(w, h, false);
    camera.aspect = w / h; camera.updateProjectionMatrix();
  };
  window.addEventListener('resize', () => { resize(); if (reduceMotion) renderer.render(scene, camera); });
  resize();

  function frame(ts) {
    requestAnimationFrame(frame);
    if (document.hidden) return;
    const t = ts / 1000;
    camera.position.x += (mx * 5 - camera.position.x) * 0.04;
    camera.position.y += (-my * 3 - camera.position.y) * 0.04;
    camera.position.z += ((40 - scrollP * 34) - camera.position.z) * 0.06;
    camera.lookAt(0, 0, -10);
    stars.rotation.y = t * 0.008 + scrollP * 1.1;
    stars.rotation.x = Math.sin(t * 0.05) * 0.04;
    planets.forEach(p => { p.rotation.y = t * p.userData.spin; p.rotation.z = Math.sin(t * 0.1) * 0.05; });
    renderer.render(scene, camera);
  }
  if (reduceMotion) renderer.render(scene, camera); else requestAnimationFrame(frame);
}

/* ================= pointer effects: tilt + button sheen ================= */
function initPointerFx() {
  const canTilt = !reduceMotion && !window.matchMedia('(hover: none)').matches;
  let cur = null;
  const reset = el => { if (el) { el.style.transform = ''; el.classList.remove('is-tilting'); } };
  document.addEventListener('pointermove', e => {
    if (e.pointerType === 'touch') return;
    const target = e.target instanceof Element ? e.target : null;
    const btn = target && target.closest('.btn');
    if (btn) {
      const r = btn.getBoundingClientRect();
      btn.style.setProperty('--mx', (e.clientX - r.left) + 'px');
      btn.style.setProperty('--my', (e.clientY - r.top) + 'px');
    }
    if (!canTilt) return;
    const el = target ? target.closest('.tilt') : null;
    if (el !== cur) { reset(cur); cur = el; }
    if (!el) return;
    const r = el.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width, py = (e.clientY - r.top) / r.height;
    const max = Math.min(8, 1800 / r.width);
    el.style.transform = 'perspective(1000px) rotateX(' + ((0.5 - py) * max * 2).toFixed(2) + 'deg) rotateY(' + ((px - 0.5) * max * 2).toFixed(2) + 'deg) translateZ(0)';
    el.style.setProperty('--gx', (px * 100).toFixed(1) + '%');
    el.style.setProperty('--gy', (py * 100).toFixed(1) + '%');
    el.classList.add('is-tilting');
  }, { passive: true });
  document.documentElement.addEventListener('mouseleave', () => { reset(cur); cur = null; });
}

/* ================= ambient audio synth ================= */
const audio = { ctx: null, master: null, on: false };

function buildAudio() {
  const ctx = audio.ctx;
  const master = ctx.createGain();
  master.gain.value = 0; master.connect(ctx.destination); audio.master = master;

  const tone = (freq, pan, gain) => {
    const o = ctx.createOscillator(); o.type = 'sine'; o.frequency.value = freq;
    const g = ctx.createGain(); g.gain.value = gain;
    o.connect(g);
    if (ctx.createStereoPanner) {
      const p = ctx.createStereoPanner(); p.pan.value = pan; g.connect(p); p.connect(master);
    } else { g.connect(master); }
    o.start();
  };
  // 55 Hz binaural drone: left ear 55 Hz, right ear 59 Hz (4 Hz theta beat), plus a soft octave
  tone(55, -1, 0.17); tone(59, 1, 0.17); tone(110, -0.4, 0.035); tone(111.5, 0.4, 0.035);

  // pink-noise rain (Paul Kellet filter)
  const len = ctx.sampleRate * 4;
  const buf = ctx.createBuffer(1, len, ctx.sampleRate);
  const d = buf.getChannelData(0);
  let b0 = 0, b1 = 0, b2 = 0, b3 = 0, b4 = 0, b5 = 0, b6 = 0;
  for (let i = 0; i < len; i++) {
    const w = Math.random() * 2 - 1;
    b0 = 0.99886 * b0 + w * 0.0555179; b1 = 0.99332 * b1 + w * 0.0750759; b2 = 0.969 * b2 + w * 0.153852;
    b3 = 0.8665 * b3 + w * 0.3104856; b4 = 0.55 * b4 + w * 0.5329522; b5 = -0.7616 * b5 - w * 0.016898;
    d[i] = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362) * 0.11;
    b6 = w * 0.115926;
  }
  const src = ctx.createBufferSource(); src.buffer = buf; src.loop = true;
  const hp = ctx.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 400;
  const lp = ctx.createBiquadFilter(); lp.type = 'lowpass'; lp.frequency.value = 4200;
  const rg = ctx.createGain(); rg.gain.value = 0.22;
  src.connect(hp); hp.connect(lp); lp.connect(rg); rg.connect(master); src.start();
  const lfo = ctx.createOscillator(); lfo.frequency.value = 0.07;
  const lg = ctx.createGain(); lg.gain.value = 0.08;
  lfo.connect(lg); lg.connect(rg.gain); lfo.start();
}

function toggleSound() {
  const btn = $('#soundBtn');
  if (!audio.ctx) {
    const C = window.AudioContext || window.webkitAudioContext;
    if (!C) { toast('Your browser does not support Web Audio.', true); return; }
    audio.ctx = new C(); buildAudio();
  }
  const now = audio.ctx.currentTime;
  if (!audio.on) {
    audio.ctx.resume();
    audio.master.gain.cancelScheduledValues(now);
    audio.master.gain.setTargetAtTime(0.45, now, 1.2);
    audio.on = true;
  } else {
    audio.master.gain.cancelScheduledValues(now);
    audio.master.gain.setTargetAtTime(0, now, 0.6);
    audio.on = false;
    setTimeout(() => { if (!audio.on) audio.ctx.suspend(); }, 3000);
  }
  btn.setAttribute('aria-pressed', String(audio.on));
  btn.textContent = audio.on ? 'Sound: on' : 'Ambient sound';
}

/* ================= 4-7-8 breathing ================= */
const PHASES = [
  { name: 'Breathe in', sec: 4, scale: 1.28 },
  { name: 'Hold', sec: 7, scale: 1.28 },
  { name: 'Breathe out', sec: 8, scale: 0.78 }
];
const breath = { running: false, i: 0, left: 0, timer: null, cycles: 0 };

function setBreathPhase() {
  const p = PHASES[breath.i];
  $('#orbPhase').textContent = p.name;
  const orb = $('#orb');
  orb.style.transitionDuration = p.sec + 's';
  orb.style.transform = 'scale(' + p.scale + ')';
  breath.left = p.sec;
  $('#orbCount').textContent = String(breath.left);
}

function toggleBreath() {
  const btn = $('#orbBtn');
  if (breath.running) {
    clearInterval(breath.timer); breath.running = false;
    const orb = $('#orb'); orb.style.transitionDuration = '1.5s'; orb.style.transform = 'scale(0.78)';
    $('#orbPhase').textContent = 'Ready'; $('#orbCount').textContent = '\u2013';
    $('#orbCycles').textContent = breath.cycles > 0
      ? 'You completed ' + breath.cycles + (breath.cycles === 1 ? ' full cycle.' : ' full cycles.') + ' Well done.'
      : 'Breathe in for 4, hold for 7, breathe out for 8.';
    btn.textContent = 'Begin';
    return;
  }
  breath.running = true; breath.i = 0; breath.cycles = 0;
  btn.textContent = 'Stop';
  $('#orbCycles').textContent = 'Breathe with the light. Let the exhale be the longest part.';
  setBreathPhase();
  breath.timer = setInterval(() => {
    breath.left--;
    if (breath.left <= 0) {
      breath.i = (breath.i + 1) % PHASES.length;
      if (breath.i === 0) breath.cycles++;
      setBreathPhase();
    } else { $('#orbCount').textContent = String(breath.left); }
  }, 1000);
}

/* ================= STOMP ================= */
const waiters = [];

function createStomp() {
  if (!window.StompJs) return;
  const client = new StompJs.Client({
    brokerURL: (location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws-soulspace',
    reconnectDelay: 4000, heartbeatIncoming: 10000, heartbeatOutgoing: 10000
  });
  client.onConnect = () => {
    state.stompReady = true;
    client.subscribe('/topic/seeker/' + state.clientId, m => onSeeker(JSON.parse(m.body)));
    client.subscribe('/topic/board', m => onBoardLive(JSON.parse(m.body)));
    client.publish({ destination: '/app/hello', body: JSON.stringify({ clientId: state.clientId }) });
    if (state.room) subscribeRoom();
    waiters.splice(0).forEach(w => { clearTimeout(w.t); w.resolve(); });
  };
  client.onWebSocketClose = () => {
    if (state.stompReady) { state.stompReady = false; onStompLost(); }
  };
  client.activate();
  state.stomp = client;
}

function ensureStomp() {
  if (state.stompReady) return Promise.resolve();
  return new Promise((resolve, reject) => {
    const w = { resolve, reject };
    w.t = setTimeout(() => {
      const i = waiters.indexOf(w);
      if (i >= 0) { waiters.splice(i, 1); reject(new Error('Could not reach live chat. Please try again.')); }
    }, 9000);
    waiters.push(w);
    if (!state.stomp) {
      if (!window.StompJs) { clearTimeout(w.t); waiters.pop(); reject(new Error('The live chat library failed to load.')); return; }
      createStomp();
    }
  });
}

function onStompLost() {
  if (chat.phase !== 'idle') {
    addMsg($('#chatLog'), 'sys', 'Connection lost. This chat has ended and nothing was saved.');
    endChatLocal();
  }
  if (state.room) $('#roomStatus').textContent = 'Reconnecting to the room\u2026';
}

/* ================= venting lounge ================= */
const chat = { phase: 'idle', roomId: null, you: null, sub: null, hint: null, ended: false };

function showLounge(which) {
  ['Idle', 'Searching', 'Chat'].forEach(n => { $('#lounge' + n).hidden = (n !== which); });
}

function addMsg(log, cls, text) {
  const d = document.createElement('div');
  d.className = 'msg ' + cls;
  d.textContent = text;
  log.appendChild(d);
  while (log.children.length > 250) log.removeChild(log.firstChild);
  log.scrollTop = log.scrollHeight;
  return d;
}

function addCare(log) {
  const d = document.createElement('div');
  d.className = 'msg care';
  d.innerHTML = 'It sounds like you are carrying something heavy. You deserve support from a person right now: ' + HELPLINE_HTML + '. If you might act on these thoughts, please call your local emergency number.';
  log.appendChild(d);
  log.scrollTop = log.scrollHeight;
}

function endChatLocal() {
  if (chat.sub) { try { chat.sub.unsubscribe(); } catch (_) { /* ignore */ } chat.sub = null; }
  clearTimeout(chat.hint);
  chat.phase = 'idle'; chat.roomId = null; chat.ended = false;
  showLounge('Idle');
}

async function leaveChat() {
  const was = chat.phase;
  if (chat.sub) { try { chat.sub.unsubscribe(); } catch (_) { /* ignore */ } chat.sub = null; }
  clearTimeout(chat.hint);
  chat.phase = 'idle'; chat.roomId = null; chat.ended = false;
  $('#chatLog').textContent = '';
  if (was !== 'idle') {
    try { await api('/api/chat/leave', { method: 'POST', body: { clientId: state.clientId } }); } catch (_) { /* ignore */ }
  }
}

async function startMatch() {
  try { await ensureStomp(); } catch (e) { toast(e.message, true); return; }
  $('#chatLog').textContent = '';
  chat.phase = 'searching'; chat.ended = false;
  showLounge('Searching');
  $('#searchText').textContent = 'Looking for someone who is awake too\u2026';
  clearTimeout(chat.hint);
  chat.hint = setTimeout(() => {
    if (chat.phase === 'searching') $('#searchText').textContent = 'Still looking. It can be quiet at this hour. Aria is awake if you would like company meanwhile.';
  }, 20000);
  try {
    const r = await api('/api/chat/match', { method: 'POST', body: { clientId: state.clientId } });
    if (r.status === 'MATCHED' && chat.phase === 'searching') enterChat(r);
  } catch (e) {
    toast(e.message, true);
    await leaveChat(); showLounge('Idle');
  }
}

function enterChat(m) {
  clearTimeout(chat.hint);
  chat.phase = 'chat'; chat.roomId = m.roomId; chat.you = m.you; chat.ended = false;
  if (chat.sub) { try { chat.sub.unsubscribe(); } catch (_) { /* ignore */ } }
  chat.sub = state.stomp.subscribe('/topic/room/' + m.roomId, f => onRoomMsg(JSON.parse(f.body)));
  $('#chatPeer').textContent = m.peer;
  $('#chatYou').textContent = 'You are ' + m.you;
  $('#chatLog').textContent = '';
  addMsg($('#chatLog'), 'sys', 'You are connected to ' + m.peer + '. Be kind. Nothing here is saved, and it all disappears when either of you leaves.');
  $('#chatInput').disabled = false;
  showLounge('Chat');
  $('#chatInput').focus();
}

function onSeeker(msg) {
  if (msg.type === 'MATCHED' && chat.phase === 'searching') enterChat(msg);
  else if (msg.type === 'CRISIS_SUPPORT' && chat.phase === 'chat') addCare($('#chatLog'));
}

function onRoomMsg(msg) {
  const log = $('#chatLog');
  if (msg.type === 'MSG') addMsg(log, msg.from === chat.you ? 'me' : 'them', msg.text);
  else if (msg.type === 'PEER_LEFT' && chat.phase === 'chat' && !chat.ended) {
    chat.ended = true;
    addMsg(log, 'sys', 'Your companion has left. This conversation is gone and nothing was saved. You can find another soul whenever you like.');
    $('#chatInput').disabled = true;
    if (chat.sub) { try { chat.sub.unsubscribe(); } catch (_) { /* ignore */ } chat.sub = null; }
  }
}

async function refreshStats() {
  try {
    const s = await api('/api/chat/stats');
    const n = s.waiting + s.activeChats * 2;
    $('#liveCount').textContent = n > 0 ? n + (n === 1 ? ' soul is' : ' souls are') + ' in the lounge right now.' : 'The lounge is quiet right now. Be the first light.';
  } catch (_) { /* ignore */ }
}

/* ================= auth ================= */
let authMode = 'login';

function setAuthMode(mode) {
  authMode = mode;
  $$('.tab').forEach(t => { const on = t.dataset.tab === mode; t.classList.toggle('on', on); t.setAttribute('aria-selected', String(on)); });
  $('.only-register').hidden = mode !== 'register';
  $('#authSubmit').textContent = mode === 'login' ? 'Sign in' : 'Create account';
  $('#aPass').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
  $('#authError').textContent = '';
}

function openAuth(mode, msg) {
  setAuthMode(mode || 'login');
  if (msg) toast(msg);
  openDlg($('#authDlg'));
  $('#aEmail').focus();
}

function requireAuth(cb, msg) {
  state.afterLogin = cb;
  openAuth('login', msg);
}

function setMe(me) {
  state.me = me && me.authenticated ? me : null;
  const on = !!state.me;
  $('#authBtn').textContent = on ? state.me.alias + ' \u00B7 Sign out' : 'Sign in';
  $('#sanctuary').hidden = !on;
  $('#navSanctuary').hidden = !on;
  renderPassBanner();
  renderSanctuary();
}

async function afterAuth() {
  await Promise.allSettled([loadDoctors(), loadAppointments(), loadMood()]);
  const cb = state.afterLogin; state.afterLogin = null;
  if (cb) cb();
}

async function onAuthSubmit(e) {
  e.preventDefault();
  const err = $('#authError'); err.textContent = '';
  const email = $('#aEmail').value.trim(), password = $('#aPass').value;
  if (!email || !password) { err.textContent = 'Enter your email and password.'; return; }
  const btn = $('#authSubmit'); btn.disabled = true;
  try {
    let me;
    if (authMode === 'login') {
      me = await api('/api/auth/login', { method: 'POST', body: { email, password } });
    } else {
      const age = parseInt($('#aAge').value, 10);
      me = await api('/api/auth/register', { method: 'POST', body: { email, password, alias: $('#aAlias').value.trim(), age: Number.isFinite(age) ? age : null, role: $('#aRole').value } });
    }
    setMe(me);
    $('#authDlg').close();
    $('#authForm').reset();
    toast('Welcome, ' + me.alias + '.');
    afterAuth();
  } catch (ex) {
    err.textContent = ex.message;
  } finally { btn.disabled = false; }
}

async function signOut() {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch (_) { /* ignore */ }
  if (state.room) closeRoom(false);
  state.appts = [];
  setMe(null);
  loadDoctors(); loadMood();
  toast('You are signed out.');
}

/* ================= clinicians ================= */
async function loadDoctors() {
  try {
    state.doctors = await api('/api/doctors');
    renderDoctors();
  } catch (e) {
    $('#doctorGrid').innerHTML = '<p class="muted">Clinicians could not be loaded. Please refresh.</p>';
  }
}

function renderPassBanner() {
  const buy = $('#passBtn'), price = $('.pass-price');
  if (state.me && state.me.passActive) {
    price.innerHTML = '<span class="pass-active">Active</span> \u00B7 ' + esc(state.me.passDaysLeft) + ' days left';
    buy.hidden = true;
  } else {
    price.innerHTML = '<span>\u20B9899</span> / month';
    buy.hidden = false;
  }
}

function renderDoctors() {
  const grid = $('#doctorGrid');
  grid.innerHTML = state.doctors.map(d => {
    const free = d.effectiveFee === 0;
    const rv = d.latestReview;
    return '<article class="panel tilt doctor" data-code="' + esc(d.code) + '">' +
      '<div class="doc-top"><div class="avatar" aria-hidden="true">' + esc(d.avatar) + '</div>' +
      '<div><div class="doc-name">' + esc(d.name) + '</div><div class="doc-title">' + esc(d.title) + '</div></div></div>' +
      '<div class="doc-meta"><span class="chip">' + esc(d.experience) + ' yrs experience</span><span class="chip">' + esc(d.badge) + '</span></div>' +
      '<div class="rating">\u2605 ' + d.ratingAverage.toFixed(1) + ' <small>(' + esc(d.totalReviews) + ' reviews)</small></div>' +
      '<p class="doc-title">' + esc(d.specialty) + '</p>' +
      (rv ? '<blockquote class="quote">\u201C' + esc(rv.comment) + '\u201D<cite>' + esc(rv.author) + ' \u00B7 ' + '\u2605'.repeat(rv.rating) + '</cite></blockquote>' : '') +
      '<div class="price-row"><div class="price' + (free ? ' free' : '') + '">' + (free ? '<s>' + rupee(d.fee) + '</s>\u20B90' : rupee(d.fee)) + '</div></div>' +
      '<div class="next-slot">' + (d.nextSlot ? 'Next slot: ' + esc(fmtSlot(d.nextSlot)) : 'No slots available right now') + '</div>' +
      '<div class="doc-actions">' +
      '<button class="btn btn-primary btn-shine btn-sm" type="button" data-act="book">' + (free ? 'Book Free (SoulPass) \u2197' : 'Book ' + rupee(d.fee) + ' \u2197') + '</button>' +
      '<button class="btn btn-ghost btn-shine btn-sm" type="button" data-act="rate">\u2605 Rate &amp; Review</button>' +
      '</div></article>';
  }).join('') || '<p class="muted">No clinicians are available yet.</p>';
}

function payWidgetHtml() {
  return '<div class="pay-switch" role="group" aria-label="Payment method">' +
    '<button type="button" class="btn btn-ghost btn-sm" data-pay="UPI" aria-pressed="true">UPI</button>' +
    '<button type="button" class="btn btn-ghost btn-sm" data-pay="CARD" aria-pressed="false">Card</button></div>' +
    '<label class="pay-upi">UPI ID<input data-upi placeholder="name@upi" autocomplete="off" inputmode="email"></label>' +
    '<p class="fine pay-upi-note">Simulated payment: no money moves.</p>' +
    '<p class="fine pay-card" hidden>Simulated payment: no card details are collected and nothing is charged.</p>';
}

function bindPay(root) {
  const st = { method: 'UPI' };
  const upi = $('[data-upi]', root);
  $$('[data-pay]', root).forEach(b => b.addEventListener('click', () => {
    st.method = b.dataset.pay;
    $$('[data-pay]', root).forEach(x => x.setAttribute('aria-pressed', String(x === b)));
    $('.pay-upi', root).hidden = st.method !== 'UPI';
    $('.pay-upi-note', root).hidden = st.method !== 'UPI';
    $('.pay-card', root).hidden = st.method !== 'CARD';
  }));
  return { get method() { return st.method; }, get upi() { return upi.value.trim(); } };
}

async function openBooking(code) {
  const d = state.doctors.find(x => x.code === code);
  if (!d) return;
  if (!state.me) { requireAuth(() => openBooking(code), 'Sign in to book a session.'); return; }
  let slots;
  try { slots = await api('/api/doctors/' + encodeURIComponent(code) + '/slots'); } catch (e) { toast(e.message, true); return; }
  const pass = !!state.me.passActive;
  const body = $('#bookBody');
  let chosen = (slots.find(s => !s.taken) || {}).slot || null;
  body.innerHTML = '<h2 id="bookTitle">Book ' + esc(d.name) + '</h2>' +
    '<p class="muted">' + esc(d.title) + ' \u00B7 ' + esc(d.experience) + ' yrs</p>' +
    '<div class="slot-grid" role="radiogroup" aria-label="Appointment time">' +
    slots.map(s => '<button type="button" class="slot" role="radio" data-slot="' + esc(s.slot) + '" aria-checked="' + (s.slot === chosen) + '"' + (s.taken ? ' disabled' : '') + '>' + esc(fmtSlot(s.slot)) + '</button>').join('') +
    '</div>' +
    (pass ? '<p class="pass-active">Covered by your SoulPass. Nothing to pay.</p>' : payWidgetHtml()) +
    '<div class="total-row"><span>Total</span><strong>' + (pass ? '\u20B90' : rupee(d.fee)) + '</strong></div>' +
    '<p class="form-error" id="bookError" role="alert"></p>' +
    '<button class="btn btn-primary btn-shine" type="button" id="bookConfirm">' + (pass ? 'Confirm free booking' : 'Pay ' + rupee(d.fee) + ' and confirm') + '</button>';
  $$('.slot', body).forEach(b => b.addEventListener('click', () => {
    chosen = b.dataset.slot;
    $$('.slot', body).forEach(x => x.setAttribute('aria-checked', String(x === b)));
  }));
  const pay = pass ? null : bindPay(body);
  $('#bookConfirm').addEventListener('click', async ev => {
    const err = $('#bookError'); err.textContent = '';
    if (!chosen) { err.textContent = 'Choose a time slot.'; return; }
    ev.currentTarget.disabled = true;
    try {
      const b = await api('/api/bookings', { method: 'POST', body: { doctorCode: code, slot: chosen, paymentMethod: pay ? pay.method : undefined, upiId: pay ? pay.upi : undefined } });
      body.innerHTML = '<div class="success"><h2 id="bookTitle">You are booked.</h2>' +
        '<p class="muted">' + esc(b.doctorName) + ' \u00B7 ' + esc(fmtSlot(b.slot)) + '</p>' +
        '<div class="big-code">' + esc(b.appointmentCode) + '</div>' +
        '<p class="fine">' + (b.coveredByPremium ? 'Covered by SoulPass (\u20B90).' : 'Paid ' + rupee(b.feePaid) + ' (simulated).') + ' Keep this code. You can open your private room from My Soul Sanctuary.</p>' +
        '<button class="btn btn-primary btn-shine" type="button" id="goSanctuary">Open my sanctuary</button></div>';
      $('#goSanctuary').addEventListener('click', () => { $('#bookDlg').close(); $('#sanctuary').scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth' }); });
      loadAppointments(); loadDoctors();
    } catch (ex) {
      err.textContent = ex.message;
      ev.currentTarget.disabled = false;
    }
  });
  openDlg($('#bookDlg'));
}

let reviewCtx = { code: null, rating: 0 };

function openReview(code) {
  const d = state.doctors.find(x => x.code === code);
  if (!d) return;
  if (!state.me) { requireAuth(() => openReview(code), 'Sign in to leave a review.'); return; }
  reviewCtx = { code, rating: 0 };
  $('#reviewFor').textContent = d.name + ' \u00B7 ' + d.title;
  $('#reviewError').textContent = '';
  $('#reviewText').value = '';
  const pick = $('#starPick');
  pick.innerHTML = [1, 2, 3, 4, 5].map(v => '<button type="button" class="star" role="radio" aria-checked="false" aria-label="' + v + ' star' + (v > 1 ? 's' : '') + '" data-v="' + v + '">\u2605</button>').join('');
  $$('.star', pick).forEach(b => b.addEventListener('click', () => {
    reviewCtx.rating = Number(b.dataset.v);
    $$('.star', pick).forEach(x => { const on = Number(x.dataset.v) <= reviewCtx.rating; x.classList.toggle('on', on); x.setAttribute('aria-checked', String(Number(x.dataset.v) === reviewCtx.rating)); });
  }));
  openDlg($('#reviewDlg'));
}

async function onReviewSubmit(e) {
  e.preventDefault();
  const err = $('#reviewError'); err.textContent = '';
  if (!reviewCtx.rating) { err.textContent = 'Pick 1 to 5 stars.'; return; }
  const comment = $('#reviewText').value.trim();
  if (!comment) { err.textContent = 'Please write a few words.'; return; }
  try {
    await api('/api/doctors/' + encodeURIComponent(reviewCtx.code) + '/reviews', { method: 'POST', body: { rating: reviewCtx.rating, comment } });
    $('#reviewDlg').close();
    toast('Thank you. Your review has been added.');
    loadDoctors();
  } catch (ex) { err.textContent = ex.message; }
}

function openPass() {
  if (!state.me) { requireAuth(openPass, 'Sign in to get the SoulPass.'); return; }
  if (state.me.passActive) { toast('Your SoulPass is already active.'); return; }
  const body = $('#passBody');
  body.innerHTML = '<h2 id="passTitle">SoulSpace Unlimited Pass</h2>' +
    '<p class="muted">Unlimited 24/7 consultations with any clinician (\u20B9100, \u20B9300, \u20B9500 or \u20B9800) at a flat \u20B90 for 30 days.</p>' +
    payWidgetHtml() +
    '<div class="total-row"><span>Today</span><strong>\u20B9899</strong></div>' +
    '<p class="form-error" id="passError" role="alert"></p>' +
    '<button class="btn btn-primary btn-shine" type="button" id="passConfirm">Pay \u20B9899 and activate</button>';
  const pay = bindPay(body);
  $('#passConfirm').addEventListener('click', async ev => {
    const err = $('#passError'); err.textContent = '';
    ev.currentTarget.disabled = true;
    try {
      const r = await api('/api/pass/purchase', { method: 'POST', body: { paymentMethod: pay.method, upiId: pay.upi } });
      body.innerHTML = '<div class="success"><h2 id="passTitle">Your Pass is active.</h2>' +
        '<div class="big-code">' + esc(r.subscriptionCode) + '</div>' +
        '<p class="fine">Valid for 30 days. Every consultation is now \u20B90.</p>' +
        '<button class="btn btn-primary btn-shine" type="button" data-close>Start booking</button></div>';
      $('[data-close]', body).addEventListener('click', () => { $('#passDlg').close(); $('#clinicians').scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth' }); });
      try { setMe(await api('/api/me')); } catch (_) { /* ignore */ }
      loadDoctors();
    } catch (ex) { err.textContent = ex.message; ev.currentTarget.disabled = false; }
  });
  openDlg($('#passDlg'));
}

/* ================= sanctuary ================= */
async function loadAppointments() {
  if (!state.me) { state.appts = []; renderSanctuary(); return; }
  try { state.appts = await api('/api/bookings/mine'); } catch (_) { state.appts = []; }
  renderSanctuary();
}

function renderSanctuary() {
  if (!state.me) return;
  const m = state.me;
  $('#sancPass').innerHTML = '<h3>' + esc(m.alias) + '</h3>' +
    (m.passActive
      ? '<p class="pass-active">SoulPass active \u00B7 ' + esc(m.passDaysLeft) + ' days left</p>' + (m.passCode ? '<p class="code">' + esc(m.passCode) + '</p>' : '')
      : '<p class="muted">No active SoulPass.</p><button class="btn btn-primary btn-shine btn-sm" type="button" data-act="pass">Get the Pass \u20B9899</button>') +
    (m.role === 'PROFESSIONAL' ? '<p class="fine">Professional account: verification ' + esc(String(m.verificationStatus || 'PENDING').toLowerCase()) + '.</p>' : '');
  const now = Date.now();
  const rows = state.appts.map(a => {
    const t = new Date(a.slot).getTime();
    const over = now > t + 60 * 60 * 1000;
    return '<div class="appt"><div><strong>' + esc(a.doctorName) + '</strong><div class="muted">' + esc(fmtSlot(a.slot)) + ' \u00B7 ' + (a.coveredByPremium ? 'SoulPass' : rupee(a.feePaid)) + '</div><div class="code">' + esc(a.appointmentCode) + '</div></div>' +
      (over ? '<span class="muted">Completed</span>'
        : '<div style="display:flex;align-items:center;gap:6px;"><div class="countdown" data-slot="' + esc(a.slot) + '"></div><button class="btn btn-primary btn-shine btn-sm" type="button" data-act="room" data-code="' + esc(a.appointmentCode) + '">Enter room</button><button class="btn btn-ghost btn-shine btn-sm" type="button" data-act="cancel" data-code="' + esc(a.appointmentCode) + '" title="Cancel appointment">Cancel</button></div>') +
      '</div>';
  }).join('');
  $('#sancAppts').innerHTML = '<h3>Upcoming appointments</h3>' + (rows || '<p class="muted">No appointments yet. Book a clinician above and it will appear here.</p>');
  tickCountdowns();
}

function tickCountdowns() {
  const now = Date.now();
  $$('.countdown[data-slot]').forEach(el => {
    const diff = new Date(el.dataset.slot).getTime() - now;
    el.textContent = diff > 0 ? 'Starts in ' + fmtCountdown(diff) : 'In session window';
  });
}

/* ================= mood ================= */
const MOODS = {
  joyful: { emoji: '\uD83D\uDE0A', score: 5, label: 'Joyful' },
  calm: { emoji: '\uD83D\uDE0C', score: 4, label: 'Calm' },
  neutral: { emoji: '\uD83D\uDE10', score: 3, label: 'Neutral' },
  anxious: { emoji: '\uD83D\uDE30', score: 2, label: 'Anxious' },
  overwhelmed: { emoji: '\uD83D\uDE14', score: 1, label: 'Overwhelmed' }
};
const todayKey = () => new Date().toLocaleDateString('en-CA');
const readLocalMood = () => { try { return JSON.parse(localStorage.getItem('ss_mood') || '[]'); } catch (_) { return []; } };
const writeLocalMood = a => { try { localStorage.setItem('ss_mood', JSON.stringify(a.slice(-60))); } catch (_) { /* ignore */ } };

async function loadMood() {
  let entries;
  if (state.me) {
    try { entries = await api('/api/mood'); } catch (_) { entries = []; }
    $('#moodNote').textContent = 'Saved to your sanctuary.';
  } else {
    entries = readLocalMood();
    $('#moodNote').textContent = 'Saved on this device only. Sign in to keep your rhythm across devices.';
  }
  renderMood(entries);
}

async function checkIn(mood) {
  if (state.me) {
    try { await api('/api/mood', { method: 'POST', body: { mood } }); } catch (e) { toast(e.message, true); return; }
  } else {
    const a = readLocalMood().filter(e => e.date !== todayKey());
    a.push({ date: todayKey(), mood });
    writeLocalMood(a);
  }
  toast('Checked in as ' + MOODS[mood].label.toLowerCase() + '. Thank you for noticing.');
  loadMood();
}

function renderMood(entries) {
  const byDate = new Map(entries.map(e => [e.date, e.mood]));
  const t = byDate.get(todayKey());
  $$('.mood').forEach(b => b.classList.toggle('on', b.dataset.mood === t));
  const box = $('#moodChart');
  if (!entries.length) { box.innerHTML = '<p class="mood-empty">Your emotional rhythm will appear here after your first check-in.</p>'; return; }
  const W = 600, H = 230, L = 40, R = 14, T = 16, B = 30, DAYS = 14;
  const days = [];
  for (let i = DAYS - 1; i >= 0; i--) { const d = new Date(); d.setDate(d.getDate() - i); days.push(d); }
  const X = i => L + (i / (DAYS - 1)) * (W - L - R);
  const Y = s => T + ((5 - s) / 4) * (H - T - B);
  let svg = '<svg viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="Mood over the last 14 days">';
  [1, 2, 3, 4, 5].forEach(s => {
    const mk = Object.values(MOODS).find(m => m.score === s);
    svg += '<line class="grid" x1="' + L + '" x2="' + (W - R) + '" y1="' + Y(s) + '" y2="' + Y(s) + '"/><text x="8" y="' + (Y(s) + 5) + '">' + mk.emoji + '</text>';
  });
  const pts = [];
  days.forEach((d, i) => {
    const key = d.toLocaleDateString('en-CA');
    const mood = byDate.get(key);
    if (i % 3 === 0 || i === DAYS - 1) svg += '<text x="' + X(i) + '" y="' + (H - 8) + '" text-anchor="middle">' + d.getDate() + '/' + (d.getMonth() + 1) + '</text>';
    if (mood && MOODS[mood]) pts.push({ x: X(i), y: Y(MOODS[mood].score), label: MOODS[mood].label, key });
  });
  if (pts.length > 1) svg += '<polyline class="line" points="' + pts.map(p => p.x.toFixed(1) + ',' + p.y.toFixed(1)).join(' ') + '"/>';
  pts.forEach(p => { svg += '<circle class="dot" cx="' + p.x.toFixed(1) + '" cy="' + p.y.toFixed(1) + '" r="5"><title>' + esc(p.key + ': ' + p.label) + '</title></circle>'; });
  box.innerHTML = svg + '</svg>';
}

/* ================= Aria ================= */
const aria = { turn: 0, busy: false, history: [] };

function initAria() {
  addMsg($('#ariaLog'), 'them', 'Hi, I am Aria. This is a quiet space and nothing here is judged. What is on your mind tonight?');
}

async function onAriaSubmit(e) {
  e.preventDefault();
  if (aria.busy) return;
  const input = $('#ariaInput'), log = $('#ariaLog');
  const text = input.value.trim();
  if (!text) return;
  input.value = '';
  addMsg(log, 'me', text);
  aria.busy = true;
  aria.history.push({ role: 'user', content: text });
  if (aria.history.length > 8) aria.history = aria.history.slice(-8);

  const typing = addMsg(log, 'sys', 'Aria is listening\u2026');
  try {
    const [r] = await Promise.all([
      api('/api/aria', { method: 'POST', body: { message: text, turn: aria.turn, history: aria.history } }),
      sleep(400)
    ]);
    typing.remove();
    addMsg(log, 'them', r.reply);
    aria.history.push({ role: 'assistant', content: r.reply });
    if (r.crisis) addCare(log);
    aria.turn++;
  } catch (ex) {
    typing.remove();
    const d = addMsg(log, 'sys', ex.message || 'I could not reach the server just now. If you need help right now, call Tele-MANAS 14416.');
    d.classList.add('care');
  } finally { aria.busy = false; }
}

/* ================= soul board ================= */
const reacted = (() => { try { return new Set(JSON.parse(localStorage.getItem('ss_reacted') || '[]')); } catch (_) { return new Set(); } })();
const saveReacted = () => { try { localStorage.setItem('ss_reacted', JSON.stringify(Array.from(reacted).slice(-300))); } catch (_) { /* ignore */ } };

function postHtml(p) {
  const btn = (t, emoji, label, n) => '<button class="react' + (reacted.has(p.id + ':' + t) ? ' done' : '') + '" type="button" data-r="' + t + '">' + emoji + ' ' + label + ' <span>' + n + '</span></button>';
  return '<article class="post" data-id="' + esc(p.id) + '"><div class="post-head"><b>' + esc(p.alias) + '</b><span>' + esc(p.category) + ' \u00B7 ' + esc(timeAgo(p.createdAt)) + '</span></div>' +
    '<p>' + esc(p.content) + '</p><div class="reacts">' +
    btn('hug', '\uD83E\uDD17', 'Hugs', p.hugs) + btn('tea', '\u2615', 'Warm Tea', p.tea) + btn('peace', '\uD83D\uDD4A\uFE0F', 'Peace', p.peace) + '</div></article>';
}

async function loadBoard() {
  try { state.posts = await api('/api/board'); renderBoard(); } catch (_) { $('#boardFeed').innerHTML = '<p class="muted">The board could not be loaded.</p>'; }
}

function renderBoard() {
  $('#boardFeed').innerHTML = state.posts.map(postHtml).join('') || '<p class="muted">Be the first to leave a thought.</p>';
}

function onBoardLive(p) {
  const i = state.posts.findIndex(x => x.id === p.id);
  if (i >= 0) {
    state.posts[i] = p;
    const el = $('#boardFeed .post[data-id="' + p.id + '"]');
    if (el) {
      const spans = $$('.react span', el);
      if (spans.length === 3) { spans[0].textContent = p.hugs; spans[1].textContent = p.tea; spans[2].textContent = p.peace; }
    }
  } else {
    state.posts.unshift(p);
    if (state.posts.length > 50) state.posts.pop();
    renderBoard();
  }
}

async function onBoardSubmit(e) {
  e.preventDefault();
  const ta = $('#boardText'), content = ta.value.trim();
  if (!content) { toast('Write a few words first.', true); return; }
  try {
    const r = await api('/api/board', { method: 'POST', body: { content, category: $('#boardCat').value } });
    ta.value = '';
    onBoardLive(r.post);
    toast('Posted anonymously.');
    if (r.crisis) toast('You are not alone. Tele-MANAS 14416 and KIRAN 1800-599-0019 are free to call, any time.');
  } catch (ex) { toast(ex.message, true); }
}

async function react(article, type) {
  const id = article.dataset.id, key = id + ':' + type;
  if (reacted.has(key)) { toast('You already sent that one. Thank you for caring.'); return; }
  try {
    const v = await api('/api/board/' + encodeURIComponent(id) + '/react', { method: 'POST', body: { type } });
    reacted.add(key); saveReacted();
    $('[data-r="' + type + '"]', article).classList.add('done');
    onBoardLive(v);
  } catch (ex) { toast(ex.message, true); }
}

/* ================= private consultation room ================= */
function subscribeRoom() {
  const r = state.room;
  if (!r || !state.stompReady) return;
  if (r.sub) { try { r.sub.unsubscribe(); } catch (_) { /* ignore */ } }
  r.sub = state.stomp.subscribe('/topic/consult/' + r.code, f => {
    const m = JSON.parse(f.body);
    if (m.type === 'MSG') {
      addMsg($('#roomLog'), m.from === 'patient' ? 'me' : 'them', m.text);
      if (m.from !== 'patient') $('#roomStatus').textContent = 'Your clinician is here.';
    }
  });
  $('#roomStatus').textContent = 'Connected. Waiting for your clinician to join at the scheduled time\u2026';
}

function updateRoomMeta() {
  const r = state.room; if (!r) return;
  $('#roomMeta').textContent = r.booking.doctorName + ' \u00B7 ' + fmtSlot(r.booking.slot) + ' \u00B7 ' + r.code + ' \u00B7 ' + (r.mask ? 'Joined anonymously' : 'Joined as ' + state.me.alias);
}

async function openRoom(code, push) {
  if (!state.me) { requireAuth(() => openRoom(code, push), 'Sign in to enter your private room.'); return; }
  let booking;
  try { booking = await api('/api/bookings/' + encodeURIComponent(code)); } catch (e) { toast(e.message, true); if (location.pathname.startsWith('/room/')) history.replaceState(null, '', '/'); return; }
  if (state.room) closeRoom(true);
  state.room = { code, booking, start: Date.now(), sub: null, stream: null, mask: true, timer: null, pushed: !!push };
  if (push) history.pushState({ room: code }, '', '/room/private/' + code);
  $('#roomLog').textContent = '';
  $('#videoFrame').classList.add('masked'); $('#videoFrame').classList.remove('live');
  $('#maskBtn').setAttribute('aria-pressed', 'true'); $('#maskBtn').textContent = 'Anonymous Privacy Mask: on';
  $('#camBtn').textContent = 'Start camera preview';
  updateRoomMeta();
  $('#roomStatus').textContent = 'Connecting\u2026';
  $('#room').hidden = false;
  document.body.style.overflow = 'hidden';
  const tick = () => {
    const s = Math.floor((Date.now() - state.room.start) / 1000);
    $('#roomTimer').textContent = String(Math.floor(s / 60)).padStart(2, '0') + ':' + String(s % 60).padStart(2, '0');
  };
  tick(); state.room.timer = setInterval(tick, 1000);
  try { await ensureStomp(); subscribeRoom(); } catch (e) { $('#roomStatus').textContent = e.message; }
  $('#roomInput').focus();
}

function stopCamera() {
  const r = state.room; if (!r || !r.stream) return;
  r.stream.getTracks().forEach(t => t.stop());
  r.stream = null;
  $('#localVideo').srcObject = null;
  $('#videoFrame').classList.remove('live');
  $('#camBtn').textContent = 'Start camera preview';
}

function closeRoom(fromHistory) {
  const r = state.room; if (!r) return;
  stopCamera();
  clearInterval(r.timer);
  if (r.sub) { try { r.sub.unsubscribe(); } catch (_) { /* ignore */ } }
  const pushed = r.pushed;
  state.room = null;
  $('#room').hidden = true;
  document.body.style.overflow = '';
  if (!fromHistory && location.pathname.startsWith('/room/')) {
    if (pushed) history.back(); else history.replaceState(null, '', '/');
  }
}

async function toggleCamera() {
  const r = state.room; if (!r) return;
  if (r.stream) { stopCamera(); return; }
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { toast('Camera is not available in this browser.', true); return; }
  try {
    r.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'user' }, audio: false });
    if (!state.room) { r.stream.getTracks().forEach(t => t.stop()); return; }
    $('#localVideo').srcObject = r.stream;
    $('#videoFrame').classList.add('live');
    $('#camBtn').textContent = 'Stop camera preview';
  } catch (_) { toast('Camera permission was not granted.', true); }
}

function toggleMask() {
  const r = state.room; if (!r) return;
  r.mask = !r.mask;
  $('#videoFrame').classList.toggle('masked', r.mask);
  $('#maskBtn').setAttribute('aria-pressed', String(r.mask));
  $('#maskBtn').textContent = 'Anonymous Privacy Mask: ' + (r.mask ? 'on' : 'off');
  updateRoomMeta();
}

function onRoomSubmit(e) {
  e.preventDefault();
  const r = state.room, input = $('#roomInput');
  const text = input.value.trim();
  if (!r || !text) return;
  if (!state.stompReady) { toast('Reconnecting, please try again in a moment.', true); return; }
  state.stomp.publish({ destination: '/app/consult/' + r.code, body: JSON.stringify({ text }) });
  input.value = '';
}

/* ================= init ================= */
function bind() {
  $('#soundBtn').addEventListener('click', toggleSound);
  $('#orbBtn').addEventListener('click', toggleBreath);
  $('#authBtn').addEventListener('click', () => { if (state.me) signOut(); else openAuth('login'); });
  $$('.tab').forEach(t => t.addEventListener('click', () => setAuthMode(t.dataset.tab)));
  $('#authForm').addEventListener('submit', onAuthSubmit);
  $('#reviewForm').addEventListener('submit', onReviewSubmit);
  $('#passBtn').addEventListener('click', openPass);
  $$('dialog').forEach(d => {
    d.addEventListener('click', e => { if (e.target === d) d.close(); });
    $$('[data-close]', d).forEach(b => b.addEventListener('click', () => d.close()));
  });

  $('#matchBtn').addEventListener('click', startMatch);
  $('#cancelMatchBtn').addEventListener('click', async () => { await leaveChat(); showLounge('Idle'); });
  $('#endBtn').addEventListener('click', async () => { await leaveChat(); showLounge('Idle'); toast('Chat ended and erased.'); });
  $('#nextBtn').addEventListener('click', async () => { await leaveChat(); startMatch(); });
  $('#chatForm').addEventListener('submit', e => {
    e.preventDefault();
    const input = $('#chatInput'), text = input.value.trim();
    if (!text || chat.phase !== 'chat' || chat.ended || !state.stompReady) return;
    state.stomp.publish({ destination: '/app/chat/' + chat.roomId, body: JSON.stringify({ clientId: state.clientId, text }) });
    input.value = '';
  });

  $('#doctorGrid').addEventListener('click', e => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    const code = b.closest('.doctor').dataset.code;
    if (b.dataset.act === 'book') openBooking(code); else if (b.dataset.act === 'rate') openReview(code);
  });
  $('#sanctuary').addEventListener('click', async e => {
    const b = e.target.closest('[data-act]'); if (!b) return;
    if (b.dataset.act === 'room') openRoom(b.dataset.code, true);
    else if (b.dataset.act === 'pass') openPass();
    else if (b.dataset.act === 'cancel') {
      if (confirm('Cancel this appointment?')) {
        try {
          await api('/api/bookings/' + b.dataset.code + '/cancel', { method: 'POST' });
          toast('Appointment cancelled.');
          await Promise.allSettled([loadAppointments(), loadDoctors()]);
        } catch (err) { toast(err.message, true); }
      }
    }
  });
  $$('.mood').forEach(b => b.addEventListener('click', () => checkIn(b.dataset.mood)));
  $('#ariaForm').addEventListener('submit', onAriaSubmit);
  $('#boardForm').addEventListener('submit', onBoardSubmit);
  $('#boardFeed').addEventListener('click', e => {
    const b = e.target.closest('[data-r]'); if (!b) return;
    react(b.closest('.post'), b.dataset.r);
  });

  $('#roomLeave').addEventListener('click', () => closeRoom(false));
  $('#camBtn').addEventListener('click', toggleCamera);
  $('#maskBtn').addEventListener('click', toggleMask);
  $('#roomForm').addEventListener('submit', onRoomSubmit);
  document.addEventListener('keydown', e => { if (e.key === 'Escape' && state.room && !$('dialog[open]')) closeRoom(false); });
  window.addEventListener('popstate', () => {
    if (state.room && !location.pathname.startsWith('/room/private/')) closeRoom(true);
  });
}

async function init() {
  initCosmos();
  initPointerFx();
  bind();
  initAria();
  try { setMe(await api('/api/me')); } catch (_) { setMe(null); }
  loadDoctors(); loadBoard(); loadMood();
  if (state.me) loadAppointments();
  ensureStomp().catch(() => { /* live features retry on demand */ });
  refreshStats(); setInterval(refreshStats, 15000);
  setInterval(tickCountdowns, 1000);
  const m = location.pathname.match(/^\/room\/private\/([A-Za-z0-9-]+)$/);
  if (m) openRoom(m[1], false);
}

if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init); else init();
})();
