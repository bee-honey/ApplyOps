package dev.applyops.orchestrator.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AtsUrlTest {

    @ParameterizedTest
    @CsvSource({
        "https://boards.greenhouse.io/acme/jobs/4012345,greenhouse,greenhouse:acme:4012345",
        "https://job-boards.greenhouse.io/Acme/jobs/4012345?gh_src=abc,greenhouse,greenhouse:acme:4012345",
        "https://boards.greenhouse.io/embed/job_app?for=acme&token=4012345,greenhouse,greenhouse:acme:4012345",
        "https://acme.com/careers/job?gh_jid=4012345,greenhouse,greenhouse:acme.com:4012345",
        "https://jobs.lever.co/acme/0b5e7c1a-1234-4abc-9def-0123456789ab/apply,lever,lever:acme:0b5e7c1a-1234-4abc-9def-0123456789ab",
        "https://jobs.ashbyhq.com/acme/0b5e7c1a-1234-4abc-9def-0123456789ab,ashby,ashby:acme:0b5e7c1a-1234-4abc-9def-0123456789ab",
        "https://acme.wd5.myworkdayjobs.com/en-US/External/job/Remote/Engineer_R12345,workday,workday:acme:engineer_r12345",
    })
    void recognizesAtsPostings(String url, String type, String key) {
        var ats = AtsUrl.parse(url).orElseThrow();
        assertThat(ats.type()).isEqualTo(type);
        assertThat(ats.key()).isEqualTo(key);
        assertThat(ats.url()).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.linkedin.com/jobs/view/4012345678/",
        "https://jobright.ai/jobs/info/66f1a2b3c4d5e6f7a8b9c0d1",
        "https://boards.greenhouse.io/acme",
        "not a url",
        "",
    })
    void ignoresEverythingElse(String url) {
        assertThat(AtsUrl.parse(url)).isEmpty();
    }
}
