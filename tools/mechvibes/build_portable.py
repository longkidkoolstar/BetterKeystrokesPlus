"""Build a separate personal Mechvibes copy from an authorized local installation.

Only this patch/build tool is public. No installed app, native addon, sound pack or
personal configuration is committed or uploaded by this tool.
"""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import shutil
import struct

BASE_SHA256 = "881dbb091241ea8184e18932042a9f91875576216dfd6c12ed4395eacb1dc813"


def normal_user_manifest(executable):
    """The local bridge does not need the upstream launcher's mandatory elevation."""
    import ctypes
    from ctypes import wintypes as w
    k = ctypes.WinDLL("kernel32", use_last_error=True)
    pointer = lambda value: ctypes.cast(value, w.LPCWSTR)
    k.LoadLibraryExW.argtypes = [w.LPCWSTR, w.HANDLE, w.DWORD]
    k.LoadLibraryExW.restype = w.HMODULE
    k.FindResourceExW.argtypes = [w.HMODULE, w.LPCWSTR, w.LPCWSTR, w.WORD]
    k.FindResourceExW.restype = w.HANDLE
    k.SizeofResource.argtypes = [w.HMODULE, w.HANDLE]
    k.SizeofResource.restype = w.DWORD
    k.LoadResource.argtypes = [w.HMODULE, w.HANDLE]
    k.LoadResource.restype = w.HANDLE
    k.LockResource.argtypes = [w.HANDLE]
    k.LockResource.restype = ctypes.c_void_p
    k.FreeLibrary.argtypes = [w.HMODULE]
    # Resource IDs are integer pointers, not strings; never dereference them in a callback.
    callback_type = ctypes.WINFUNCTYPE(w.BOOL, w.HMODULE, ctypes.c_void_p, ctypes.c_void_p, w.WORD, ctypes.c_void_p)
    languages = []
    callback = callback_type(lambda module, kind, name, language, unused: languages.append(language) or True)
    k.EnumResourceLanguagesW.argtypes = [w.HMODULE, w.LPCWSTR, w.LPCWSTR, callback_type, ctypes.c_void_p]
    module = k.LoadLibraryExW(str(executable), None, 2)
    if not module:
        raise ctypes.WinError(ctypes.get_last_error())
    replacements = []
    try:
        if not k.EnumResourceLanguagesW(module, pointer(24), pointer(1), callback, None):
            raise ctypes.WinError(ctypes.get_last_error())
        for language in languages:
            resource = k.FindResourceExW(module, pointer(24), pointer(1), language)
            data = ctypes.string_at(k.LockResource(k.LoadResource(module, resource)), k.SizeofResource(module, resource))
            old = b'level="requireAdministrator"'
            if old not in data:
                raise ValueError("Unexpected upstream execution-level manifest")
            replacements.append((language, data.replace(old, b'level="asInvoker"')))
    finally:
        k.FreeLibrary(module)
    k.BeginUpdateResourceW.argtypes = [w.LPCWSTR, w.BOOL]
    k.BeginUpdateResourceW.restype = w.HANDLE
    k.UpdateResourceW.argtypes = [w.HANDLE, w.LPCWSTR, w.LPCWSTR, w.WORD, ctypes.c_void_p, w.DWORD]
    k.EndUpdateResourceW.argtypes = [w.HANDLE, w.BOOL]
    update = k.BeginUpdateResourceW(str(executable), False)
    if not update:
        raise ctypes.WinError(ctypes.get_last_error())
    try:
        for language, data in replacements:
            content = ctypes.create_string_buffer(data)
            if not k.UpdateResourceW(update, pointer(24), pointer(1), language, content, len(data)):
                raise ctypes.WinError(ctypes.get_last_error())
    except BaseException:
        k.EndUpdateResourceW(update, True)
        raise
    if not k.EndUpdateResourceW(update, False):
        raise ctypes.WinError(ctypes.get_last_error())


def read_asar(path):
    data = path.read_bytes()
    first, header_size, _, length = struct.unpack_from("<4I", data)
    if first != 4:
        raise ValueError("Unsupported ASAR header")
    tree = json.loads(data[16:16 + length])
    base = 8 + header_size
    files = {}

    def walk(node, prefix=""):
        for name, entry in node.get("files", {}).items():
            target = prefix + name
            if "files" in entry:
                walk(entry, target + "/")
            elif not entry.get("unpacked") and "offset" in entry:
                start = base + int(entry["offset"])
                files[target] = data[start:start + entry["size"]]
    walk(tree)
    return tree, files


