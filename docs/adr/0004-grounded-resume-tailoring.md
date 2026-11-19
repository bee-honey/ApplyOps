# ADR-0004: Grounded résumé tailoring with provenance validation

- Status: Accepted
- Date: 2026-11-18

## Context
The biggest product and ethical risk is an LLM inventing skills, metrics or roles on a résumé. A made-up claim can cost the candidate an offer and misrepresents them to employers. Prompting "don't invent" alone isn't reliable.

## Decision
- A user-maintained **verified profile** (`profile.yaml`) with stable IDs is the only factual source.
- Tailoring is a pipeline: **select by ID → rephrase per item → validate → render**.
- Validation is layered: (1) every item references a valid profile ID; (2) deterministic checks that numbers, organizations, dates and technologies in the rewrite exist in the source; (3) an LLM-judge entailment check. Items that fail go back to the original source text.
- Provenance (`output item → profile IDs`) is stored with every `document_version` and shown on request in Telegram ("View diff").
- An eval suite with a hard pass condition of **0 unsupported claims** runs on every change to the tailoring code.

## Consequences
- ✅ Truthfulness is designed in, auditable and measurable.
- ✅ Strong interview story on LLM output guarantees.
- ⚠️ Output is less "creative"; quality depends on how rich the profile is.
- ⚠️ The user has to maintain the profile (one-time setup cost, small updates).

## Alternatives considered
- **Free-form generation + human review only:** relies on the user spotting subtle fabrications; doesn't scale.
- **Static résumé variants:** truthful but not tailored.
