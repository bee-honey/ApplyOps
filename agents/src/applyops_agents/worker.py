"""Entry point: connect to Temporal and serve activities on ``agents-tq``."""

import asyncio
import logging
import os

from temporalio.client import Client
from temporalio.contrib.pydantic import pydantic_data_converter
from temporalio.worker import Worker

from applyops_agents import task_queues
from applyops_agents.activities import echo

log = logging.getLogger("applyops.agents")


async def connect(address: str, namespace: str, attempts: int = 30) -> Client:
    """Connect with retries so the worker tolerates Temporal starting after it."""
    for attempt in range(1, attempts + 1):
        try:
            return await Client.connect(
                address, namespace=namespace, data_converter=pydantic_data_converter
            )
        except Exception as exc:  # noqa: BLE001 - any connection error is retryable here
            log.warning("Temporal not ready (%s/%s): %s", attempt, attempts, exc)
            await asyncio.sleep(2)
    raise RuntimeError(f"Could not connect to Temporal at {address}")


async def main() -> None:
    address = os.environ.get("TEMPORAL_ADDRESS", "localhost:7233")
    namespace = os.environ.get("TEMPORAL_NAMESPACE", "default")
    client = await connect(address, namespace)
    worker = Worker(client, task_queue=task_queues.AGENTS, activities=[echo])
    log.info("agents worker polling %s on %s", task_queues.AGENTS, address)
    await worker.run()


def run() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")
    asyncio.run(main())


if __name__ == "__main__":
    run()
