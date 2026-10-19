# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.
This is a **single-context** repo: one glossary and one decision log, both repo-wide.

## Before exploring, read these

- **`GLOSSARY.md`** at the repo root.
- **`docs/decisions.md`** — the decision log. This repo's equivalent of `docs/adr/`: read the entries
  that touch the area you're about to work in. There is no `docs/adr/` directory, and none should be
  created; a skill that would write an ADR appends an entry here instead.

If `GLOSSARY.md` doesn't exist, **proceed silently**. Don't flag its absence; don't suggest creating it
upfront. The `/domain-modeling` skill (reached via `/grill-with-docs` and
`/improve-codebase-architecture`) creates it lazily when terms actually get resolved.

## File structure

```
/
├── GLOSSARY.md          ← created lazily
├── AGENTS.md
├── docs/
│   └── decisions.md     ← the decision log (stands in for docs/adr/)
└── src/
```

## Recording a decision

Append to `docs/decisions.md` in its existing shape — a `- ✅ **One-sentence decision.**` bullet followed
by the reasoning — rather than numbered ADR files. Respect the rule in that file's banner: add an entry
only when the reasoning **isn't discoverable from a single commit** (a constraint that shapes future
work, something deliberately rejected, or a decision assembled across several commits). A shipped
feature on its own goes in the commit message and `CHANGELOG.md`, not here.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `GLOSSARY.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag decision-log conflicts

If your output contradicts an entry in `docs/decisions.md`, surface it explicitly rather than silently overriding:

> _Contradicts the decision log's signing entry, but worth reopening because…_
