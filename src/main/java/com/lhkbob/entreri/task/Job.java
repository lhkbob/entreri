package com.lhkbob.entreri.task;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.lhkbob.entreri.ComponentData;

public class Job implements Runnable {
    private final Task[] tasks;
    private final Map<Class<? extends Result>, List<ResultReporter>> resultMethods;

    private final boolean needsExclusiveLock;
    private final List<Class<? extends ComponentData<?>>> locks;

    private final Scheduler scheduler;
    private final String name;

    private final Set<Class<? extends Result>> singletonResults;
    private int taskIndex;

    Job(String name, Scheduler scheduler, Task... tasks) {
        this.scheduler = scheduler;
        this.tasks = new Task[tasks.length];
        this.name = name;

        singletonResults = new HashSet<Class<? extends Result>>();
        resultMethods = new HashMap<Class<? extends Result>, List<ResultReporter>>();

        boolean exclusive = false;
        Set<Class<? extends ComponentData<?>>> typeLocks = new HashSet<Class<? extends ComponentData<?>>>();
        for (int i = 0; i < tasks.length; i++) {
            this.tasks[i] = tasks[i];

            // collect parallelization info (which should not change over
            // a task's lifetime)
            if (tasks[i] instanceof ParallelAware) {
                ParallelAware pa = (ParallelAware) tasks[i];
                exclusive |= pa.isEntitySetModified();
                typeLocks.addAll(pa.getAccessedComponents());
            } else {
                // must assume it could touch anything
                exclusive = true;
            }

            // record all result report methods exposed by this task
            for (Method m : tasks[i].getClass().getMethods()) {
                if (m.getName().equals("report")) {
                    if (m.getReturnType().equals(void.class) && m.getParameterTypes().length == 1 && Result.class.isAssignableFrom(m.getParameterTypes()[0])) {
                        // found a valid report method
                        ResultReporter reporter = new ResultReporter(m, i);
                        Class<? extends Result> type = reporter.getResultType();

                        List<ResultReporter> all = resultMethods.get(type);
                        if (all == null) {
                            all = new ArrayList<ResultReporter>();
                            resultMethods.put(type, all);
                        }

                        all.add(reporter);
                    }
                }
            }
        }

        if (exclusive) {
            needsExclusiveLock = true;
            locks = null;
        } else {
            needsExclusiveLock = false;
            locks = new ArrayList<Class<? extends ComponentData<?>>>(typeLocks);
            // give locks a consistent ordering
            Collections.sort(locks, new Comparator<Class<? extends ComponentData<?>>>() {
                @Override
                public int compare(Class<? extends ComponentData<?>> o1,
                                   Class<? extends ComponentData<?>> o2) {
                    return o1.getName().compareTo(o2.getName());
                }
            });
        }
    }

    public String getName() {
        return name;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void run() {
        // repeatedly run jobs until no task produces a post-process task
        Job toInvoke = this;
        while (toInvoke != null) {
            toInvoke = toInvoke.runJob();
        }
    }

    private Job runJob() {
        // acquire locks (either exclusive or per type in order)
        if (needsExclusiveLock) {
            scheduler.getEntitySystemLock().writeLock().lock();
        } else {
            scheduler.getEntitySystemLock().readLock().lock();
            for (int i = 0; i < locks.size(); i++) {
                scheduler.getTypeLock(locks.get(i)).lock();
            }
        }

        try {
            // reset all tasks and the job
            taskIndex = 0;
            singletonResults.clear();
            for (int i = 0; i < tasks.length; i++) {
                tasks[i].reset();
            }

            // process all tasks and collect all returned tasks, in order
            List<Task> postProcess = new ArrayList<Task>();
            for (int i = 0; i < tasks.length; i++) {
                taskIndex = i;
                Task after = tasks[i].process(scheduler.getEntitySystem(), this);
                if (after != null) {
                    postProcess.add(after);
                }
            }

            if (postProcess.isEmpty()) {
                // nothing to process afterwards
                return null;
            } else {
                Task[] tasks = postProcess.toArray(new Task[postProcess.size()]);
                return new Job(name + "-postprocess", scheduler, tasks);
            }
        } finally {
            // unlock
            if (needsExclusiveLock) {
                scheduler.getEntitySystemLock().writeLock().unlock();
            } else {
                for (int i = locks.size() - 1; i >= 0; i--) {
                    scheduler.getTypeLock(locks.get(i)).unlock();
                }
                scheduler.getEntitySystemLock().readLock().unlock();
            }
        }
    }

    public void report(Result r) {
        if (r.isSingleton()) {
            // make sure this is the first we've seen the result
            if (!singletonResults.add(r.getClass())) {
                throw new IllegalStateException("Singleton result of type: " + r.getClass() + " has already been reported during " + name + "'s execution");
            }
        }

        Class<?> type = r.getClass();
        while (Result.class.isAssignableFrom(type)) {
            // report to all methods that receive the type
            List<ResultReporter> all = resultMethods.get(type);
            if (all != null) {
                int ct = all.size();
                for (int i = 0; i < ct; i++) {
                    // this will filter on the current task index to only report
                    // results to future tasks
                    all.get(i).report(r);
                }
            }

            type = type.getSuperclass();
        }
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
                if (taskIndex > Job.this.taskIndex) {
                    reportMethod.invoke(Job.this.tasks[taskIndex], r);
                }
            } catch (IllegalArgumentException e) {
                // shouldn't happen, since we check the type before invoking
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                // shouldn't happen since we only use public methods
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