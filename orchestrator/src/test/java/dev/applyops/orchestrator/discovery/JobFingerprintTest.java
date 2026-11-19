package dev.applyops.orchestrator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class JobFingerprintTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Acme Corp      | Senior Backend Engineer          | ACME             | Sr. Backend Engineer",
        "Globex, Inc.   | Staff Engineer (Remote)          | Globex           | Staff Engineer",
        "Johnson & Co   | Software Engineer - Remote       | Johnson and Co.  | software engineer",
        "Initech LLC    | Engineer II                      | initech          | Engineer  II",
    })
    void sameJobAcrossProviderFormatting(String c1, String t1, String c2, String t2) {
        assertThat(JobFingerprint.of(c1, t1)).isEqualTo(JobFingerprint.of(c2, t2));
    }

    @Test
    void differentTitlesOrCompaniesDiffer() {
        assertThat(JobFingerprint.of("Acme", "Senior Engineer")).isNotEqualTo(JobFingerprint.of("Acme", "Staff Engineer"));
        assertThat(JobFingerprint.of("Acme", "Engineer")).isNotEqualTo(JobFingerprint.of("Globex", "Engineer"));
        assertThat(JobFingerprint.of("Acme", "C++ Engineer")).isNotEqualTo(JobFingerprint.of("Acme", "C Engineer"));
    }

    @Test
    void normalization() {
        assertThat(JobFingerprint.normalizeCompany("Stripe, Inc.")).isEqualTo("stripe");
        assertThat(JobFingerprint.normalizeTitle("Sr. Software Engineer (Backend)")).isEqualTo("senior software engineer");
    }
}
