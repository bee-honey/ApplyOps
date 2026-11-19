# ADR-0003: Discover through email alerts, apply through company ATS

- Status: Accepted
- Date: 2026-11-18

## Context
LinkedIn's User Agreement prohibits bots and automated access, including automated applying; accounts that do it risk restriction. Jobright and similar aggregators have their own terms. Most jobs eventually link to a company-hosted applicant tracking system (Greenhouse, Lever, Ashby, Workday, …), which are designed for public candidates to apply directly.

## Decision
- Use LinkedIn/Jobright **only through the alert emails they send to the user's Gmail**, as a discovery signal. No scraping or automation of LinkedIn or Jobright sites.
- Resolve each alert to the **company's ATS URL** and apply there.
- Support ATS platforms in order: Greenhouse → Lever → Ashby (simple, consistent forms; public job-board pages). Workday and others start as `NEEDS_MANUAL`.
- Respect robots/terms, CAPTCHAs and login walls: detect and pause for the user; never bypass.

## Consequences
- ✅ Lower legal/account risk; defensible design to explain in interviews.
- ✅ ATS-specific adapters are deterministic and testable against fixtures.
- ⚠️ "Easy Apply"-only LinkedIn jobs can't be automated → notify the user with a link.
- ⚠️ Some alerts link to aggregator pages; the resolver may fail → mark `NEEDS_MANUAL`.

## Amendment (2026-11-18, M1)
LinkedIn alert links point to `linkedin.com/jobs/view/{id}`, not to the ATS. Opening those pages to find the external apply link would be automated access to LinkedIn. So:
- The link resolver **never requests linkedin.com**. It follows redirects hop by hop and stops before any LinkedIn host.
- LinkedIn jobs are deduplicated by provider id and by a company + title fingerprint.
- Finding the ATS posting moves to M2. There we look up the company's public job board (Greenhouse/Lever/Ashby public APIs) by company name and title, rather than going through LinkedIn.

## Alternatives considered
- **Automate LinkedIn Easy Apply:** highest coverage, unacceptable terms-of-service and account risk.
- **Job board APIs only:** clean, but loses the user's personalized alert stream.
