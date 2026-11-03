import struct
import sys
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

TILE_PIXELS = 256
EDGE_PIXELS = 3
CONTENT_TYPES = {".html": "text/html", ".js": "text/javascript", ".wasm": "application/wasm", ".json": "application/json",
                 ".png": "image/png", ".css": "text/css", ".map": "application/json"}


def solid_tile(red, green, blue):
    rows = []
    for row in range(TILE_PIXELS):
        line = bytearray([0])
        for column in range(TILE_PIXELS):
            on_edge = min(row, column, TILE_PIXELS - 1 - row, TILE_PIXELS - 1 - column) < EDGE_PIXELS
            line += bytes((0, 0, 0)) if on_edge else bytes((red, green, blue))
        rows.append(bytes(line))

    def chunk(kind, body):
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", TILE_PIXELS, TILE_PIXELS, 8, 2, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(b"".join(rows))) + chunk(b"IEND", b"")


def tile_for(path):
    parts = path.split("/")
    name, second, third = parts[2], int(parts[4]), int(parts[5])
    if name == "satellite":
        return solid_tile((third * 37) % 256, (second * 53) % 256, 160)
    return solid_tile(200, (second * 37) % 256, (third * 53) % 256)


def make_handler(distribution, request_log):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            path = self.path.split("?")[0]
            with request_log.open("a") as log:
                log.write(path + "\n")
            if path.startswith("/t/"):
                body, kind = tile_for(path), "image/png"
            else:
                target = distribution / "index.html" if path == "/" else (distribution / path.lstrip("/")).resolve()
                if not str(target).startswith(str(distribution)) or not target.is_file():
                    self.send_response(404)
                    self.end_headers()
                    return
                body, kind = target.read_bytes(), CONTENT_TYPES.get(target.suffix, "application/octet-stream")
            self.send_response(200)
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *arguments):
            pass

    return Handler


if __name__ == "__main__":
    distribution_folder = Path(sys.argv[1]).resolve()
    port = int(sys.argv[2])
    log_file = Path(sys.argv[3])
    ThreadingHTTPServer(("127.0.0.1", port), make_handler(distribution_folder, log_file)).serve_forever()
