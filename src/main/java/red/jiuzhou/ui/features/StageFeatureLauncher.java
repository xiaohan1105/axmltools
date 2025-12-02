package red.jiuzhou.ui.features;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.function.Supplier;

import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/**
 * Launch strategy that manages a single Stage instance per feature.
 */
public final class StageFeatureLauncher implements FeatureLauncher {

    private final Supplier<Stage> stageFactory;
    private Stage cachedStage;

    public StageFeatureLauncher(Supplier<Stage> stageFactory) {
        this.stageFactory = Objects.requireNonNull(stageFactory, "stageFactory must not be null");
    }

    @Override
    public synchronized void launch(Stage owner) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> launch(owner));
            return;
        }

        prepareStage(owner);

        if (!cachedStage.isShowing()) {
            cachedStage.show();
        }
        cachedStage.toFront();
    }

    public Stage ensureStage(Stage owner) {
        if (Platform.isFxApplicationThread()) {
            synchronized (this) {
                prepareStage(owner);
                return cachedStage;
            }
        }

        CountDownLatch latch = new CountDownLatch(1);
        final Stage[] holder = new Stage[1];
        Platform.runLater(() -> {
            synchronized (StageFeatureLauncher.this) {
                prepareStage(owner);
                holder[0] = cachedStage;
            }
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        return holder[0];
    }

    private synchronized void handleStageClosed(Stage stageReference) {
        if (stageReference == cachedStage) {
            cachedStage = null;
        }
    }

    private void prepareStage(Stage owner) {
        if (cachedStage == null) {
            cachedStage = stageFactory.get();
            Stage stageReference = cachedStage;
            EventHandler<WindowEvent> originalHiddenHandler = cachedStage.getOnHidden();
            cachedStage.setOnHidden(event -> {
                if (originalHiddenHandler != null) {
                    originalHiddenHandler.handle(event);
                }
                handleStageClosed(stageReference);
            });
        }
        if (owner != null && cachedStage.getOwner() == null) {
            cachedStage.initOwner(owner);
        }
    }
}
