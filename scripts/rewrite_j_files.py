#!/usr/bin/env python3
"""Rewrite assembly .j files: replace Windows obfuscated names with target platform equivalents.

Usage: python3 rewrite_j_files.py [--platform macos]

Two-phase replacement:
1. Context-aware: Field/Method/InterfaceMethod refs where member names changed
2. Global: class path string replacement (longest-first to avoid partial matches)
"""
import argparse, re, glob, os, sys

parser = argparse.ArgumentParser()
parser.add_argument("--platform", default="macos", help="Target platform (default: macos)")
args = parser.parse_args()

# Platform-specific line replacements: calls/fields that don't exist on the target.
# Each entry is (match_substring, replacement_instruction).
PLATFORM_FIXUPS = {
    "linux": [
        # D/return.public() — method is private on Linux, stock game doesn't call it
        ("invokestatic Method com/fs/starfarer/D/return 'public' ()V", "nop"),
    ],
}.get(args.platform, [])

# Load class mapping
class_map = {}
with open(f"assembly/mappings/asm_classes_{args.platform}.tsv") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) == 2:
            class_map[parts[0]] = parts[1]

# Load member mapping: (win_class, win_member) -> target_member
member_map = {}  # (cls, member) -> target_member (for Phase 1 instruction replacement)
member_map_by_kind = {}  # (cls, member, kind) -> target_member (for Phase 0 declarations)
with open(f"assembly/mappings/asm_members_{args.platform}.tsv") as f:
    for line in f:
        parts = line.strip().split("\t")
        if len(parts) >= 3:
            kind = parts[3] if len(parts) == 4 else "both"
            win_member = parts[1].strip("'")
            target_member = parts[2].strip("'")
            member_map[(parts[0], win_member)] = target_member
            member_map_by_kind[(parts[0], win_member, kind)] = target_member

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
    needs_quote = (
        any(ord(c) > 127 for c in name) or
        name in JAVA_KEYWORDS or
        (name and name[0].isdigit()) or
        '.' in name
    )
    return f"'{name}'" if needs_quote else name

# Build member replacement regex patterns (Phase 1)
member_patterns = []
for (win_cls, win_member, kind), target_member in member_map_by_kind.items():
    esc_cls = re.escape(win_cls)
    esc_member = re.escape(win_member)
    quoted_target = krak_quote(target_member)
    has_field = (win_cls, win_member, "field") in member_map_by_kind
    has_method = (win_cls, win_member, "method") in member_map_by_kind
    field_target = member_map_by_kind.get((win_cls, win_member, "field"))
    method_target = member_map_by_kind.get((win_cls, win_member, "method"))
    if has_field and has_method and field_target == method_target:
        instr = r'(?:Field|Method|InterfaceMethod)'
    elif kind == "field":
        instr = r'Field'
    else:
        instr = r'(?:Method|InterfaceMethod)'
    pattern = re.compile(
        r'(' + instr + r'\s+' + esc_cls + r"\s+)'?" + esc_member + r"'?" + r'(\s+)'
    )
    replacement = r'\g<1>' + quoted_target + r'\g<2>'
    member_patterns.append((pattern, replacement))

# Build per-class member lookup by kind
class_member_map = {}
for (win_cls, win_member, kind), target_member in member_map_by_kind.items():
    class_member_map.setdefault(win_cls, {"field": {}, "method": {}})
    class_member_map[win_cls][kind][win_member] = target_member

