# CLAUDE.md

Project-level guidance for Claude Code when working in this repo.

## Task delegation / multi-model orchestration

This project has four worker subagents defined in `.claude/agents/`:
`haiku-worker`, `sonnet-worker`, `opus-worker`, `fable-worker` — same contract,
different model tier. Use them to keep expensive-model tokens for the parts of a
task that actually need them.

**Check for solo mode first.** If the user's request says "solo", "no delegate",
"don't delegate", "do it yourself", "no workers", or the task is a single trivial
action — just do it directly yourself, no breakdown, no worker prompts.

**Otherwise, break the task into subtasks and classify each one:**

| Tier | Use for |
|---|---|
| `haiku-worker` | Mechanical, one-right-answer work: renames, formatting/lint fixes, boilerplate, simple well-specified single-function edits, running a command and reporting output, locating things via search. |
| `sonnet-worker` | Default. Well-scoped feature/function needing some internal design; refactors that don't change public contracts; tests incl. edge cases; bugs with a known root cause; consistent multi-file changes. |
| `opus-worker` / `fable-worker` | Architecture/design decisions; cross-subsystem or non-obvious debugging; security-sensitive code; anything touching public API/backward compatibility; arbitrating conflicting worker outputs. |

Give each worker, in its delegation prompt: the goal, the exact files/directories
in scope (and what's out of scope), and acceptance criteria. Workers are
instructed to stop and report `SCOPE_ESCALATION` rather than expand scope on
their own if the task can't be finished within their boundaries — when that
happens, re-plan just that subtask (widen scope, reassign to a higher tier,
handle it yourself, or ask the user), don't restart everything.

Verify each worker's `DONE` report (skim the diff, run tests) before considering
a subtask closed, and do a final build/test/lint pass before telling the user the
overall task is complete.

Full rationale and the Cowork-mode equivalent live in
`../claude-orchestration-kit/ORCHESTRATOR.md`.

## Project notes

See `README.md` and `ARCHITECTURE.md` in this repo for what blast-arena is and
how it's structured.
