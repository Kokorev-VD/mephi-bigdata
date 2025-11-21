#!/usr/bin/env python3
import asyncio
import sys
import os
import nats

async def main():
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--nats-url', default=os.getenv("NATS_URL", "nats://nats:4222"))
    parser.add_argument('--subject', default=os.getenv("SUBJECT", "metrics.system.snapshot"))
    parser.add_argument('--batch-size', type=int, default=int(os.getenv("BATCH_SIZE", "1")))
    args = parser.parse_args()

    nc = await nats.connect(args.nats_url)
    js = nc.jetstream()

    count = 0

    async def handler(msg):
        nonlocal count
        print(msg.data.decode(), flush=True)
        await msg.ack()
        count += 1
        if args.batch_size > 0 and count >= args.batch_size:
            await nc.close()

    await js.subscribe(args.subject, cb=handler, durable="nifi-consumer")

    while nc.is_connected and (args.batch_size == 0 or count < args.batch_size):
        await asyncio.sleep(0.1)

    if nc.is_connected:
        await nc.close()


if __name__ == "__main__":
    asyncio.run(main())
