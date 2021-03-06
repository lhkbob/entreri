/*
 * Entreri, an entity-component framework in Java
 *
 * Copyright (c) 2014, Michael Ludwig
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *     Redistributions of source code must retain the above copyright notice,
 *         this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above copyright notice,
 *         this list of conditions and the following disclaimer in the
 *         documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.lhkbob.entreri.task;

import com.lhkbob.entreri.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * Job
 * ===
 *
 * Job represents a list of {@link Task tasks} that must be executed in a particular order so that they
 * produce a meaningful computation over an entity system. Examples of a job might be to render a frame, which
 * could then be decomposed into tasks for computing the visible objects, occluded objects, the optimal
 * rendering order, and shadow computations, etc.
 *
 * Jobs are created by first getting the {@link Scheduler} from a particular EntitySystem, and then calling
 * {@link Scheduler#createJob(String, Task...)}. The name of a job is used to for informational purposes and
 * does not affect its behavior.
 *
 * @author Michael Ludwig
 */
public class Job implements Runnable {
    private final Task[] tasks;
    private final Map<Class<? extends Result>, List<ResultReporter>> resultMethods;

    private final List<Lock> locks; // includes system lock and type locks in proper, consistent order to prevent deadlocks

    private final Scheduler scheduler;
    private final String name;

    private final Set<Class<? extends Result>> singletonResults;
    private int taskIndex;

