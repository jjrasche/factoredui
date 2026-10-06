"""Evaluate JavaScript in one Chrome tab on the plugged-in phone and print the result.

Needs: adb forward tcp:9222 localabstract:chrome_devtools_remote
Only the tab whose id you name is touched; list candidates with --find <text in url>.
"""

import argparse
import asyncio
import base64
import json
import pathlib
import urllib.request

import websockets

DEVTOOLS = "http://127.0.0.1:9222/json"


def find_tabs(url_text):
    tabs = json.load(urllib.request.urlopen(DEVTOOLS))
    return [(tab["id"], tab["title"], tab["url"]) for tab in tabs if url_text in tab["url"]]


async def call(socket, number, method, **params):
    await socket.send(json.dumps({"id": number, "method": method, "params": params}))
    while True:
        reply = json.loads(await socket.recv())
        if reply.get("id") == number:
            return reply


async def evaluate(tab_id, expression, should_reload, shot_path):
    tabs = json.load(urllib.request.urlopen(DEVTOOLS))
    tab = next(candidate for candidate in tabs if candidate["id"] == tab_id)
    async with websockets.connect(tab["webSocketDebuggerUrl"], max_size=None) as socket:
        if should_reload:
            await call(socket, 1, "Page.enable")
            await call(socket, 2, "Network.enable")
            await call(socket, 3, "Network.clearBrowserCache")
            await call(socket, 4, "Page.reload", ignoreCache=True)
            await asyncio.sleep(7)
        reply = await call(socket, 5, "Runtime.evaluate", expression=expression, returnByValue=True, awaitPromise=True)
        if shot_path:
            shot = await call(socket, 6, "Page.captureScreenshot", format="png")
            pathlib.Path(shot_path).write_bytes(base64.b64decode(shot["result"]["data"]))
        return reply["result"].get("result", reply["result"]).get("value", reply["result"])


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--find", help="list tabs whose url contains this text")
    parser.add_argument("--tab", help="tab id to evaluate in")
    parser.add_argument("--eval", dest="expression", help="JavaScript expression to evaluate")
    parser.add_argument("--reload", action="store_true", help="clear cache and reload the tab first")
    parser.add_argument("--shot", help="save a PNG of the page (only that tab's page) to this path")
    arguments = parser.parse_args()
    if arguments.find:
        for found in find_tabs(arguments.find):
            print(*found)
        return
    print(json.dumps(asyncio.run(evaluate(arguments.tab, arguments.expression, arguments.reload, arguments.shot)), indent=1))


if __name__ == "__main__":
    main()
