#!/usr/bin/env python3
"""Contributor credits with GitHub avatars for commits between the previous tag and the current ref."""

from __future__ import annotations

import json
import re
import subprocess
import sys
import urllib.request
import urllib.error

BOT_RE = re.compile(
    r"(\[bot\]|github-actions|dependabot|renovate|copilot|codecov|"
    r"semantic-release-bot|renovate-bot)",
    re.I,
)

Contributor = tuple[str, str, str | None, int]  # (name, email, handle, count)


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=True
    ).stdout


def previous_tag(current: str) -> str | None:
    tags = [t for t in git("tag", "--sort=-v:refname").splitlines() if t.strip()]
    for i, tag in enumerate(tags):
        if tag == current and i + 1 < len(tags):
            return tags[i + 1]
    return None


def is_bot(name: str, email: str) -> bool:
    return bool(BOT_RE.search(f"{name} {email}"))


def github_handle_from_email(email: str) -> str | None:
    """Extract GitHub handle from noreply email (e.g. 123+alice@users.noreply.github.com → alice)."""
    if "users.noreply.github.com" not in email.lower():
        return None
    local = email.split("@", 1)[0]
    if "+" in local:
        return local.split("+", 1)[1]
    if local.isdigit():
        return None
    return local


def gh_token() -> str | None:
    """Get GitHub token from gh CLI config."""
    try:
        result = subprocess.run(
            ["gh", "auth", "token"], capture_output=True, text=True, check=True
        )
        return result.stdout.strip()
    except (subprocess.CalledProcessError, FileNotFoundError):
        return None


def search_github_user(email: str, token: str | None) -> str | None:
    """Search GitHub for a user by email, return their login handle."""
    try:
        url = f"https://api.github.com/search/users?q={email}+in:email"
        req = urllib.request.Request(url)
        req.add_header("Accept", "application/vnd.github+json")
        if token:
            req.add_header("Authorization", f"Bearer {token}")
        with urllib.request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode())
            if data.get("items"):
                return data["items"][0].get("login")
    except (urllib.error.URLError, json.JSONDecodeError, KeyError, IndexError):
        pass
    return None


def resolve_handle(name: str, email: str, token: str | None) -> str | None:
    """Try to find GitHub handle: noreply email → API search."""
    handle = github_handle_from_email(email)
    if handle:
        return handle
    if token:
        return search_github_user(email, token)
    return None


def contributors(from_ref: str, to_ref: str, token: str | None) -> list[Contributor]:
    counts: dict[str, tuple[str, str, int]] = {}
    out = git("log", f"{from_ref}..{to_ref}", "--format=%aN|%aE")
    for line in out.splitlines():
        if "|" not in line:
            continue
        name, email = line.split("|", 1)
        if is_bot(name, email):
            continue
        key = email.lower()
        if key in counts:
            prev_name, prev_email, count = counts[key]
            counts[key] = (prev_name, prev_email, count + 1)
        else:
            counts[key] = (name, email, 1)

    result: list[Contributor] = []
    for name, email, count in counts.values():
        handle = resolve_handle(name, email, token)
        result.append((name, email, handle, count))
    result.sort(key=lambda row: (-row[3], (row[2] or row[0]).lower()))
    return result


def format_credits(people: list[Contributor]) -> str:
    if not people:
        return ""
    avatars = []
    for name, _, handle, _ in people:
        if handle:
            avatars.append(f"[![@{handle}](https://github.com/{handle}.png?size=50)](https://github.com/{handle})")
        else:
            safe_name = name.replace(" ", "+")
            avatars.append(f"![{name}](https://ui-avatars.com/api/?name={safe_name}&size=50&background=random)")
    return "\n---\n\n## Contributors\n\n" + " ".join(avatars) + "\n"


def main() -> None:
    to_ref = sys.argv[1] if len(sys.argv) > 1 else "HEAD"
    from_ref = previous_tag(to_ref)
    if not from_ref:
        return
    token = gh_token()
    people = contributors(from_ref, to_ref, token)
    print(format_credits(people))


def self_check() -> None:
    assert is_bot("github-actions[bot]", "github-actions[bot]@users.noreply.github.com")
    assert not is_bot("Alice", "alice@example.com")
    assert github_handle_from_email("123+alice@users.noreply.github.com") == "alice"
    assert github_handle_from_email("alice@users.noreply.github.com") == "alice"
    assert github_handle_from_email("alice@example.com") is None

    people: list[Contributor] = [
        ("Alice", "alice@users.noreply.github.com", "alice", 5),
        ("Bob", "bob@example.com", "bob", 2),
    ]
    body = format_credits(people)
    assert "github.com/alice" in body
    assert "github.com/bob" in body
    assert "![@" in body
    print("ok")


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "--self-check":
        self_check()
    else:
        main()