def rewrite_line(line, file_class):
    """Apply all replacements to a single line."""
    # Platform-specific fixups
    for match, replacement in PLATFORM_FIXUPS:
        if match in line:
            return re.sub(r'(L\d+:\s+)\S.*', r'\g<1>' + replacement + ' ', line)

    # Phase 0: rewrite .field/.method declarations for the file's own class
    if file_class and file_class in class_member_map:
        kinds = class_member_map[file_class]
        m = re.match(r'(\.field\s+.+\s)(\S+)(\s+[LIJFDBCSZ\[]\S*.*)', line)
        if m:
            name = m.group(2).strip("'")
            if name in kinds["field"]:
                line = m.group(1) + krak_quote(kinds["field"][name]) + m.group(3)
                if not line.endswith('\n'):
                    line += '\n'
        m = re.match(r'(\.method\s+.+\s)(\S+)(\s+:\s+.*)', line)
        if m:
            name = m.group(2).strip("'")
            if name in kinds["method"]:
                line = m.group(1) + krak_quote(kinds["method"][name]) + m.group(3)
                if not line.endswith('\n'):
                    line += '\n'

    # Phase 1: member-aware replacements
    for pattern, replacement in member_patterns:
        line = pattern.sub(replacement, line)
    
    # Phase 2: simultaneous class path replacement
    if class_regex.search(line):
        line = class_regex.sub(lambda m: class_map[m.group(0)], line)
    
    return line

def resolve_const_aliases(lines):
    """Resolve [cNNN] constant pool aliases to full class paths."""
    utf8 = {}
    class_defs = {}
    for line in lines:
        m = re.match(r'\.const \[u(\d+)\] = Utf8 (.+)', line.strip())
        if m:
            utf8[m.group(1)] = m.group(2).strip()
        m = re.match(r'\.const \[c(\d+)\] = Class \[u(\d+)\]', line.strip())
        if m:
            class_defs[m.group(1)] = m.group(2)
    return {f'[c{c_id}]': utf8[u_id] for c_id, u_id in class_defs.items() if u_id in utf8}

# Process all .j files
j_files = sorted(glob.glob("assembly/overrides/**/*.j", recursive=True))
total_changes = 0

for jf in j_files:
    with open(jf) as f:
        original = f.readlines()
    
    file_class = None
    for line in original:
        m = re.match(r'\.class\s+.*\s+(\S+)\s*$', line.strip())
        if m:
            file_class = m.group(1)
            break
    
    # Expand [cNNN] aliases in-memory so Phase 1 member patterns can match.
    const_aliases = resolve_const_aliases(original)
    
    # Platform-specific block skips: replace entire instruction sequences
    # with goto to skip past them (stack-clean, preserves jump targets).
    if args.platform == "linux":
        text = "".join(original)
        # getPausePlayWidget chain — method removed on Linux.
        # Replace aload_0 with goto to skip the 5-instruction block.
        def skip_widget_block(m):
            lines = m.group(0).split('\n')
            # Find the first label after the flash() call
            for i in range(5, min(8, len(lines))):
                lm = re.match(r'(L\d+):', lines[i])
                if lm:
                    lines[0] = re.sub(r'(L\d+:\s+).*', r'\1goto ' + lm.group(1) + ' ', lines[0])
                    for j in range(1, 5):
                        lines[j] = re.sub(r'(L\d+:\s+).*', r'\1nop ', lines[j])
                    break
            return '\n'.join(lines)
        text = re.sub(
            r'L\d+:\s+aload_0\s*\nL\d+:\s+getfield[^\n]*warroom[^\n]*\nL\d+:\s+invokevirtual[^\n]*getPausePlayWidget[^\n]*\nL\d+:\s+invokevirtual[^\n]*get(?:Pause|Play)Button[^\n]*\nL\d+:\s+invokevirtual[^\n]*flash[^\n]*\n(?:[^\n]*\n){1,3}',
            skip_widget_block, text)
        original = text.splitlines(True)
    
    modified = []
    file_changes = 0
    for line in original:
        expanded = line
        if const_aliases and not line.strip().startswith('.const'):
            for ref, cls in const_aliases.items():
                if ref in expanded:
                    expanded = expanded.replace(ref, cls)
        new_line = rewrite_line(expanded, file_class)
        if new_line != line:
            file_changes += 1
        modified.append(new_line)
    
    if file_changes > 0:
        with open(jf, "w") as f:
            f.writelines(modified)
        total_changes += file_changes
        print(f"  {os.path.basename(jf)}: {file_changes} lines changed")

print(f"\nTotal: {total_changes} lines changed across {len(j_files)} files")
