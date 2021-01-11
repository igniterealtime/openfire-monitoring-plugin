/*
 * Copyright (C) 2021 Ignite Realtime Foundation. All rights reserved.
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
package org.jivesoftware.openfire.plugin;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

/**
 * A delegating ExecutorService that attempts to coerce any threads that were used by the delegate to stop to exist,
 * after the instance is shut down.
 *
 * The purpose of this implementation is to facilitate that the classloader that has loaded the instance becomes
 * eligible for garbage collection fast.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 * @see <a href="https://github.com/igniterealtime/openfire-monitoring-plugin/issues/155">Issue #155</a>
 */
public class ForceClosingThreadPoolExecutor implements ExecutorService {

    final ExecutorService delegate;

    public ForceClosingThreadPoolExecutor(@Nonnull final ExecutorService delegate) {
        this.delegate = delegate;
    }

    protected void killThreads()
    {
        if (delegate instanceof ThreadPoolExecutor) {
            // Issue #155: References to classes (used during execution of Runnable instances) exist for as long as the
            // duration of the keepAliveTime. It is assumed that even after termination, (cached) threads exist, that keep
            // these references alive. This in turn prevents garbage collection, which in turn leads to classloading
            // problems when a new instance of the plugin that this code is part if is being loaded. The next two lines try
            // to coerce threads from being removed, thus preventing the issue.
            ((ThreadPoolExecutor) delegate).allowCoreThreadTimeOut(true);
            ((ThreadPoolExecutor) delegate).setKeepAliveTime(1, TimeUnit.NANOSECONDS);
        }
    }

    public void shutdown() {
        delegate.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return delegate.submit(task);
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return delegate.submit(task, result);
    }

    public Future<?> submit(Runnable task) {
        return delegate.submit(task);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return delegate.invokeAll(tasks);
    }

    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.invokeAll(tasks, timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return delegate.invokeAny(tasks);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return delegate.invokeAny(tasks, timeout, unit);
    }

    public void execute(Runnable command) {
        delegate.execute(command);
    }
}
