package red.jiuzhou.ui.features;

import javafx.stage.Stage;

/**
 * Launches an interactive feature window from the primary application stage.
 */
@FunctionalInterface
public interface FeatureLauncher {

    void launch(Stage owner);
}
