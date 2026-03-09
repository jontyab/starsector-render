#!/usr/bin/env python3
"""Rewrite assembly .j files: replace Windows obfuscated names with macOS equivalents.

Two-phase replacement:
1. Context-aware: Field/Method/InterfaceMethod refs where member names changed
2. Global: class path string replacement (longest-first to avoid partial matches)
"""
import re, glob, os, sys

# Load class mapping
class_map = {}
with open("mappings/class_map_raw.tsv") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) == 2:
            class_map[parts[0]] = parts[1]

# Load member mapping: (win_class, win_member) -> mac_member
member_map = {}  # (cls, member) -> mac_member (for Phase 1 instruction replacement)
member_map_by_kind = {}  # (cls, member, kind) -> mac_member (for Phase 0 declarations)
with open("mappings/member_map.tsv") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) >= 3:
            kind = parts[3] if len(parts) == 4 else "both"
            member_map[(parts[0], parts[1])] = parts[2]
            member_map_by_kind[(parts[0], parts[1], kind)] = parts[2]

# Build simultaneous class replacement regex (longest-first to avoid partial matches)
sorted_classes = sorted(class_map.items(), key=lambda x: -len(x[0]))
class_regex = re.compile(
    '(' + '|'.join(re.escape(win) for win, _ in sorted_classes) + r')(?![a-zA-Z0-9_/])'
)

# Krakatau quoting rules for member names
JAVA_KEYWORDS = {
    'super', 'new', 'return', 'class', 'int', 'void', 'do', 'if', 'for',
    'null', 'Object', 'String', 'interface', 'byte', 'float', 'while',
    'public', 'private', 'protected', 'static', 'final', 'abstract',
    'synchronized', 'volatile', 'transient', 'native', 'strictfp',
    'throws', 'extends', 'implements', 'import', 'package', 'boolean',
    'char', 'short', 'long', 'double', 'try', 'catch', 'finally',
    'throw', 'this', 'instanceof', 'switch', 'case', 'default', 'break',
    'continue', 'goto', 'const', 'enum', 'assert',
}

def krak_quote(name):
    """Add Krakatau single-quote escaping if needed."""
    if name.startswith("'") and name.endswith("'"):
        return name  # already quoted
    # Needs quoting if: contains non-ASCII, is a Java keyword, or starts with digit
    needs_quote = (
        any(ord(c) > 127 for c in name) or
        name in JAVA_KEYWORDS or
        (name and name[0].isdigit())
    )
    return f"'{name}'" if needs_quote else name

# Build member replacement regex patterns (Phase 1)
# Kind-aware: field-only changes match only Field, method-only match only Method/InterfaceMethod
# Only replaces member name — class path is handled by Phase 2
member_patterns = []
for (win_cls, win_member, kind), mac_member in member_map_by_kind.items():
    esc_cls = re.escape(win_cls)
    esc_member = re.escape(win_member)
    quoted_mac = krak_quote(mac_member)
    has_field = (win_cls, win_member, "field") in member_map_by_kind
    has_method = (win_cls, win_member, "method") in member_map_by_kind
    if has_field and has_method:
        instr = r'(?:Field|Method|InterfaceMethod)'
    elif kind == "field":
        instr = r'Field'
    else:
        instr = r'(?:Method|InterfaceMethod)'
    pattern = re.compile(
        r'(' + instr + r'\s+' + esc_cls + r'\s+)' + esc_member + r'(\s+)'
    )
    replacement = r'\g<1>' + quoted_mac + r'\g<2>'
    member_patterns.append((pattern, replacement))

# Build per-class member lookup by kind: win_class -> {"field": {m->m}, "method": {m->m}}
class_member_map = {}
for (win_cls, win_member, kind), mac_member in member_map_by_kind.items():
    class_member_map.setdefault(win_cls, {"field": {}, "method": {}})
    class_member_map[win_cls][kind][win_member] = mac_member

def rewrite_line(line, file_class):
    """Apply all replacements to a single line."""
    # Phase 0: rewrite .field/.method declarations for the file's own class
    if file_class and file_class in class_member_map:
        kinds = class_member_map[file_class]
        # .field [modifiers] <name> <desc>
        m = re.match(r'(\.field\s+.+\s)(\S+)(\s+[LIJFDBCSZ\[]\S*.*)', line)
        if m:
            name = m.group(2)
            if name in kinds["field"]:
                line = m.group(1) + krak_quote(kinds["field"][name]) + m.group(3)
                if not line.endswith('\n'):
                    line += '\n'
        # .method [modifiers] <name> : <desc>
        m = re.match(r'(\.method\s+.+\s)(\S+)(\s+:\s+.*)', line)
        if m:
            name = m.group(2)
            if name in kinds["method"]:
                line = m.group(1) + krak_quote(kinds["method"][name]) + m.group(3)
                if not line.endswith('\n'):
                    line += '\n'

    # Phase 1: member-aware replacements — only replace member names (class already handled by Phase 2)
    for pattern, replacement in member_patterns:
        line = pattern.sub(replacement, line)
    
    # Phase 2: simultaneous class path replacement (avoids swap cycles)
    if class_regex.search(line):
        line = class_regex.sub(lambda m: class_map[m.group(0)], line)
    
    return line

# Process all .j files
j_files = sorted(glob.glob("assembly/overrides/**/*.j", recursive=True))
total_changes = 0

for jf in j_files:
    with open(jf) as f:
        original = f.readlines()
    
    # Detect which class this .j file defines
    file_class = None
    for line in original:
        m = re.match(r'\.class\s+.*\s+(\S+)\s*$', line.strip())
        if m:
            file_class = m.group(1)
            break
    
    modified = []
    file_changes = 0
    for line in original:
        new_line = rewrite_line(line, file_class)
        if new_line != line:
            file_changes += 1
        modified.append(new_line)
    
    if file_changes > 0:
        with open(jf, "w") as f:
            f.writelines(modified)
        total_changes += file_changes
        print(f"  {os.path.basename(jf)}: {file_changes} lines changed")

print(f"\nTotal: {total_changes} lines changed across {len(j_files)} files")