    /**
     * Create a new job with the given name and tasks.
     *
     * @param name      The name of the job
     * @param scheduler The owning scheduler
     * @param tasks     The tasks in order of execution
     * @throws NullPointerException if name is null, tasks is null or contains null elements
     */
    Job(String name, Scheduler scheduler, Task... tasks) {
        if (name == null) {
            throw new NullPointerException("Name cannot be null");
        }
        this.scheduler = scheduler;
        this.tasks = new Task[tasks.length];
        this.name = name;

        singletonResults = new HashSet<>();
        resultMethods = new HashMap<>();
        taskIndex = -1;

        boolean exclusive = false;
        Set<Class<? extends Component>> writtenTypes = new HashSet<>();
        Set<Class<? extends Component>> readTypes = new HashSet<>();
        for (int i = 0; i < tasks.length; i++) {
            if (tasks[i] == null) {
                throw new NullPointerException("Task cannot be null");
            }

            this.tasks[i] = tasks[i];

            // collect parallelization info
            ParallelAware config = tasks[i].getClass().getAnnotation(ParallelAware.class);
            if (config == null) {
                // must assume it could touch anything
                exclusive = true;
            } else {
                exclusive |= config.entitySetModified();
                for (Class<? extends Component> written : config.modifiedComponents()) {
                    readTypes.remove(written); // if it was read-only it can't be anymore
                    writtenTypes.add(written);
                }
                for (Class<? extends Component> readOnly : config.readOnlyComponents()) {
                    // if it's a modified type don't put it in the readtypes collection
                    if (!writtenTypes.contains(readOnly)) {
                        readTypes.add(readOnly);
                    }
                }
            }

            // record all result report methods exposed by this task
            for (Method m : tasks[i].getClass().getMethods()) {
                if (m.getName().equals("report")) {
                    if (m.getReturnType().equals(void.class) &&
                        m.getParameterTypes().length == 1 &&
                        Result.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        // found a valid report method
                        m.setAccessible(true);
                        ResultReporter reporter = new ResultReporter(m, i);
                        Class<? extends Result> type = reporter.getResultType();

                        List<ResultReporter> all = resultMethods.get(type);
                        if (all == null) {
                            all = new ArrayList<>();
                            resultMethods.put(type, all);
                        }

                        all.add(reporter);
                    }
                }
            }
        }

        locks = new ArrayList<>();
        // first lock is always for the system, either read or write depending on exclusivity of tasks
        if (exclusive) {
            locks.add(scheduler.getEntitySystemLock().writeLock());
        } else {
            locks.add(scheduler.getEntitySystemLock().readLock());
        }

        // type locks are acquired always in name order, regardless of it its a read or write lock
        List<Class<? extends Component>> consistentTypeOrdering = new ArrayList<>(writtenTypes.size() +
                                                                                  readTypes.size());
        consistentTypeOrdering.addAll(writtenTypes);
        consistentTypeOrdering.addAll(readTypes);
        Collections.sort(consistentTypeOrdering, new Comparator<Class<? extends Component>>() {
            @Override
            public int compare(Class<? extends Component> o1, Class<? extends Component> o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (Class<? extends Component> type : consistentTypeOrdering) {
            if (writtenTypes.contains(type)) {
                locks.add(scheduler.getTypeLock(type).writeLock());
            } else {
                locks.add(scheduler.getTypeLock(type).readLock());
            }
        }
    }

    /**
     * @return The designated name of this job
     */
    public String getName() {
        return name;
    }

    /**
     * @return The Scheduler that created this job
     */
    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Invoke all tasks in this job. This method is thread-safe and will use its owning scheduler to
     * coordinate the locks necessary to safely execute its tasks.
     *
     * Although {@link Scheduler} has convenience methods to repeatedly invoke a job, this method can be
     * called directly if a more controlled job execution scheme is required.
     */
    @Override
    public void run() {
        // repeatedly run jobs until no task produces a post-process task
        Job toInvoke = this;
        while (toInvoke != null) {
            toInvoke = toInvoke.runJob();
        }
    }

    private Job runJob() {
        // acquire locks (already correctly organized in the constructor)
        for (int i = 0; i < locks.size(); i++) {
            locks.get(i).lock();
        }

        try {
            // reset all tasks and the job
            taskIndex = 0;
            singletonResults.clear();
            for (int i = 0; i < tasks.length; i++) {
                tasks[i].reset(scheduler.getEntitySystem());
            }

            // process all tasks and collect all returned tasks, in order
            List<Task> postProcess = new ArrayList<>();
            for (int i = 0; i < tasks.length; i++) {
                taskIndex = i;
                Task after = tasks[i].process(scheduler.getEntitySystem(), this);
                if (after != null) {
                    postProcess.add(after);
                }
            }

            // set this to negative so that report() can fail now that
            // we're not executing tasks anymore
            taskIndex = -1;

            if (postProcess.isEmpty()) {
                // nothing to process afterwards
                return null;
            } else {
                Task[] tasks = postProcess.toArray(new Task[postProcess.size()]);
                return new Job(name + "-postprocess", scheduler, tasks);
            }
        } finally {
            // unlock
            for (int i = locks.size() - 1; i >= 0; i--) {
                locks.get(i).unlock();
            }
        }
    }

    /**
     * Report the given result instance to all tasks in this job (ignoring post-processing tasks), that have
     * declared a public method named 'report' that takes a Result sub-type that is compatible with `r`'s
     * type.
     *
     * @param r The result to report
     * @throws NullPointerException  if r is null
     * @throws IllegalStateException if r is a singleton result whose type has already been reported by
     *                               another task in this job, or if the job is not currently executing tasks
     */
    public void report(Result r) {
        if (r == null) {
            throw new NullPointerException("Cannot report null results");
        }
        if (taskIndex < 0) {
            throw new IllegalStateException("Can only be invoked by a task from within run()");
        }

        if (r.isSingleton()) {
            // make sure this is the first we've seen the result
            if (!singletonResults.add(r.getClass())) {
                throw new IllegalStateException("Singleton result of type: " + r.getClass() +
                                                " has already been reported during " + name + "'s execution");
            }
        }

        Class<?> type = r.getClass();
        while (Result.class.isAssignableFrom(type)) {
            // report to all methods that receive the type
            List<ResultReporter> all = resultMethods.get(type);
            if (all != null) {
                int ct = all.size();
                for (int i = 0; i < ct; i++) {
                    all.get(i).report(r);
                }
            }

            type = type.getSuperclass();
        }
    }

    @Override
    public String toString() {
        return "Job(" + name + ", # tasks=" + tasks.length + ")";
    }

    private class ResultReporter {
        private final Method reportMethod;
        private final int taskIndex;

        public ResultReporter(Method reportMethod, int taskIndex) {
            this.reportMethod = reportMethod;
            this.taskIndex = taskIndex;
        }

        public void report(Result r) {
            try {
                reportMethod.invoke(Job.this.tasks[taskIndex], r);
            } catch (IllegalArgumentException | IllegalAccessException e) {
                // shouldn't happen, since we check the type before invoking
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException("Error reporting result", e.getCause());
            }
        }

        @SuppressWarnings("unchecked")
        public Class<? extends Result> getResultType() {
            return (Class<? extends Result>) reportMethod.getParameterTypes()[0];
        }
    }
}
