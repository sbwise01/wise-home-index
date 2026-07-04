#!/usr/bin/env python3
"""Print the Maven project <version> from a pom.xml.

Usage: pom_version.py [path-to-pom]   (defaults to ./pom.xml)

Reads only the top-level <project><version>, ignoring dependency/plugin
versions, so it is safe to use as the single source of truth for the release
version.
"""
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1] if len(sys.argv) > 1 else "pom.xml"
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(path).getroot()
version = root.find("m:version", ns)
text = (version.text or "").strip() if version is not None else ""
if not text:
    sys.exit(f"no top-level <version> found in {path}")
print(text)