def write_asar(path, original, files):
    tree = copy.deepcopy(original)
    offset = 0
    chunks = []
    # Register added source files before calculating offsets.
    for name in files:
        node = tree
        parts = name.split("/")
        for directory in parts[:-1]:
            node = node.setdefault("files", {}).setdefault(directory, {"files": {}})
        node["files"].setdefault(parts[-1], {"size": 0, "offset": "0"})

    def walk(node, prefix=""):
        nonlocal offset
        for name, entry in node.get("files", {}).items():
            target = prefix + name
            if "files" in entry:
                walk(entry, target + "/")
            elif not entry.get("unpacked") and target in files:
                content = files[target]
                entry["offset"], entry["size"] = str(offset), len(content)
                entry.pop("integrity", None)
                offset += len(content)
                chunks.append(content)
    walk(tree)
    header = json.dumps(tree, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    payload = struct.pack("<I", len(header)) + header
    payload += b"\0" * (-len(payload) % 4)
    pickle = struct.pack("<I", len(payload)) + payload
    with path.open("wb") as output:
        output.write(struct.pack("<II", 4, len(pickle)))
        output.write(pickle)
        for chunk in chunks:
            output.write(chunk)


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError("Unexpected upstream source layout for: " + old[:70])
    return text.replace(old, new, 1)


def patch(files, version):
    main = files["src/main.js"].decode("utf-8")
    main = replace_once(main, "const fs = require('fs-extra');", """const fs = require('fs-extra');
// Separate portable settings: do this before importing any electron-store handlers.
app.setName('MechvibesPlusPlus-BKBridge');
const bksData = path.join(path.dirname(process.execPath), 'bridge-data');
fs.ensureDirSync(bksData);
app.setPath('userData', bksData);
global.bks_selftest = process.env.BKS_SOUND_SELFTEST === '1' || app.commandLine.hasSwitch('bks-self-test');
if (global.bks_selftest) {
  const diagnostic = value => fs.appendFileSync(path.join(path.dirname(process.execPath), 'bridge-selftest-debug.txt'), value + '\\n');
  diagnostic('Main process ready for isolated test');
  process.on('uncaughtException', error => { diagnostic(String(error.stack || error)); app.exit(4); });
  app.on('web-contents-created', (_event, contents) => {
    contents.on('console-message', (_event, level, message) => diagnostic('Renderer: ' + message));
    contents.on('did-fail-load', (_event, code, description) => diagnostic('Load: ' + code + ' ' + description));
  });
  ipcMain.on('bks-selftest-result', (_event, result) => {
    fs.writeJsonSync(path.join(path.dirname(process.execPath), 'bridge-selftest.json'), result);
    app.exit(result.ok ? 0 : 2);
  });
  setTimeout(() => app.exit(3), 20000).unref();
}""")
    main = replace_once(main, "      preload: path.join(__dirname, 'app.js'),", "      preload: path.join(__dirname, 'app.js'),\n      backgroundThrottling: false,")
    main = replace_once(main, "    show,", "    show: show && !global.bks_selftest,")
    main = replace_once(main, "win.loadFile('./src/app.html');", "win.loadFile(path.join(__dirname, 'app.html'));")
    main = replace_once(main, "editor_window.loadFile('./src/editor.html');", "editor_window.loadFile(path.join(__dirname, 'editor.html'));")
    main = replace_once(main, "tray.setToolTip('MechvibesPlusPlus');", "tray.setToolTip('Mechvibes++ BetterKeystrokes Bridge');")
    # A portable companion must not register itself over the installed app's startup entry.
    start = main.index("      {\n        label: 'Enable at Startup',")
    end = main.index("      {\n        label: 'Quit',", start)
    main = main[:start] + main[end:]
    files["src/main.js"] = main.encode("utf-8")

    renderer = files["src/app.js"].decode("utf-8")
    renderer = replace_once(renderer, "let is_mousesounds = store.get('mechvibes-mouse') || true;", "let is_mousesounds = store.get('mechvibes-mouse') === undefined ? true : store.get('mechvibes-mouse');")
    renderer = replace_once(renderer, "const iohook = require('iohook');", """const iohook = require('iohook');
const BKS_SELFTEST = remote.getGlobal('bks_selftest') === true;
if (BKS_SELFTEST) { iohook.start = () => {}; iohook.stop = () => {}; }""")
    start = renderer.index("    // check for new version\n")
    end = renderer.index("    // a little hack for open link", start)
    renderer = renderer[:start] + "    // Portable bridge updates are delivered separately.\n\n" + renderer[end:]
    renderer = replace_once(renderer, "    iohook.on('mousedown', ({ button }) => {", "    iohook.on('mousedown', ({ button }) => {\n      if (bksBridge.claimsMouse(button)) return;")
    renderer = replace_once(renderer, "    iohook.on('mouseup', () => {", "    iohook.on('mouseup', ({ button }) => {\n      if (bksBridge.claimsMouse(button)) return;")
    renderer = replace_once(renderer, "    iohook.on('keyup', ({ keycode }) => {", "    iohook.on('keyup', ({ keycode, bksBridgeEvent }) => {\n      if (!bksBridgeEvent && bksBridge.claimsKeyboard(keycode)) return;")
    renderer = replace_once(renderer, "    iohook.on('keydown', ({ keycode }) => {", "    iohook.on('keydown', ({ keycode, bksBridgeEvent }) => {\n      if (!bksBridgeEvent && bksBridge.claimsKeyboard(keycode)) return;")
    renderer = replace_once(renderer, "keyPressedList.splice(keyPressedList.indexOf(keycode), 1);", "if (keyPressedList.indexOf(keycode) >= 0) keyPressedList.splice(keyPressedList.indexOf(keycode), 1);")
    # Binds callbacks in the same scope as the user's current mute/volume/pack options.
    marker = "    iohook.on('mousedown', ({ button }) => {"
    renderer = replace_once(renderer, marker, (Path(__file__).parent / "renderer.js.inc").read_text(encoding="utf-8") + "\n" + marker)
    files["src/app.js"] = renderer.encode("utf-8")
    files["src/bks-bridge.js"] = (Path(__file__).parent / "bridge.js").read_bytes()
    package = json.loads(files["package.json"])
    package.update(name="mechvibesplusplus-bksbridge", productName="Mechvibes++ BetterKeystrokes Bridge", version="2.4.0-bks." + version)
    files["package.json"] = json.dumps(package, indent=2).encode("utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--config", type=Path)
    args = parser.parse_args()
    source, output = args.source.resolve(), args.output.resolve()
    archive = source / "resources/app.asar"
    if source == output or source in output.parents or output in source.parents:
        raise ValueError("Use a separate output directory outside the installed app")
    if output.exists():
        raise ValueError("Output already exists; use a new versioned directory")
    if hashlib.sha256(archive.read_bytes()).hexdigest() != BASE_SHA256:
        raise ValueError("MechvibesPlusPlus input differs from the reviewed v2.4.0 build")
    original_hash = hashlib.sha256(archive.read_bytes()).hexdigest()
    tree, files = read_asar(archive)
    version = (Path(__file__).resolve().parents[2] / "VERSION").read_text().strip()
    patch(files, version)
    shutil.copytree(source, output, ignore=shutil.ignore_patterns("Uninstall*.exe"))
    normal_user_manifest(output / "MechvibesPlusPlus.exe")
    write_asar(output / "resources/app.asar", tree, files)
    # Independently read back all changed/added sources after rebuilding offsets.
    _, delivered = read_asar(output / "resources/app.asar")
    for name in ("src/main.js", "src/app.js", "src/bks-bridge.js", "package.json"):
        if delivered[name] != files[name]:
            raise ValueError("ASAR verification failed: " + name)
    if args.config:
        config = json.loads(args.config.read_text(encoding="utf-8-sig"))
        permitted = {key: value for key, value in config.items() if key.startswith("mechvibes-") and key != "mechvibes-startup"}
        folder = output / "bridge-data"
        folder.mkdir()
        (folder / "config.json").write_text(json.dumps(permitted, indent=2), encoding="utf-8")
    (output / "Launch-Mechvibes-Bridge.cmd").write_text(
        '@echo off\nsetlocal\nset ELECTRON_RUN_AS_NODE=\nset BKS_SOUND_SELFTEST=\nstart "" /D "%~dp0" "%~dp0MechvibesPlusPlus.exe"\n',
        encoding="ascii")
    if hashlib.sha256(archive.read_bytes()).hexdigest() != original_hash:
        raise ValueError("Original installation changed unexpectedly")
    print("Built and verified a separate portable companion:", output)
    print("Original Mechvibes installation unchanged. Config copied read-only into portable settings.")


if __name__ == "__main__":
    main()
