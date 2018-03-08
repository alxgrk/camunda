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

import io.zeebe.util.Loggers;
import io.zeebe.util.TestUtil;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.fail;

public class ActorLifecycleMethodsTest
{
    @Rule
    public final ActorSchedulerRule schedulerRule = new ActorSchedulerRule();

    @Test
    public void shouldReturnStartingFutureOnSubmitActor()
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);
        final CompletableActorFuture<Void> watingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                this.actor.runOnCompletion(watingFuture, (v, t) ->
                {
                    latch.countDown();
                });
            }
        };

        // when
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);

        // then
        assertThat(startingFuture).isNotDone();
        assertThat(latch.getCount()).isEqualTo(1);
    }

    @Test
    public void shouldCallActorStartedAfterStartingFutureCompletes() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final CompletableActorFuture<Void> watingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                this.actor.runOnCompletion(watingFuture, (v, t) ->
                {
                    latch.countDown();
                });
            }

            @Override
            protected void onActorStarted()
            {
                latch.countDown();
            }
        };

        // when
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        watingFuture.complete(null);

        // then
        if (!latch.await(5, TimeUnit.MINUTES))
        {
            fail("onActorStarted() was never called");
        }
        assertThat(startingFuture).isDone();
    }


    @Test
    public void shouldCallOnActorCloseRequested() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorCloseRequested()
            {
                latch.countDown();
            }

            @Override
            protected void onActorStarted()
            {
                this.actor.runOnCompletion(new CompletableActorFuture<>(), (r, t) ->
                {
                    // ensure that we remain in STARTED/CLOSE_REQUESTED
                });
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.get(5, TimeUnit.SECONDS);

        // when
        actor.actor.close();

        // then
        if (!latch.await(5, TimeUnit.MINUTES))
        {
            fail("onActorCloseRequested() was never called");
        }
        assertThat(startingFuture).isDone();
    }

    @Test
    public void shouldCallOnActorStarting() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);

        // when
        schedulerRule.submitActor(new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                latch.countDown();
            }
        });

        // then
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorStarting() never called");
        }
    }

    @Test
    public void shouldNotCallConsumeOnStarting()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                actor.consume(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);

        // expect
        assertThatThrownBy(() ->
            startingFuture.join())
            .hasMessageContaining("STARTING")
            .hasMessageContaining("consume");
    }

    @Test
    public void shouldNotCallPollBlockingOnStarting()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                actor.pollBlocking(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);

        // expect
        assertThatThrownBy(() ->
            startingFuture.join())
            .hasMessageContaining("STARTING")
            .hasMessageContaining("pollBlocking");
    }

    @Test
    public void shouldNotCallOnConditionOnStarting()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                actor.onCondition(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);

        // expect
        assertThatThrownBy(() ->
            startingFuture.join())
            .hasMessageContaining("STARTING")
            .hasMessageContaining("onCondition");
    }

    @Test
    public void shouldNotCallRunDelayedOnStarting()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                actor.runDelayed(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);

        // expect
        assertThatThrownBy(() ->
            startingFuture.join())
            .hasMessageContaining("STARTING")
            .hasMessageContaining("runDelayed");
    }


    @Test
    public void shouldNotCallRunAtFixedRateOnStarting()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                actor.runAtFixedRate(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);

        // expect
        assertThatThrownBy(() ->
            startingFuture.join())
            .hasMessageContaining("STARTING")
            .hasMessageContaining("runAtFixedRate");
    }

    @Test
    public void shouldCallCloseOnStarting() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final AtomicInteger called = new AtomicInteger(0);

        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarting()
            {
                Loggers.ACTOR_LOGGER.debug("Starting");
                actor.close();
                actor.submit(() -> {
                    Loggers.ACTOR_LOGGER.debug("Submit job");
                    called.incrementAndGet();
                });
            }

            @Override
            protected void onActorStarted()
            {
                Loggers.ACTOR_LOGGER.debug("Started");
                latch.countDown();
            }

            @Override
            protected void onActorClosing()
            {
                Loggers.ACTOR_LOGGER.debug("Closing");
                latch.countDown();
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // then
        if (!latch.await(1, TimeUnit.SECONDS))
        {
            fail("onActorClosing() is never called");
        }
        assertThat(called).hasValue(0);
    }

    @Test
    public void shouldDoNothingOnCallCloseOnClosing() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger called = new AtomicInteger(0);

        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                actor.close();
                called.incrementAndGet();
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // when
        actor.actor.close();

        if (!latch.await(1, TimeUnit.SECONDS))
        {
            fail("onActorClosing() is never called");
        }
        assertThat(called).hasValue(1);
    }

    @Test
    public void shouldCallOnActorStarted() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);

        // when
        schedulerRule.submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                latch.countDown();
            }
        });

        // then
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorStarted() never called");
        }
    }

    @Test
    public void shouldRemainInActorStartedPhase() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                latch.countDown();
            }

            @Override
            protected void onActorClosing()
            {
                latch.countDown();
            }
        };

        //when
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // then
        if (!latch.await(1, TimeUnit.SECONDS))
        {
            assertThat(latch.getCount()).isEqualTo(1);
        }
        else
        {
            fail("onActorClosing() was called");
        }
    }

    @Test
    public void shouldRemainInActorStartedPhaseUntilCloseIsCalled() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                latch.countDown();
            }

            @Override
            protected void onActorClosing()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone() && latch.getCount() == 1);

        // when
        actor.actor.close();

        // then
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorClosing() never called");
        }
    }

    @Test
    public void shouldCallOnActorClosed() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                latch.countDown();
            }

            @Override
            protected void onActorClosed()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // when
        actor.actor.close();

        // then
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorClosed() never called");
        }
    }

    @Test
    public void shouldRemainInClosingStateIfWaitingOnFuture() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final CompletableActorFuture<Void> waitingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                latch.countDown();
                this.actor.runOnCompletion(waitingFuture, (v, t) -> { });
            }

            @Override
            protected void onActorClosed()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // when
        actor.actor.close();

        // then
        if (!latch.await(1, TimeUnit.SECONDS))
        {
            assertThat(latch.getCount()).isEqualTo(1);
        }
        else
        {
            fail("onActorClosed() was called");
        }
    }

    @Test

    public void shouldNotCallConsumeOnClosing()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                actor.consume(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // expect
        final ActorFuture<Void> closeFuture = actor.actor.close();
        assertThatThrownBy(() -> closeFuture.join())
            .hasMessageContaining("CLOSING")
            .hasMessageContaining("consume");
    }

    @Test
    public void shouldNotCallPollBlockingOnClosing()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                actor.pollBlocking(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // expect
        final ActorFuture<Void> closeFuture = actor.actor.close();
        assertThatThrownBy(() -> closeFuture.join())
            .hasMessageContaining("CLOSING")
            .hasMessageContaining("pollBlocking");
    }

    @Test
    public void shouldNotCallOnConditionOnClosing()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                actor.onCondition(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // expect
        final ActorFuture<Void> closeFuture = actor.actor.close();
        assertThatThrownBy(() -> closeFuture.join())
            .hasMessageContaining("CLOSING")
            .hasMessageContaining("onCondition");
    }

    @Test
    public void shouldNotCallRunDelayedOnClosing()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                actor.runDelayed(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // expect
        final ActorFuture<Void> closeFuture = actor.actor.close();
        assertThatThrownBy(() -> closeFuture.join())
            .hasMessageContaining("CLOSING")
            .hasMessageContaining("runDelayed");
    }


    @Test
    public void shouldNotCallRunAtFixedRateOnClosing()
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                actor.runAtFixedRate(null, null);
            }
        };

        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // expect
        final ActorFuture<Void> closeFuture = actor.actor.close();
        assertThatThrownBy(() -> closeFuture.join())
            .hasMessageContaining("CLOSING")
            .hasMessageContaining("runAtFixedRate");
    }

    @Test
    public void shouldCallOnActorClosedWhenClosingIsCompleted() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final CompletableActorFuture<Void> waitingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                latch.countDown();
                this.actor.runOnCompletion(waitingFuture, (v, t) -> { });
            }

            @Override
            protected void onActorClosed()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // when
        actor.actor.close();
        waitingFuture.complete(null);

        // then
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorClosed() never called");
        }
    }

    @Test
    public void shouldNotCompleteCloseFutureBeforeOnActorClosedIsCalled() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final CompletableActorFuture<Void> waitingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                latch.countDown();
                this.actor.runOnCompletion(waitingFuture, (v, t) -> { });
            }

            @Override
            protected void onActorClosed()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // when
        final ActorFuture<Void> closeFuture = actor.actor.close();

        // then
        assertThat(closeFuture).isNotDone();
    }

    @Test
    public void shouldCompleteCloseFutureAfterOnActorClosedIsCalled() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(2);
        final CompletableActorFuture<Void> waitingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorClosing()
            {
                latch.countDown();
                this.actor.runOnCompletion(waitingFuture, (v, t) -> { });
            }

            @Override
            protected void onActorClosed()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // when
        final ActorFuture<Void> closeFuture = actor.actor.close();
        waitingFuture.complete(null);

        // then
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail("onActorClosed() never called");
        }
        assertThat(closeFuture).isDone();
    }


    @Test
    public void shouldCloseActorInternally() throws InterruptedException
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);
        schedulerRule.submitActor(new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runAtFixedRate(Duration.ofMillis(1), () ->
                {
                    actor.close();
                });
            }

            @Override
            protected void onActorClosing()
            {
                latch.countDown();
            }
        });

        // then
        if (!latch.await(5, TimeUnit.MINUTES))
        {
            fail("onActorStarted() never called");
        }
    }


    // TESTS FOR
    // TODO runOnCompletion vs close -> closing
    // TODO


    @Test
    @Ignore("Discussion https://github.com/zeebe-io/zeebe/issues/704")
    public void shouldActorCloseExternallyEvenIfRunOnCompletion() throws Exception
    {
        // given
        final CountDownLatch latch = new CountDownLatch(1);
        final CompletableActorFuture<Void> waitingFuture = new CompletableActorFuture<>();
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runOnCompletion(waitingFuture, (v, t) -> { });
            }

            @Override
            protected void onActorClosing()
            {
                latch.countDown();
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        startingFuture.join();

        // when
        final Future<Void> future = actor.actor.close();

        // then
        future.get(5, TimeUnit.MINUTES);
        if (!latch.await(5, TimeUnit.SECONDS))
        {
            fail();
        }

    }

    @Test
    public void shouldActorCloseExternally() throws InterruptedException, ExecutionException, TimeoutException
    {
        // given
        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                // subscription would normally prevent actor from closing
                actor.runAtFixedRate(Duration.ofMillis(1), () ->
                {
                    // no-op
                });
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        // when
        final Future<Void> future = actor.actor.close();

        // then
        future.get(5, TimeUnit.MINUTES);

    }

    @Test
    public void shouldReSubmitClosedActor() throws InterruptedException, ExecutionException, TimeoutException
    {
        // given
        final AtomicLong invocations = new AtomicLong(0);

        final Actor actor = new Actor()
        {
            @Override
            protected void onActorStarted()
            {
                actor.runAtFixedRate(Duration.ofMillis(1), () ->
                {
                    invocations.incrementAndGet();
                });
            }
        };
        final ActorFuture<Void> startingFuture = schedulerRule.submitActor(actor);
        TestUtil.waitUntil(() -> startingFuture.isDone());

        Future<Void> future = actor.actor.close();
        future.get(5, TimeUnit.SECONDS);

        invocations.set(0);

        // when submit actor again
        schedulerRule.submitActor(actor);

        // then
        TestUtil.waitUntil(() -> invocations.get() > 0);

        future = actor.actor.close();
        future.get(5, TimeUnit.SECONDS);
    }


}
