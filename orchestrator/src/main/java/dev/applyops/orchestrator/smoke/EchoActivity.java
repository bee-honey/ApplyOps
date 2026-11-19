package dev.applyops.orchestrator.smoke;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Activity contract implemented by the Python agents worker
 * ({@code agents/src/applyops_agents/activities.py}). The activity type name "echo"
 * must match on both sides; payloads follow schemas/echo/*.v1.json.
 */
@ActivityInterface
public interface EchoActivity {
    @ActivityMethod(name = "echo")
    EchoResult echo(EchoRequest request);
}
