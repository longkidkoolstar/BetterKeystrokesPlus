'use strict';

// Numeric loopback sound events only. Does not inject input, log keys or send to the internet.
function createRouter(options) {
  const now = options.now || Date.now;
  const supported = new Set(Array.from({ length: 88 }, (_, i) => i + 1).concat([91,92,93,3597,3612,3613,3637,3639,3640,3653,3675,3676,3677,60999,61000,61001,61003,61005,61007,61008,61009,61010,61011]));
  let session = '', sequence = 0, lastSeen = 0, active = false;
  const states = new Map();
  function deactivate() {
    if (active) {
      for (const [code, down] of states) if (down) options.key(code, false);
    }
    states.clear(); active = false;
    if (options.status) options.status('Physical sounds; waiting for Minecraft');
  }
  function expire() { if (active && now() - lastSeen > 750) deactivate(); }
  function receive(buffer) {
    if (buffer.length > 2048) return null;
    const parts = buffer.toString('ascii').split(' ');
    if (parts[0] !== 'BKS1' || !/^[a-f0-9]{32}$/.test(parts[1] || '') || !/^\d{1,15}$/.test(parts[2] || '')) return null;
    const seq = Number(parts[2]), kind = parts[3], code = Number(parts[4]);
    if (!Number.isSafeInteger(seq) || seq < 1 || !/^\d+$/.test(parts[4] || '')) return null;
    if (kind === 'H') { if (parts.length !== 5 || (code !== 0 && code !== 1)) return null; }
    else if (kind === 'K') { if (parts.length !== 6 || !Number.isInteger(code) || code < 1 || code > 65535 || !/^[01]$/.test(parts[5] || '')) return null; }
    else if (kind === 'M') { if (parts.length !== 5 || (code !== 1 && code !== 2)) return null; }
    else return null;
    expire();
    if (session !== parts[1]) {
      if (active && now() - lastSeen <= 750) return null;
      if (kind !== 'H') return null;
      deactivate(); session = parts[1]; sequence = 0;
    }
    if (seq <= sequence) return null;
    sequence = seq; lastSeen = now();
    if (kind === 'H') {
      if (code === 0) deactivate();
      else { active = true; if (options.status) options.status('Connected: Minecraft click/key sounds'); }
    } else if (active && kind === 'K') {
      const down = parts[5] === '1';
      if (states.get(code) !== down) { states.set(code, down); options.key(code, down); }
    } else if (active && kind === 'M') options.click(code);
    return 'BKS1 ACK ' + session;
  }
  return {
    receive, expire, close: deactivate,
    claimsKeyboard(code) { expire(); return active && (code === undefined || supported.has(code)); },
    claimsMouse(button) { expire(); return active && (button === 1 || button === 2); },
  };
}
function start(options) {
  const dgram = require('dgram');
  const router = createRouter(options), socket = dgram.createSocket('udp4');
  socket.on('message', (buffer, peer) => {
    if (peer.address !== '127.0.0.1') return;
    const ack = router.receive(buffer);
    if (ack) socket.send(Buffer.from(ack, 'ascii'), peer.port, '127.0.0.1', () => {});
  });
  socket.on('error', () => { router.close(); if (options.status) options.status('Bridge unavailable: local port is busy'); });
  socket.bind(options.port || 39189, '127.0.0.1');
  const timer = setInterval(router.expire, 100);
  if (timer && typeof timer.unref === 'function') timer.unref();
  router.closeSocket = () => { clearInterval(timer); router.close(); try { socket.close(); } catch (_) {} };
  return router;
}
module.exports = { start, createRouter };
