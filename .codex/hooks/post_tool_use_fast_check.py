#!/usr/bin/env python3
import json
import subprocess
import sys


def main() -> int:
    event = json.load(sys.stdin)
    cwd = event.get("cwd") or "."
    result = subprocess.run(
        ["make", "fast-check"],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=115,
    )
    output = trim(result.stdout)
    if result.returncode == 0:
        if meaningful(output):
            print(json.dumps({
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": "AI Harness fast-check passed.\n\n" + output,
                }
            }))
        return 0

    feedback = (
        "AI Harness fast-check failed after the file edit. Fix these diagnostics before continuing, "
        "then re-run the edit or `make fast-check`.\n\n" + output
    )
    print(json.dumps({
        "decision": "block",
        "reason": feedback,
        "hookSpecificOutput": {
            "hookEventName": "PostToolUse",
            "additionalContext": feedback,
        },
    }))
    return 0


def meaningful(output: str) -> bool:
    return "verification skipped" not in output or "==" in output


def trim(output: str, limit: int = 6000) -> str:
    output = output.strip()
    if len(output) <= limit:
        return output
    return output[:3000] + "\n\n... output truncated ...\n\n" + output[-3000:]


if __name__ == "__main__":
    raise SystemExit(main())
