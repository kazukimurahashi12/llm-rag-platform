#!/usr/bin/env python3
import json
import subprocess
import sys


def main() -> int:
    event = json.load(sys.stdin)
    if event.get("stop_hook_active"):
        print(json.dumps({"continue": True}))
        return 0

    cwd = event.get("cwd") or "."
    result = subprocess.run(
        ["make", "verify"],
        cwd=cwd,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=590,
    )
    output = trim(result.stdout)
    if result.returncode == 0:
        print(json.dumps({
            "continue": True,
            "systemMessage": "AI Harness verify passed.",
        }))
        return 0

    reason = (
        "AI Harness verify failed. Continue the task, fix the failing checks below, "
        "and run `make verify` again before final response.\n\n" + output
    )
    print(json.dumps({
        "decision": "block",
        "reason": reason,
    }))
    return 0


def trim(output: str, limit: int = 8000) -> str:
    output = output.strip()
    if len(output) <= limit:
        return output
    return output[:4000] + "\n\n... output truncated ...\n\n" + output[-4000:]


if __name__ == "__main__":
    raise SystemExit(main())
