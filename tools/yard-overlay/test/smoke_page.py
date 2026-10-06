"""Drive the yard overlay page in real headless Chrome: draw, toggle, align, save, upload, then reload with the server stopped."""

import asyncio
import base64
import json
import os
import pathlib
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.request

import websockets

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import serve

CHROME_PATHS = [r"C:\Program Files\Google\Chrome\Application\chrome.exe", r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"]
WEB = pathlib.Path(__file__).resolve().parent.parent / "web"

PAINTED_PIXELS = """(() => {
  const c = document.getElementById('view'); const d = c.getContext('2d').getImageData(0, 0, c.width, c.height).data;
  let painted = 0; for (let i = 0; i < d.length; i += 4) if (d[i] !== 16 || d[i+1] !== 20 || d[i+2] !== 24) painted++;
  return painted; })()"""

TAP_AT = """((cx, cy) => {
  const c = document.getElementById('view'); const b = c.getBoundingClientRect(); const s = c.width / b.width;
  window.yardOverlay.onStageTap({ clientX: b.left + cx / s, clientY: b.top + cy / s }); })"""


def free_port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


class Page:
    def __init__(self, socket_connection):
        self.socket = socket_connection
        self.next_id = 0

    async def send(self, method, **params):
        self.next_id += 1
        await self.socket.send(json.dumps({"id": self.next_id, "method": method, "params": params}))
        while True:
            reply = json.loads(await self.socket.recv())
            if reply.get("id") == self.next_id:
                if "error" in reply:
                    raise RuntimeError(reply["error"])
                return reply["result"]

    async def evaluate(self, expression):
        result = await self.send("Runtime.evaluate", expression=expression, awaitPromise=True, returnByValue=True)
        if "exceptionDetails" in result:
            raise RuntimeError(result["exceptionDetails"].get("exception", {}).get("description", result["exceptionDetails"]))
        return result["result"].get("value")

    async def wait_for(self, expression, seconds=15):
        deadline = time.time() + seconds
        while time.time() < deadline:
            if await self.evaluate(expression):
                return True
            await asyncio.sleep(0.2)
        return False


results = []


def check(name, passed, detail=""):
    results.append(passed)
    print(("PASS" if passed else "FAIL"), name, detail)


async def take_picture(page, name):
    folder = os.environ.get("YARD_SHOT_DIR")
    if not folder:
        return
    shot = await page.send("Page.captureScreenshot", format="png")
    pathlib.Path(folder, name).write_bytes(base64.b64decode(shot["data"]))


async def run_checks(page, port, captures, server):
    await page.send("Page.enable")
    await page.send("Page.navigate", url=f"http://localhost:{port}/")
    check("the twin loads", await page.wait_for("window.yardOverlay && yardOverlay.state.twin !== null"))
    await asyncio.sleep(0.5)
    await take_picture(page, "01-overlay-on-empty-stage.png")
    painted = await page.evaluate(PAINTED_PIXELS)
    check("the overlay paints pixels over the empty stage", painted > 500, f"{painted} painted")

    await page.evaluate("document.querySelectorAll('#layers input').forEach(i => { i.checked = false; i.dispatchEvent(new Event('change')); })")
    await asyncio.sleep(0.4)
    check("with every layer toggled off nothing is painted", await page.evaluate(PAINTED_PIXELS) == 0)
    await page.evaluate("document.querySelectorAll('#layers input')[3].click()")
    await asyncio.sleep(0.4)
    one_layer = await page.evaluate(PAINTED_PIXELS)
    check("one layer toggled back on paints something", one_layer > 0, f"{one_layer} painted")
    await page.evaluate("document.querySelectorAll('#layers input').forEach(i => { if (!i.checked) i.click(); })")
    await asyncio.sleep(0.4)
    all_layers = await page.evaluate(PAINTED_PIXELS)
    check("all layers on paint more than one layer", all_layers > one_layer, f"{all_layers} vs {one_layer}")

    await page.evaluate("""(async () => {
      const c = new OffscreenCanvas(640, 480); const g = c.getContext('2d');
      g.fillStyle = '#87b8e8'; g.fillRect(0, 0, 640, 480); g.fillStyle = '#4a8a3a'; g.fillRect(0, 300, 640, 180);
      await yardOverlay.useBlobAsPhoto(await c.convertToBlob({ type: 'image/jpeg' }), 'file'); })()""")
    check("a photo becomes the background", await page.evaluate("yardOverlay.state.background.kind") == "photo")

    await page.evaluate("yardOverlay.startAlignment(); yardOverlay.drawNow()")
    anchors = await page.evaluate("yardOverlay.state.visibleAnchors.map(a => ({ id: a.id, screen: a.screen }))")
    check("the overlay offers items to align on", len(anchors) >= 2, f"{len(anchors)} anchors")
    first, second = anchors[0], anchors[-1]
    for anchor in (first, second):
        await page.evaluate(f"({TAP_AT})({anchor['screen'][0]}, {anchor['screen'][1]})")
        await page.evaluate(f"({TAP_AT})({anchor['screen'][0] + 30}, {anchor['screen'][1] - 20})")
    await take_picture(page, "02-photo-aligned.png")
    taps = await page.evaluate("yardOverlay.state.taps.length")
    shifted = await page.evaluate("yardOverlay.state.alignment.translateX")
    check("two tap pairs complete the alignment", taps == 2 and await page.evaluate("yardOverlay.state.alignFlow.step") == "idle", f"taps={taps}")
    check("the alignment moves the overlay", abs(shifted) > 1, f"translateX={shifted:.1f}")

    await page.evaluate("yardOverlay.saveCurrentCapture()")
    await page.evaluate("yardOverlay.uploadNow()")
    folders = list(captures.glob("cap-*"))
    check("an uploaded capture lands in the private folder", len(folders) == 1, str(folders))
    if folders:
        record = json.loads((folders[0] / "capture.json").read_text())
        check("the capture keeps the two alignment taps", len(record["taps"]) == 2)
        check("the capture keeps the viewpoint, layers and twin versions", {"viewpoint", "enabled_layers", "twin", "alignment"} <= set(record))
        check("the capture file does not carry the photo bytes inline", "photo" not in record and record["photo_bytes"] > 100)
        check("the photo file is a real image", (folders[0] / "photo.jpg").read_bytes()[:2] == b"\xff\xd8")

    check("the page reports it is ready offline", await page.wait_for("document.getElementById('status-cache').textContent.includes('ready')"))
    server.shutdown()
    server.server_close()
    await page.send("Page.navigate", url=f"http://localhost:{port}/")
    check("with the server stopped the cached page still loads its twin", await page.wait_for("window.yardOverlay && yardOverlay.state.twin !== null", 15))
    await asyncio.sleep(0.5)
    check("and still paints the overlay", await page.evaluate(PAINTED_PIXELS) > 500)


async def main():
    workspace = tempfile.TemporaryDirectory()
    captures = pathlib.Path(workspace.name) / "captures"
    server = serve.make_server(WEB, captures, 0)
    port = server.server_address[1]
    threading.Thread(target=server.serve_forever, daemon=True).start()
    debug_port = free_port()
    chrome = next(path for path in CHROME_PATHS if pathlib.Path(path).exists())
    browser = subprocess.Popen(
        [chrome, "--headless=new", f"--remote-debugging-port={debug_port}", f"--user-data-dir={workspace.name}/profile", "--no-first-run", "--window-size=800,900", "about:blank"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        targets = []
        for _ in range(50):
            try:
                targets = json.load(urllib.request.urlopen(f"http://127.0.0.1:{debug_port}/json"))
                if any(target["type"] == "page" for target in targets):
                    break
            except OSError:
                pass
            time.sleep(0.2)
        page_target = next(target for target in targets if target["type"] == "page")
        async with websockets.connect(page_target["webSocketDebuggerUrl"], max_size=None) as connection:
            await run_checks(Page(connection), port, captures, server)
    finally:
        browser.terminate()
        server.server_close()
    print(f"{sum(results)}/{len(results)} checks passed")
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(asyncio.run(main()))
