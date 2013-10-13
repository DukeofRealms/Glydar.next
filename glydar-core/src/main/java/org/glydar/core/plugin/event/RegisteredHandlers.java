package org.glydar.core.plugin.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.glydar.api.plugin.Plugin;
import org.glydar.api.plugin.event.EventExecutor;
import org.glydar.api.plugin.event.EventPriority;

import com.google.common.base.Predicate;

/**
 * Stores each registered handler of a given {@link Event} subclass.
 * <p/>
 * These classes holds a reference to `parent` and `children`
 * RegisteredHandlers, thus creating a tree hierarchy which is supposed to
 * mirror the Event classes one.
 * <p/>
 * In other words, the parent of an instance storing handlers of a given Event
 * class, is the RegisteredHandlers instance storing handler for the superclass
 * of this Event class.
 */
class RegisteredHandlers {

    private final RegisteredHandlers parent;
    private final List<RegisteredHandlers> children = new ArrayList<>();
    private final List<RegisteredHandler> list = new ArrayList<>();
    EventExecutor<?>[] resolvedExecutors = new EventExecutor<?>[0];

    public RegisteredHandlers() {
        this.parent = null;
    }

    public RegisteredHandlers(RegisteredHandlers parent) {
        this.parent = parent;
        parent.addChild(this);
    }

    private void resolve() {
        List<RegisteredHandler> resolvedHandlers = new ArrayList<>();
        resolveHandlersIn(this, resolvedHandlers);
        Collections.sort(resolvedHandlers);

        int i = 0;
        resolvedExecutors = new EventExecutor<?>[resolvedHandlers.size()];
        for (RegisteredHandler handler : resolvedHandlers) {
            resolvedExecutors[i++] = handler.getExecutor();
        }

        for (RegisteredHandlers child : children) {
            child.resolve();
        }
    }

    public void addChild(RegisteredHandlers child) {
        children.add(child);
        resolve();
    }

    public void addHandler(RegisteredHandler registeredHandler) {
        list.add(registeredHandler);
        resolve();
    }

    public void removeHandlersIf(Predicate<RegisteredHandler> predicate) {
        boolean modified = false;

        Iterator<RegisteredHandler> handlerIt = list.iterator();
        while (handlerIt.hasNext()) {
            if (predicate.apply(handlerIt.next())) {
                modified = true;
                handlerIt.remove();
            }
        }

        if (modified) {
            resolve();
        }
    }

    private static void resolveHandlersIn(RegisteredHandlers handlers, List<RegisteredHandler> list) {
        for (RegisteredHandler handler : handlers.list) {
            list.add(handler);
        }
        if (handlers.parent != null) {
            resolveHandlersIn(handlers.parent, list);
        }
    }

    static class RegisteredHandler implements Comparable<RegisteredHandler> {

        private final Plugin plugin;
        private final EventPriority priority;
        private final EventExecutor<?> executor;
        private final int index;

        public RegisteredHandler(Plugin plugin, int index, EventPriority priority, EventExecutor<?> executor) {
            this.plugin = plugin;
            this.index = index;
            this.priority = priority;
            this.executor = executor;
        }

        public Plugin getPlugin() {
            return plugin;
        }

        public EventPriority getPriority() {
            return priority;
        }

        public EventExecutor<?> getExecutor() {
            return executor;
        }

        @Override
        public int compareTo(RegisteredHandler other) {
            int priorityComparison = priority.compareTo(other.priority);
            if (priorityComparison != 0) {
                return priorityComparison;
            }

            return index - other.index;
        }
    }
}
