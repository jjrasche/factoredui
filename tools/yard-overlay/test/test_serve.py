import http.client
import json
import pathlib
import sys
import tempfile
import threading
import unittest

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import serve

CAPTURE_ID = "cap-20261006120000-abc123"


class ServerCase(unittest.TestCase):
    def setUp(self):
        self.workspace = tempfile.TemporaryDirectory()
        root = pathlib.Path(self.workspace.name)
        self.web = root / "web"
        self.captures = root / "captures"
        self.web.mkdir()
        (self.web / "index.html").write_text("<html>page</html>")
        (self.web / "app.js").write_text("export {}")
        (self.web / "module.wasm").write_bytes(b"\0asm")
        self.server = serve.make_server(self.web, self.captures, 0)
        self.port = self.server.server_address[1]
        threading.Thread(target=self.server.serve_forever, daemon=True).start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.workspace.cleanup()

    def request(self, method, path, body=None, headers=None):
        connection = http.client.HTTPConnection("127.0.0.1", self.port)
        sent = {"Host": f"localhost:{self.port}", **(headers or {})}
        connection.request(method, path, body=body, headers=sent)
        response = connection.getresponse()
        data = response.read()
        connection.close()
        return response.status, response.getheader("Content-Type"), data


class PageServingTest(ServerCase):
    def test_the_page_is_served_at_the_root(self):
        status, _, body = self.request("GET", "/")
        self.assertEqual(200, status)
        self.assertIn(b"page", body)

    def test_scripts_and_wasm_get_the_types_a_browser_needs(self):
        self.assertEqual("text/javascript", self.request("GET", "/app.js")[1])
        self.assertEqual("application/wasm", self.request("GET", "/module.wasm")[1])

    def test_a_second_server_cannot_take_a_port_that_is_in_use(self):
        with self.assertRaises(OSError):
            serve.make_server(self.web, self.captures, self.port)

    def test_the_server_listens_on_loopback_only(self):
        self.assertEqual("127.0.0.1", self.server.server_address[0])

    def test_a_request_for_another_host_name_is_refused(self):
        status, _, _ = self.request("GET", "/", headers={"Host": "evil.example:8000"})
        self.assertEqual(403, status)

    def test_captures_are_not_served_back_out(self):
        self.request("PUT", f"/captures/{CAPTURE_ID}/capture.json", body=b"{}", headers={"Content-Type": "application/json"})
        status, _, _ = self.request("GET", f"/captures/{CAPTURE_ID}/capture.json")
        self.assertEqual(404, status)


class CaptureReceivingTest(ServerCase):
    def test_a_photo_and_its_record_land_in_the_capture_folder(self):
        record = json.dumps({"id": CAPTURE_ID}).encode()
        self.assertEqual(201, self.request("PUT", f"/captures/{CAPTURE_ID}/photo.jpg", body=b"\xff\xd8photo")[0])
        self.assertEqual(201, self.request("PUT", f"/captures/{CAPTURE_ID}/capture.json", body=record)[0])
        self.assertEqual(b"\xff\xd8photo", (self.captures / CAPTURE_ID / "photo.jpg").read_bytes())
        self.assertEqual({"id": CAPTURE_ID}, json.loads((self.captures / CAPTURE_ID / "capture.json").read_text()))

    def test_sending_a_capture_again_replaces_it(self):
        self.request("PUT", f"/captures/{CAPTURE_ID}/photo.jpg", body=b"first")
        self.request("PUT", f"/captures/{CAPTURE_ID}/photo.jpg", body=b"second")
        self.assertEqual(b"second", (self.captures / CAPTURE_ID / "photo.jpg").read_bytes())

    def test_a_path_that_climbs_out_of_the_capture_folder_is_refused(self):
        for path in ["/captures/../escape.json", "/captures/cap-20261006120000-abc123/../../x.json", "/captures/%2e%2e/photo.jpg"]:
            self.assertIn(self.request("PUT", path, body=b"x")[0], (400, 404), path)
        self.assertFalse((self.captures.parent / "escape.json").exists())

    def test_an_id_that_is_not_a_capture_id_is_refused(self):
        self.assertEqual(404, self.request("PUT", "/captures/not-a-capture/photo.jpg", body=b"x")[0])

    def test_only_the_two_capture_files_may_be_written(self):
        self.assertEqual(404, self.request("PUT", f"/captures/{CAPTURE_ID}/shell.sh", body=b"x")[0])

    def test_a_write_from_another_website_is_refused_even_with_the_right_host(self):
        status, _, _ = self.request("PUT", f"/captures/{CAPTURE_ID}/photo.jpg", body=b"x", headers={"Origin": "https://evil.example"})
        self.assertEqual(403, status)
        self.assertFalse((self.captures / CAPTURE_ID).exists())

    def test_a_write_from_the_page_itself_is_accepted(self):
        status, _, _ = self.request("PUT", f"/captures/{CAPTURE_ID}/photo.jpg", body=b"x", headers={"Origin": f"http://localhost:{self.port}"})
        self.assertEqual(201, status)

    def test_a_record_over_the_size_limit_is_refused(self):
        too_big = b"x" * (serve.MAX_RECORD_BYTES + 1)
        self.assertEqual(413, self.request("PUT", f"/captures/{CAPTURE_ID}/capture.json", body=too_big)[0])
        self.assertFalse((self.captures / CAPTURE_ID / "capture.json").exists())

    def test_a_write_with_no_length_is_refused(self):
        connection = http.client.HTTPConnection("127.0.0.1", self.port)
        connection.putrequest("PUT", f"/captures/{CAPTURE_ID}/photo.jpg", skip_host=True)
        connection.putheader("Host", f"localhost:{self.port}")
        connection.endheaders()
        self.assertEqual(411, connection.getresponse().status)


if __name__ == "__main__":
    unittest.main()
