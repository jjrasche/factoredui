import hashlib
import io
import shutil
import subprocess
import sys
import tarfile
from pathlib import Path

MODULE = Path(__file__).resolve().parent.parent
TARGET = MODULE / "reference"
SOURCE_ROOT = "design/world-engine"


def archive_of(repository, commit):
    full = subprocess.run(["git", "-C", repository, "rev-parse", "--verify", commit + "^{commit}"], capture_output=True, text=True, check=True).stdout.strip()
    data = subprocess.run(["git", "-C", repository, "archive", "--format=tar", full, SOURCE_ROOT], capture_output=True, check=True).stdout
    return full, tarfile.open(fileobj=io.BytesIO(data))


def extract(archive):
    if TARGET.exists():
        shutil.rmtree(TARGET)
    for member in archive.getmembers():
        if not member.isfile():
            continue
        relative = Path(member.name).relative_to(SOURCE_ROOT)
        destination = TARGET / relative
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(archive.extractfile(member).read())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def write_pin(commit):
    files = sorted(path for path in TARGET.rglob("*") if path.is_file() and path.name not in ("PINNED_COMMIT", "CASE_COUNT", "MANIFEST.sha256"))
    cases = [path for path in (TARGET / "conformance" / "cases").glob("*.json")]
    (TARGET / "PINNED_COMMIT").write_text(commit + "\n", newline="\n")
    (TARGET / "CASE_COUNT").write_text(f"{len(cases)}\n", newline="\n")
    lines = [f"{digest(path)}  {path.relative_to(TARGET).as_posix()}" for path in files]
    (TARGET / "MANIFEST.sha256").write_text("\n".join(lines) + "\n", newline="\n")
    return len(files), len(cases)


if __name__ == "__main__":
    repository, commit = sys.argv[1], sys.argv[2]
    full, archive = archive_of(repository, commit)
    extract(archive)
    count, cases = write_pin(full)
    print(f"vendored {count} files, {cases} cases, commit {full}")
