package red.jiuzhou.ui.features;

import java.util.Objects;

/**
 * Declarative description of a launchable feature button.
 */
public final class FeatureDescriptor {

    private final String id;
    private final String displayName;
    private final String description;
    private final FeatureCategory category;
    private final FeatureLauncher launcher;

    public FeatureDescriptor(String id,
                             String displayName,
                             String description,
                             FeatureCategory category,
                             FeatureLauncher launcher) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.displayName = Objects.requireNonNull(displayName, "displayName must not be null");
        this.description = description;
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.launcher = Objects.requireNonNull(launcher, "launcher must not be null");
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public FeatureCategory category() {
        return category;
    }

    public FeatureLauncher launcher() {
        return launcher;
    }
}
