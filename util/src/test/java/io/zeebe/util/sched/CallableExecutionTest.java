/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.util.sched;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import io.zeebe.util.TestUtil;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;

public class CallableExecutionTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule(3);

    class PongActor extends Actor
    {
        ActorFuture<Integer> onPing(int val)
        {
            return actor.call(() ->
            {
                return val + 1;
            });
        }
    }

    static class PingActor extends Actor
    {

        int count = 0;
        final CountDownLatch latch;

        PongActor pongActor;

        Runnable sendPing = this::sendPing;

        PingActor(PongActor pongActor, CountDownLatch latch)
        {
            this.pongActor = pongActor;
            this.latch = latch;
        }

        @Override
        protected void onActorStarted()
        {
            actor.run(sendPing);
        }

        void sendPing()
        {
            final ActorFuture<Integer> future = pongActor.onPing(count);
            actor.runOnCompletion(future, (r, t) ->
            {
                count = r;
                if (count == 1_000_000)
                {
                    latch.countDown();
                }
                else
                {
                    actor.run(sendPing);
                }
            });
        }
    }

    class PingMultipleActor extends Actor
    {

        int count = 0;

        PongActor pongActor;

        Runnable sendPing = this::sendPing;

        CountDownLatch latch;

        PingMultipleActor(PongActor pongActor, CountDownLatch latch)
        {
            this.pongActor = pongActor;
            this.latch = latch;
        }

        @Override
        protected void onActorStarted()
        {
            actor.run(sendPing);
        }

        void sendPing()
        {
            final ActorFuture<Integer> future1 = pongActor.onPing(count);
            final ActorFuture<Integer> future2 = pongActor.onPing(count);
            final ActorFuture<Integer> future3 = pongActor.onPing(count);

            actor.runOnCompletion(Arrays.asList(future1, future2, future3), (t) ->
            {
                count = Math.max(Math.max(future1.join(), future2.join()), future3.join());

                if (count == 100_000)
                {
                    latch.countDown();
                }
                else
                {
                    actor.run(sendPing);
                }
            });
        }
    }

    @Test
    public void testAwaitRunMultipleTimesConcurrent() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(10);
        final PongActor pongActor = new PongActor();

        final PingActor[] actors = new PingActor[(int) latch.getCount()];

        for (int i = 0; i < actors.length; i++)
        {
            actors[i] = new PingActor(pongActor, latch);
        }

        schedulerRule.submitActor(pongActor);
        for (PingActor pingActor : actors)
        {
            schedulerRule.submitActor(pingActor);
        }

        if (!latch.await(5, TimeUnit.MINUTES))
        {
            Assert.fail();
        }

        final ActorScheduler actorScheduler = schedulerRule.get();
        actorScheduler.dumpMetrics(System.out);
    }

    @Test
    public void testAwaitAllRunMultipleTimesConcurrent() throws InterruptedException
    {
        final CountDownLatch latch = new CountDownLatch(10);
        final PongActor pongActor = new PongActor();

        final PingMultipleActor[] actors = new PingMultipleActor[(int) latch.getCount()];

        for (int i = 0; i < actors.length; i++)
        {
            actors[i] = new PingMultipleActor(pongActor, latch);
        }

        schedulerRule.submitActor(pongActor);
        for (PingMultipleActor pingActor : actors)
        {
            schedulerRule.submitActor(pingActor);
        }

        if (!latch.await(5, TimeUnit.MINUTES))
        {
            Assert.fail();
        }

        final ActorScheduler actorScheduler = schedulerRule.get();
        actorScheduler.dumpMetrics(System.out);
    }

    @Test
    public void shouldCompleteFutureExceptionallyWhenSubmittedDuringActorClosedJob() throws InterruptedException, BrokenBarrierException
    {
        // given
        final CyclicBarrier barrier = new CyclicBarrier(2);
        final CloseableActor actor = new CloseableActor()
        {
            @Override
            protected void onActorClosed()
            {
                try
                {
                    barrier.await(); // signal arrival at barrier
                    barrier.await(); // wait for continuation
                }
                catch (InterruptedException | BrokenBarrierException e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        schedulerRule.submitActor(actor);
        actor.close();
        barrier.await(); // wait for actor to reach onActorClosed callback

        final ActorFuture<Void> future = actor.doCall();

        // when
        barrier.await(); // signal actor to continue

        // then
        TestUtil.waitUntil(() -> future.isDone());
        assertThat(future).isDone();
        assertThatThrownBy(() -> future.get())
            .isInstanceOf(ExecutionException.class)
            .hasMessage("Actor is closed");
    }

    class CloseableActor extends Actor
    {
        ActorFuture<Void> doCall()
        {
            return actor.call(() ->
            {
            });
        }

        void close()
        {
            actor.close();
        }
    }
}
