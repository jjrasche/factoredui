"""Serve the yard overlay page on loopback and receive captures into a private folder.

Reach it from a plugged-in Android phone with: adb reverse tcp:PORT tcp:PORT
Captures arrive by PUT /captures/<id>/photo.jpg and PUT /captures/<id>/capture.json.
"""

import argparse
import functools
import http.server
import pathlib
import re
import tempfile

LOOPBACK = "127.0.0.1"
CAPTURE_PATH = re.compile(r"^/captures/(cap-[0-9a-z-]{8,64})/(photo\.jpg|capture\.json)$")
MAX_PHOTO_BYTES = 40 * 1024 * 1024
MAX_RECORD_BYTES = 1024 * 1024
FILE_LIMITS = {"photo.jpg": MAX_PHOTO_BYTES, "capture.json": MAX_RECORD_BYTES}
CONTENT_TYPES = {".mjs": "text/javascript", ".js": "text/javascript", ".wasm": "application/wasm", ".json": "application/json"}


class YardHandler(http.server.SimpleHTTPRequestHandler):
    extensions_map = {**http.server.SimpleHTTPRequestHandler.extensions_map, **CONTENT_TYPES}

    def __init__(self, *args, captures_dir, **kwargs):
        self.captures_dir = captures_dir
        super().__init__(*args, **kwargs)

    def own_origins(self):
        port = self.server.server_address[1]
        return {f"localhost:{port}", f"{LOOPBACK}:{port}"}

    def is_from_this_page(self):
        if self.headers.get("Host") not in self.own_origins():
            return False
        origin = self.headers.get("Origin")
        return origin is None or origin.removeprefix("http://") in self.own_origins()

    def do_GET(self):
        if not self.is_from_this_page():
            return self.send_error(403, "wrong host")
        super().do_GET()

    def do_HEAD(self):
        if not self.is_from_this_page():
            return self.send_error(403, "wrong host")
        super().do_HEAD()

    def do_PUT(self):
        if not self.is_from_this_page():
            return self.send_error(403, "wrong host or origin")
        matched = CAPTURE_PATH.match(self.path)
        if not matched:
            return self.send_error(404, "not a capture path")
        capture_id, file_name = matched.groups()
        length = self.headers.get("Content-Length")
        if length is None or not length.isdigit():
            return self.send_error(411, "length required")
        if int(length) > FILE_LIMITS[file_name]:
            return self.send_error(413, "too large")
        self.store_capture_file(capture_id, file_name, self.rfile.read(int(length)))
        self.send_response(201)
        self.send_header("Content-Length", "0")
        self.end_headers()

    def store_capture_file(self, capture_id, file_name, body):
        folder = self.captures_dir / capture_id
        folder.mkdir(parents=True, exist_ok=True)
        with tempfile.NamedTemporaryFile(dir=folder, delete=False) as partial:
            partial.write(body)
        pathlib.Path(partial.name).replace(folder / file_name)


def make_server(web_dir, captures_dir, port):
    handler = functools.partial(YardHandler, directory=str(web_dir), captures_dir=pathlib.Path(captures_dir))
    return http.server.ThreadingHTTPServer((LOOPBACK, port), handler)


def main():
    here = pathlib.Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--port", type=int, default=8000)
    parser.add_argument("--captures", default=str(here / "captures"), help="private folder that receives uploads")
    arguments = parser.parse_args()
    server = make_server(here / "web", arguments.captures, arguments.port)
    print(f"serving {here / 'web'} on http://localhost:{arguments.port}/ ; captures go to {arguments.captures}")
    server.serve_forever()


if __name__ == "__main__":
    main()
