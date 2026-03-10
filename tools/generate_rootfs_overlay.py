from __future__ import annotations

import argparse
import io
import tarfile
from pathlib import Path

import zstandard


AR_MAGIC = b"!<arch>\n"


def iter_ar_members(data: bytes):
    if not data.startswith(AR_MAGIC):
        raise ValueError("not an ar archive")

    offset = len(AR_MAGIC)
    total = len(data)
    while offset + 60 <= total:
        header = data[offset : offset + 60]
        if len(header) < 60:
            break
        offset += 60

        name = header[:16].decode("utf-8", errors="ignore").strip()
        size_text = header[48:58].decode("ascii", errors="ignore").strip()
        end = header[58:60]
        if end != b"`\n":
            raise ValueError(f"invalid ar header terminator for {name!r}")

        size = int(size_text)
        payload = data[offset : offset + size]
        offset += size
        if offset % 2 == 1:
            offset += 1

        cleaned_name = name.rstrip("/")
        yield cleaned_name, payload


def open_data_tar(member_name: str, payload: bytes):
    if member_name.endswith(".tar"):
        return tarfile.open(fileobj=io.BytesIO(payload), mode="r:")
    if member_name.endswith(".tar.gz"):
        return tarfile.open(fileobj=io.BytesIO(payload), mode="r:gz")
    if member_name.endswith(".tar.xz"):
        return tarfile.open(fileobj=io.BytesIO(payload), mode="r:xz")
    if member_name.endswith(".tar.zst"):
        reader = zstandard.ZstdDecompressor().stream_reader(io.BytesIO(payload))
        return tarfile.open(fileobj=reader, mode="r|")
    raise ValueError(f"unsupported deb payload member: {member_name}")


def append_deb_payload(out_tar: tarfile.TarFile, deb_path: Path) -> int:
    member_count = 0
    data = deb_path.read_bytes()
    payload_member = None
    payload_bytes = None
    for member_name, member_payload in iter_ar_members(data):
        if member_name.startswith("data.tar"):
            payload_member = member_name
            payload_bytes = member_payload
            break

    if payload_member is None or payload_bytes is None:
        raise ValueError(f"missing data payload in {deb_path.name}")

    with open_data_tar(payload_member, payload_bytes) as deb_tar:
        for member in deb_tar:
            normalized_name = member.name.lstrip("./")
            if not normalized_name or normalized_name == ".":
                continue
            member.name = normalized_name
            fileobj = deb_tar.extractfile(member) if member.isfile() else None
            out_tar.addfile(member, fileobj=fileobj)
            member_count += 1
    return member_count


def iter_debs_from_bundle(bundle_path: Path):
    with tarfile.open(bundle_path, "r") as bundle:
        for member in bundle:
            if not member.isfile() or not member.name.endswith(".deb"):
                continue
            extracted = bundle.extractfile(member)
            if extracted is None:
                continue
            yield Path(member.name).name, extracted.read()


def build_overlay(bundle_path: Path, qq_path: Path, output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    total_entries = 0
    processed_debs = 0

    with tarfile.open(output_path, "w:xz") as out_tar:
        for deb_name, deb_bytes in iter_debs_from_bundle(bundle_path):
            temp_path = output_path.parent / f".tmp_{deb_name}"
            temp_path.write_bytes(deb_bytes)
            try:
                entries = append_deb_payload(out_tar, temp_path)
            finally:
                temp_path.unlink(missing_ok=True)
            processed_debs += 1
            total_entries += entries
            print(f"[overlay] added {deb_name}: {entries} entries")

        qq_entries = append_deb_payload(out_tar, qq_path)
        processed_debs += 1
        total_entries += qq_entries
        print(f"[overlay] added {qq_path.name}: {qq_entries} entries")

    print(
        f"[overlay] complete: {processed_debs} deb archives, {total_entries} tar entries, output={output_path}",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--qq", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    build_overlay(args.bundle, args.qq, args.output)


if __name__ == "__main__":
    main()
