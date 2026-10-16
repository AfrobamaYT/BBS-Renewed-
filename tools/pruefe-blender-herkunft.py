#!/usr/bin/env python3
"""Validate provenance records for code ported directly from Blender."""

from __future__ import annotations

import argparse
import datetime
import glob
import json
import pathlib
import re
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
REGISTER = ROOT / "third_party" / "blender_ports.json"
DIRECT_PORT_MARKERS = (
    "Uebersetzt aus Blenders",
    "Übersetzt aus Blenders",
    "translated from Blender",
    "Direct-Blender-Port:",
    "Zeile-fuer-Zeile aus IK_",
    "Zeile-für-Zeile aus IK_",
)
SOURCE_SUFFIXES = {".c", ".cc", ".cpp", ".h", ".hpp", ".java", ".py", ".rs"}
COMMIT_RE = re.compile(r"^[0-9a-f]{40}$")
SPDX_RE = re.compile(r"SPDX-License-Identifier:\s*([A-Za-z0-9.+-]+)")


def load_register() -> dict:
    try:
        return json.loads(REGISTER.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        raise RuntimeError(f"Register unlesbar: {REGISTER}: {error}") from error


def expand_globs(patterns: list[str]) -> set[pathlib.Path]:
    files: set[pathlib.Path] = set()

    for pattern in patterns:
        for match in glob.glob(str(ROOT / pattern), recursive=True):
            path = pathlib.Path(match)

            if path.is_file():
                files.add(path.resolve())

    return files


def scan_direct_ports(scan_roots: list[str]) -> set[pathlib.Path]:
    found: set[pathlib.Path] = set()

    for root_name in scan_roots:
        root = ROOT / root_name

        if not root.exists():
            continue

        for path in root.rglob("*"):
            if path.resolve() == pathlib.Path(__file__).resolve():
                continue

            if not path.is_file() or path.suffix.lower() not in SOURCE_SUFFIXES:
                continue

            try:
                text = path.read_text(encoding="utf-8")
            except UnicodeDecodeError:
                continue

            if any(marker in text for marker in DIRECT_PORT_MARKERS):
                found.add(path.resolve())

    return found


def valid_date(value: object) -> bool:
    if not isinstance(value, str):
        return False

    try:
        datetime.date.fromisoformat(value)
        return True
    except ValueError:
        return False


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--release",
        action="store_true",
        help="Treat unresolved legacy provenance as a release-blocking error.",
    )
    arguments = parser.parse_args()

    try:
        register = load_register()
    except RuntimeError as error:
        print(f"FEHLER: {error}")
        return 1

    errors: list[str] = []
    warnings: list[str] = []
    covered: set[pathlib.Path] = set()
    ids: set[str] = set()

    if register.get("schema") != 1:
        errors.append("Unbekannte oder fehlende Register-Schema-Version.")

    ports = register.get("ports")

    if not isinstance(ports, list):
        errors.append("ports muss eine Liste sein.")
        ports = []

    for entry in ports:
        port_id = entry.get("id")
        status = entry.get("status")
        kind = entry.get("kind")

        if not isinstance(port_id, str) or not port_id:
            errors.append("Ein Port hat keine gueltige id.")
            continue

        if port_id in ids:
            errors.append(f"{port_id}: id ist doppelt.")

        ids.add(port_id)

        if kind != "direct-port":
            errors.append(f"{port_id}: kind muss direct-port sein.")

        if status not in {"planned", "verified", "legacy-unresolved"}:
            errors.append(f"{port_id}: ungueltiger status {status!r}.")

        patterns = entry.get("local_globs")

        if not isinstance(patterns, list) or not patterns:
            errors.append(f"{port_id}: local_globs fehlt.")
            files: set[pathlib.Path] = set()
        else:
            files = expand_globs(patterns)

        if status != "planned" and not files:
            errors.append(f"{port_id}: local_globs findet keine Datei.")

        overlap = covered.intersection(files)

        if overlap:
            names = ", ".join(str(path.relative_to(ROOT)) for path in sorted(overlap))
            errors.append(f"{port_id}: Dateien bereits durch anderen Port erfasst: {names}")

        covered.update(files)

        license_id = entry.get("license")

        if not isinstance(license_id, str) or not license_id:
            errors.append(f"{port_id}: license fehlt.")

        for path in sorted(files):
            text = path.read_text(encoding="utf-8")
            licenses = SPDX_RE.findall(text)

            if license_id not in licenses:
                relative = path.relative_to(ROOT)
                errors.append(
                    f"{port_id}: {relative} traegt nicht SPDX-License-Identifier: {license_id}"
                )

        if not isinstance(entry.get("upstream_paths"), list) or not entry["upstream_paths"]:
            errors.append(f"{port_id}: upstream_paths fehlt.")

        if not isinstance(entry.get("copyright"), list) or not entry["copyright"]:
            errors.append(f"{port_id}: copyright fehlt.")

        if not valid_date(entry.get("modified")):
            errors.append(f"{port_id}: modified muss YYYY-MM-DD sein.")

        if not isinstance(entry.get("parity_evidence"), str) or not entry["parity_evidence"]:
            errors.append(f"{port_id}: parity_evidence fehlt.")

        commit = entry.get("upstream_commit")

        if status == "verified" and (not isinstance(commit, str) or not COMMIT_RE.fullmatch(commit)):
            errors.append(f"{port_id}: verified braucht einen exakten 40-stelligen Commit.")

        if status == "verified":
            for path in sorted(files):
                text = path.read_text(encoding="utf-8")

                if "Direct-Blender-Port:" not in text:
                    relative = path.relative_to(ROOT)
                    errors.append(f"{port_id}: {relative} hat keinen Direct-Blender-Port-Vermerk.")

        if status in {"planned", "legacy-unresolved"}:
            message = f"{port_id}: status {status}; nicht releasefaehig."

            if arguments.release:
                errors.append(message)
            else:
                warnings.append(message)

    detected = scan_direct_ports(register.get("scan_roots", []))

    for path in sorted(detected - covered):
        errors.append(f"Nicht registrierter Blender-Direktport: {path.relative_to(ROOT)}")

    for warning in warnings:
        print(f"WARNUNG: {warning}")

    for error in errors:
        print(f"FEHLER: {error}")

    print(
        f"Blender-Herkunft: {len(ports)} Port(s), "
        f"{len(covered)} erfasste Datei(en), {len(errors)} Fehler."
    )

    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
