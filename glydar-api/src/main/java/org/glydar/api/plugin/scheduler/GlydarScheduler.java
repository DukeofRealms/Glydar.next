package org.glydar.api.plugin.scheduler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.glydar.api.plugin.Plugin;

import com.google.common.base.Preconditions;

/**
 * GlydarScheduler copied from CraftScheduler by the Bukkit project.
 * 
 * @author YoshiGenius
 */
public class GlydarScheduler {

    /**
     * Counter for IDs. Order doesn't matter, only uniqueness.
     */
    private final AtomicInteger                          ids         = new AtomicInteger(1);
    /**
     * Current head of linked-list. This reference is always stale,
     * {@link GlydarTask#next} is the live reference.
     */
    private volatile GlydarTask                          head        = new GlydarTask();
    /**
     * Tail of a linked-list. AtomicReference only matters when adding to queue
     */
    private final AtomicReference<GlydarTask>            tail        = new AtomicReference<GlydarTask>(head);
    /**
     * Main thread logic only
     */
    private final PriorityQueue<GlydarTask>              pending     = new PriorityQueue<GlydarTask>(10,
                                                                             new Comparator<GlydarTask>() {
                                                                                 @Override
                                                                                 public int compare(
                                                                                         final GlydarTask o1,
                                                                                         final GlydarTask o2) {
                                                                                     return (int) (o1.getNextRun() - o2
                                                                                             .getNextRun());
                                                                                 }
                                                                             });
    /**
     * Main thread logic only
     */
    private final List<GlydarTask>                       temp        = new ArrayList<GlydarTask>();
    /**
     * These are tasks that are currently active. It's provided for 'viewing'
     * the current state.
     */
    private final ConcurrentHashMap<Integer, GlydarTask> runners     = new ConcurrentHashMap<Integer, GlydarTask>();
    private volatile int                                 currentTick = -1;
    private static final int                             RECENT_TICKS;

    static {
        RECENT_TICKS = 30;
    }

    public int scheduleDelayedTask(final Plugin plugin, final Runnable task) {
        return this.scheduleDelayedTask(plugin, task, 0l);
    }

    public GlydarTask runTask(Plugin plugin, Runnable runnable) {
        return runTaskLater(plugin, runnable, 0l);
    }

    public int scheduleDelayedTask(final Plugin plugin, final Runnable task, final long delay) {
        return this.scheduleRepeatingTask(plugin, task, delay, -1l);
    }

    public GlydarTask runTaskLater(Plugin plugin, Runnable runnable, long delay) {
        return runTaskTimer(plugin, runnable, delay, -1l);
    }

    public int scheduleRepeatingTask(final Plugin plugin, final Runnable runnable, long delay, long period) {
        return runTaskTimer(plugin, runnable, delay, period).getID();
    }

    public GlydarTask runTaskTimer(Plugin plugin, Runnable runnable, long delay, long period) {
        validate(plugin, runnable);
        if (delay < 0l) {
            delay = 0;
        }
        if (period == 0l) {
            period = 1l;
        }
        else if (period < -1l) {
            period = -1l;
        }
        return handle(new GlydarTask(plugin, runnable, nextId(), period), delay);
    }

    public <T> Future<T> callSyncMethod(final Plugin plugin, final Callable<T> task) {
        validate(plugin, task);
        final GlydarFuture<T> future = new GlydarFuture<T>(task, plugin, nextId());
        handle(future, 0l);
        return future;
    }

    public void cancelTask(final int taskId) {
        if (taskId <= 0) {
            return;
        }
        GlydarTask task = runners.get(taskId);
        if (task != null) {
            task.cancel0();
        }
        task = new GlydarTask(new Runnable() {
            @Override
            public void run() {
                if (!check(GlydarScheduler.this.temp)) {
                    check(GlydarScheduler.this.pending);
                }
            }

            private boolean check(final Iterable<GlydarTask> collection) {
                final Iterator<GlydarTask> tasks = collection.iterator();
                while (tasks.hasNext()) {
                    final GlydarTask task = tasks.next();
                    if (task.getID() == taskId) {
                        task.cancel0();
                        tasks.remove();
                        runners.remove(taskId);
                        return true;
                    }
                }
                return false;
            }
        });
        handle(task, 0l);
        for (GlydarTask taskPending = head.getNext(); taskPending != null; taskPending = taskPending.getNext()) {
            if (taskPending == task) {
                return;
            }
            if (taskPending.getID() == taskId) {
                taskPending.cancel0();
            }
        }
    }

    public void cancelTasks(final Plugin plugin) {
        Preconditions.checkNotNull(plugin, "Cannot cancel tasks of null plugin");
        final GlydarTask task = new GlydarTask(new Runnable() {
            @Override
            public void run() {
                check(GlydarScheduler.this.pending);
                check(GlydarScheduler.this.temp);
            }

            void check(final Iterable<GlydarTask> collection) {
                final Iterator<GlydarTask> tasks = collection.iterator();
                while (tasks.hasNext()) {
                    final GlydarTask task = tasks.next();
                    if (task.getPlugin().equals(plugin)) {
                        task.cancel0();
                        tasks.remove();
                        runners.remove(task.getID());
                    }
                }
            }
        });
        handle(task, 0l);
        for (GlydarTask taskPending = head.getNext(); taskPending != null; taskPending = taskPending.getNext()) {
            if (taskPending == task) {
                return;
            }
            if (taskPending.getID() != -1 && taskPending.getPlugin().equals(plugin)) {
                taskPending.cancel0();
            }
        }
        for (GlydarTask runner : runners.values()) {
            if (runner.getPlugin().equals(plugin)) {
                runner.cancel0();
            }
        }
    }

