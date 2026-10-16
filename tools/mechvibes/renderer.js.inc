    const bridgeStatus = document.createElement('p');
    bridgeStatus.style.cssText = 'font-size:12px;text-align:center;padding:8px;';
    bridgeStatus.textContent = 'BetterKeystrokes: waiting for Minecraft';
    app_body.appendChild(bridgeStatus);
    const bksBridge = require('./bks-bridge').start({
      status: text => { bridgeStatus.textContent = 'BetterKeystrokes: ' + text; },
      key: (keycode, down) => {
        if (!is_muted && current_keyboard_pack) iohook.emit(down ? 'keydown' : 'keyup', { keycode, bksBridgeEvent: true });
      },
      click: button => {
        if (is_muted || !playMouseSounds || !current_mouse_pack) return;
        const volume = BKS_SELFTEST ? 0 : store.get(MV_MOUSE_VOL_LSID);
        playMouseSound(String(button), volume, 'down');
        setTimeout(() => {
          if (!is_muted && playMouseSounds && current_mouse_pack) playMouseSound(String(button), volume, 'up');
        }, 25);
      },
    });
    window.addEventListener('beforeunload', () => bksBridge.closeSocket());
    if (BKS_SELFTEST) {
      // No native input listener, no audible output, no installed settings modified.
      setTimeout(async () => {
        const result = { ok: false, keyboardLoaded: false, mouseLoaded: false, decoded: false, heard: [] };
        try {
          const keyPack = current_keyboard_pack, mousePack = current_mouse_pack;
          result.keyboardLoaded = !!keyPack; result.mouseLoaded = !!mousePack;
          const sounds = [keyPack.sound, mousePack.sound['keycode-1'], mousePack.sound['keycode-01'], mousePack.sound['keycode-2'], mousePack.sound['keycode-02']];
          await Promise.all(sounds.map(sound => new Promise((resolve, reject) => {
            if (!sound) return reject(new Error('Missing selected sound'));
            sound.volume(0);
            if (sound.state() === 'loaded') return resolve();
            sound.once('load', resolve); sound.once('loaderror', () => reject(new Error('Audio decode failed')));
          })));
          result.decoded = true;
          const oldKey = playSound, oldMouse = playMouseSound;
          playSound = function (id, volume, up, phase) { result.heard.push(['K', id, phase]); return oldKey(id, 0, up, phase); };
          playMouseSound = function (id, volume, phase) { result.heard.push(['M', id, phase]); return oldMouse(id, 0, phase); };
          const dgram = require('dgram'), sender = dgram.createSocket('udp4');
          const id = '0123456789abcdef0123456789abcdef'; let seq = 0;
          const send = text => new Promise((resolve, reject) => sender.send(Buffer.from('BKS1 ' + id + ' ' + (++seq) + ' ' + text), 39189, '127.0.0.1', err => err ? reject(err) : resolve()));
          await send('H 1'); await send('K 42 1'); await send('K 42 0'); await send('M 1'); await send('M 2');
          await new Promise(resolve => setTimeout(resolve, 200));
          // A physical event during takeover must not add a second sound.
          iohook.emit('keydown', { keycode: 42 }); iohook.emit('keyup', { keycode: 42 });
          iohook.emit('mousedown', { button: 1 }); iohook.emit('mouseup', { button: 1 });
          await send('H 0'); sender.close();
          result.ok = result.decoded && result.heard.length === 6;
        } catch (error) { result.error = String(error); }
        ipcRenderer.send('bks-selftest-result', result);
      }, 1200);
    }
