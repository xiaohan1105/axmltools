package red.jiuzhou.ui.features;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import javafx.concurrent.Task;

/**
 * Shared background executor for feature-oriented asynchronous tasks.
 */
public final class FeatureTaskExecutor {

    private static final FeatureTaskExecutor INSTANCE = new FeatureTaskExecutor();

    private final ExecutorService executor;

    private FeatureTaskExecutor() {
        this.executor = Executors.newCachedThreadPool(new FeatureThreadFactory());
    }

    public static void run(Task<?> task, String threadName) {
        Objects.requireNonNull(task, "task must not be null");
        INSTANCE.executor.submit(() -> {
            Thread current = Thread.currentThread();
            String originalName = current.getName();
            try {
                if (hasText(threadName)) {
                    current.setName(threadName);
                }
                task.run();
            } finally {
                current.setName(originalName);
            }
        });
    }

    public static void shutdown() {
        INSTANCE.executor.shutdownNow();
    }

    private static final class FeatureThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "feature-task-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }

    private static boolean hasText(String text) {
        return text != null && text.trim().length() > 0;
    }
}