    public void cancelAllTasks() {
        final GlydarTask task = new GlydarTask(new Runnable() {
            @Override
            public void run() {
                Iterator<GlydarTask> it = GlydarScheduler.this.runners.values().iterator();
                while (it.hasNext()) {
                    GlydarTask task = it.next();
                    task.cancel0();
                    it.remove();
                }
                GlydarScheduler.this.pending.clear();
                GlydarScheduler.this.temp.clear();
            }
        });
        handle(task, 0l);
        for (GlydarTask taskPending = head.getNext(); taskPending != null; taskPending = taskPending.getNext()) {
            if (taskPending == task) {
                break;
            }
            taskPending.cancel0();
        }
        for (GlydarTask runner : runners.values()) {
            runner.cancel0();
        }
    }

    public boolean isQueued(final int taskId) {
        if (taskId <= 0) {
            return false;
        }
        for (GlydarTask task = head.getNext(); task != null; task = task.getNext()) {
            if (task.getID() == taskId) {
                return task.getInterval() >= -1l; // The task will run
            }
        }
        GlydarTask task = runners.get(taskId);
        return task != null && task.getInterval() >= -1l;
    }

    public List<GlydarTask> getPendingTasks() {
        final ArrayList<GlydarTask> truePending = new ArrayList<GlydarTask>();
        for (GlydarTask task = head.getNext(); task != null; task = task.getNext()) {
            if (task.getID() != -1) {
                // -1 is special code
                truePending.add(task);
            }
        }

        final ArrayList<GlydarTask> pending = new ArrayList<GlydarTask>();
        for (GlydarTask task : runners.values()) {
            if (task.getInterval() >= -1l) {
                pending.add(task);
            }
        }

        for (final GlydarTask task : truePending) {
            if (task.getInterval() >= -1l && !pending.contains(task)) {
                pending.add(task);
            }
        }
        return pending;
    }

    /**
     * This method is designed to never block or wait for locks; an immediate
     * execution of all current tasks.
     */
    public void mainThreadHeartbeat(final int currentTick) {
        this.currentTick = currentTick;
        final List<GlydarTask> temp = this.temp;
        parsePending();
        while (isReady(currentTick)) {
            final GlydarTask task = pending.remove();
            if (task.getInterval() < -1l) {
                runners.remove(task.getID(), task);
                parsePending();
                continue;
            }
            try {
                task.run();
            }
            catch (final Throwable throwable) {
                task.getPlugin()
                        .getLogger()
                        .warning(throwable, "Task #{0} for {1} generated an exception", task.getID());
            }
            parsePending();
            final long period = task.getInterval(); // State consistency
            if (period > 0) {
                task.setNextRun(currentTick + period);
                temp.add(task);
            }
            else {
                runners.remove(task.getID());
            }
        }
        pending.addAll(temp);
        temp.clear();
    }

    private void addTask(final GlydarTask task) {
        final AtomicReference<GlydarTask> tail = this.tail;
        GlydarTask tailTask = tail.get();
        while (!tail.compareAndSet(tailTask, task)) {
            tailTask = tail.get();
        }
        tailTask.setNext(task);
    }

    private GlydarTask handle(final GlydarTask task, final long delay) {
        task.setNextRun(currentTick + delay);
        addTask(task);
        return task;
    }

    private static void validate(final Plugin plugin, final Object task) {
        Preconditions.checkNotNull(plugin, "Plugin cannot be null");
        Preconditions.checkNotNull(task, "Task cannot be null");
    }

    private int nextId() {
        return ids.incrementAndGet();
    }

    private void parsePending() {
        GlydarTask head = this.head;
        GlydarTask task = head.getNext();
        GlydarTask lastTask = head;
        for (; task != null; task = (lastTask = task).getNext()) {
            if (task.getID() == -1) {
                task.run();
            }
            else if (task.getInterval() >= -1l) {
                pending.add(task);
                runners.put(task.getID(), task);
            }
        }
        // We split this because of the way things are ordered for all of the
        // async calls in CraftScheduler
        // (it prevents race-conditions)
        for (task = head; task != lastTask; task = head) {
            head = task.getNext();
            task.setNext(null);
        }
        this.head = lastTask;
    }

    private boolean isReady(final int currentTick) {
        return !pending.isEmpty() && pending.peek().getNextRun() <= currentTick;
    }

    @Override
    public String toString() {
        int debugTick = currentTick;
        StringBuilder string = new StringBuilder("Recent tasks from ").append(debugTick - RECENT_TICKS).append('-')
                .append(debugTick).append('{');
        return string.append('}').toString();
    }

    private static final GlydarScheduler instance = new GlydarScheduler();

    public static GlydarScheduler getInstance() {
        return instance;
    }
}
