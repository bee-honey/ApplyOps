# Alert email fixtures

Real LinkedIn and Jobright alert emails, scrubbed of personal data. The parser tests are pinned to these files, so a template change shows up as a failing test.

## Adding or refreshing a fixture

1. Save raw samples locally (they land in the gitignored `data/alert-samples/`):
   ```bash
   make gmail-dump Q='from:jobalerts-noreply@linkedin.com' N=5
   ```
2. Scrub one into this directory. Pass every personal string you can think of with `--redact`:
   ```bash
   python3 tools/scrub_alert_email.py data/alert-samples/<id>.html \
     orchestrator/src/test/resources/fixtures/email/<name>.html \
     --redact "First" --redact "Last" --redact "you@example.com" --note "what this sample covers"
   ```
   The script refuses to write the output if a redacted string, an unknown email address, or a tracking token survives.
3. **Read the visible text** before committing. Automated checks don't catch everything (for example, a profile headline that names your employer).
4. After fixing a parser, run `make discover-resync` to re-read the last 14 days of mail; a normal run only looks at mail received since the last checkpoint.
