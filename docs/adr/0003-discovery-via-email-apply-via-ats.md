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

## Alternatives considered
- **Automate LinkedIn Easy Apply:** highest coverage, unacceptable terms-of-service and account risk.
- **Job board APIs only:** clean, but loses the user's personalized alert stream.
