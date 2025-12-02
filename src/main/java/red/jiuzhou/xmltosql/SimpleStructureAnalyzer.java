package red.jiuzhou.xmltosql;

import org.dom4j.Attribute;
import org.dom4j.Element;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Helper to detect simple XML structures where list items only expose attributes/text.
 */
final class SimpleStructureAnalyzer {

    private SimpleStructureAnalyzer() {
    }

    static Optional<SimpleStructureMeta> analyze(Element root, boolean treatAsWorld) {
        if (root == null) {
            return Optional.empty();
        }

        List<Element> children = root.elements();
        if (children == null || children.isEmpty()) {
            return Optional.empty();
        }

        Element sample = children.get(0);
        if (sample == null) {
            return Optional.empty();
        }

        // Only handle flat nodes without nested children.
        if (!sample.elements().isEmpty()) {
            return Optional.empty();
        }

        List<String> attributeNames = sample.attributes().stream()
            .map(Attribute::getName)
            .collect(Collectors.toList());

        String itemTag = treatAsWorld ? "" : sample.getName();

        String primaryColumn;
        if (!attributeNames.isEmpty()) {
            String primaryAttr = attributeNames.contains("id") ? "id" : attributeNames.get(0);
            primaryColumn = prefixAttr(primaryAttr);
        } else {
            primaryColumn = sample.getName();
        }

        List<String> attributeColumns = attributeNames.stream()
            .map(SimpleStructureAnalyzer::prefixAttr)
            .collect(Collectors.toList());

        return Optional.of(new SimpleStructureMeta(
            itemTag,
            primaryColumn,
            attributeColumns,
            sample.getName(),
            true
        ));
    }

    private static String prefixAttr(String name) {
        return name.startsWith("_attr_") ? name : "_attr_" + name;
    }

    static final class SimpleStructureMeta {
        private final String itemTag;
        private final String primaryColumn;
        private final List<String> attributeColumns;
        private final String textColumn;
        private final boolean includeTextColumn;

        SimpleStructureMeta(String itemTag,
                            String primaryColumn,
                            List<String> attributeColumns,
                            String textColumn,
                            boolean includeTextColumn) {
            this.itemTag = itemTag;
            this.primaryColumn = primaryColumn;
            this.attributeColumns = attributeColumns == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(attributeColumns);
            this.textColumn = textColumn;
            this.includeTextColumn = includeTextColumn;
        }

        String getItemTag() {
            return itemTag;
        }

        String getPrimaryColumn() {
            return primaryColumn;
        }

        List<String> getAttributeColumns() {
            return attributeColumns;
        }

        String getTextColumn() {
            return textColumn;
        }

        boolean hasTextColumn() {
            return includeTextColumn;
        }

        String getPrimaryDisplayName() {
            return stripAttrPrefix(primaryColumn);
        }

        static String stripAttrPrefix(String name) {
            return name != null && name.startsWith("_attr_") ? name.substring(6) : name;
        }
    }
}
