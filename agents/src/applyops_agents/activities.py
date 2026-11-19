"""Activities executed by the agents worker."""

import socket

from temporalio import activity

from applyops_agents.models import EchoRequest, EchoResult


@activity.defn(name="echo")
async def echo(request: EchoRequest) -> EchoResult:
    """M0 smoke activity called by the Java ``HelloWorkflow``."""
    activity.logger.info("echo called: %s", request.message)
    return EchoResult(message=request.message, worker=socket.gethostname(), language="python")
