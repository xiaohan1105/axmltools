package red.jiuzhou.ui;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.extern.slf4j.Slf4j;
import red.jiuzhou.search.GlobalSearchEngine;
import red.jiuzhou.search.GlobalSearchEngine.SearchResult;
import red.jiuzhou.search.GlobalSearchEngine.ReplaceOptions;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 全局搜索替换对话框
 * 提供友好的UI界面进行XML文件的搜索和替换操作
 *
 * 功能：
 * - 多种搜索模式（文本、正则、XPath、ID等）
 * - 条件替换（支持属性条件）
 * - 实时预览
 * - 批量操作
 * - 关联文件提示
 */
@Slf4j
public class SearchReplaceDialog extends Stage {

    private final GlobalSearchEngine searchEngine;
    private final ObservableList<SearchResultItem> searchResults = FXCollections.observableArrayList();

    // UI组件
    private TextField searchField;
    private TextField replaceField;
    private TextField conditionField;
    private ComboBox<GlobalSearchEngine.SearchMode> searchModeCombo;
    private ComboBox<String> searchScopeCombo;
    private TextField customPathField;
    private CheckBox caseSensitiveCheck;
    private CheckBox wholeWordCheck;
    private CheckBox regexCheck;
    private CheckBox previewCheck;
    private CheckBox backupCheck;
    private TableView<SearchResultItem> resultTable;
    private Label statusLabel;
    private ProgressBar progressBar;
    private TextArea previewArea;

    // 搜索结果项
    public static class SearchResultItem {
        private final CheckBox selected = new CheckBox();
        private final SimpleStringProperty file;
        private final SimpleStringProperty path;
        private final SimpleStringProperty match;
        private final SimpleStringProperty context;
        private final SearchResult searchResult;

        public SearchResultItem(SearchResult result) {
            this.searchResult = result;
            this.selected.setSelected(true);
            this.file = new SimpleStringProperty(new File(result.getFilePath()).getName());
            this.path = new SimpleStringProperty(result.getElementPath());
            this.match = new SimpleStringProperty(result.getMatchedText());

            // 生成上下文
            String contextText = "";
            if (result.getContextBefore() != null) {
                contextText += result.getContextBefore() + " ";
            }
            contextText += "[" + result.getMatchedText() + "]";
            if (result.getContextAfter() != null) {
                contextText += " " + result.getContextAfter();
            }
            this.context = new SimpleStringProperty(contextText);
        }

        public CheckBox getSelected() { return selected; }
        public String getFile() { return file.get(); }
        public String getPath() { return path.get(); }
        public String getMatch() { return match.get(); }
        public String getContext() { return context.get(); }
        public SearchResult getSearchResult() { return searchResult; }
    }

    public SearchReplaceDialog(Stage owner) {
        initModality(Modality.WINDOW_MODAL);
        initOwner(owner);
        setTitle("全局搜索与替换");
        searchEngine = new GlobalSearchEngine();
        searchEngine.initializeRelationships();

        initUI();
        setupEventHandlers();
    }

    private void initUI() {
        VBox root = new VBox(10);
        root.setPadding(new Insets(15));
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 搜索区域
        VBox searchSection = createSearchSection();

        // 替换区域
        VBox replaceSection = createReplaceSection();

        // 结果表格
        VBox resultSection = createResultSection();

        // 预览区域
        VBox previewSection = createPreviewSection();

        // 状态栏
        HBox statusBar = createStatusBar();

        root.getChildren().addAll(searchSection, replaceSection, resultSection,
                                 previewSection, statusBar);

        Scene scene = new Scene(root, 1200, 800);
        setScene(scene);
    }

    private VBox createSearchSection() {
        VBox section = new VBox(8);
        section.setStyle("-fx-background-color: white; -fx-padding: 10; " +
                        "-fx-border-color: #ddd; -fx-border-radius: 5;");

        Label titleLabel = new Label("搜索设置");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // 搜索输入行
        HBox searchRow = new HBox(10);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("搜索内容:");
        searchLabel.setPrefWidth(80);

        searchField = new TextField();
        searchField.setPrefWidth(400);
        searchField.setPromptText("输入搜索内容（支持正则表达式、XPath）");

        searchModeCombo = new ComboBox<>();
        searchModeCombo.getItems().addAll(GlobalSearchEngine.SearchMode.values());
        searchModeCombo.setValue(GlobalSearchEngine.SearchMode.SMART);
        searchModeCombo.setPrefWidth(120);

        Button searchButton = new Button("搜索");
        searchButton.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white;");

        searchRow.getChildren().addAll(searchLabel, searchField, searchModeCombo, searchButton);

        // 搜索范围行
        HBox scopeRow = new HBox(10);
        scopeRow.setAlignment(Pos.CENTER_LEFT);

        Label scopeLabel = new Label("搜索范围:");
        scopeLabel.setPrefWidth(80);

        searchScopeCombo = new ComboBox<>();
        searchScopeCombo.getItems().addAll(
            "服务端XML",
            "客户端XML",
            "所有XML",
            "自定义路径"
        );
        searchScopeCombo.setValue("所有XML");
        searchScopeCombo.setPrefWidth(150);

        customPathField = new TextField();
        customPathField.setPrefWidth(300);
        customPathField.setPromptText("自定义搜索路径");
        customPathField.setDisable(true);

        Button browseButton = new Button("浏览...");
        browseButton.setDisable(true);

        scopeRow.getChildren().addAll(scopeLabel, searchScopeCombo,
                                      customPathField, browseButton);

        // 搜索选项行
        HBox optionRow = new HBox(20);
        optionRow.setAlignment(Pos.CENTER_LEFT);
        optionRow.setPadding(new Insets(5, 0, 0, 85));

        caseSensitiveCheck = new CheckBox("区分大小写");
        wholeWordCheck = new CheckBox("全字匹配");
        regexCheck = new CheckBox("使用正则表达式");

        optionRow.getChildren().addAll(caseSensitiveCheck, wholeWordCheck, regexCheck);

        section.getChildren().addAll(titleLabel, searchRow, scopeRow, optionRow);

        // 事件处理
        searchButton.setOnAction(e -> performSearch());

        searchScopeCombo.setOnAction(e -> {
            boolean custom = "自定义路径".equals(searchScopeCombo.getValue());
            customPathField.setDisable(!custom);
            browseButton.setDisable(!custom);
        });

        browseButton.setOnAction(e -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("选择搜索目录");
            File dir = chooser.showDialog(this);
            if (dir != null) {
                customPathField.setText(dir.getAbsolutePath());
            }
        });

        return section;
    }

    private VBox createReplaceSection() {
        VBox section = new VBox(8);
        section.setStyle("-fx-background-color: white; -fx-padding: 10; " +
                        "-fx-border-color: #ddd; -fx-border-radius: 5;");

        Label titleLabel = new Label("替换设置");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // 替换输入行
        HBox replaceRow = new HBox(10);
        replaceRow.setAlignment(Pos.CENTER_LEFT);

        Label replaceLabel = new Label("替换为:");
        replaceLabel.setPrefWidth(80);

        replaceField = new TextField();
        replaceField.setPrefWidth(400);
        replaceField.setPromptText("输入替换内容");

        Button replaceAllButton = new Button("替换全部");
        replaceAllButton.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white;");

        Button replaceSelectedButton = new Button("替换选中");
        replaceSelectedButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");

        replaceRow.getChildren().addAll(replaceLabel, replaceField,
                                        replaceAllButton, replaceSelectedButton);

        // 条件行
        HBox conditionRow = new HBox(10);
        conditionRow.setAlignment(Pos.CENTER_LEFT);

        Label conditionLabel = new Label("替换条件:");
        conditionLabel.setPrefWidth(80);

        conditionField = new TextField();
        conditionField.setPrefWidth(400);
        conditionField.setPromptText("例如: level > 50 或 type = 'weapon'");

        Button helpButton = new Button("?");
        helpButton.setStyle("-fx-background-radius: 50%;");
        helpButton.setTooltip(new Tooltip(
            "条件语法:\n" +
            "- 比较: level > 50, attack >= 100\n" +
            "- 相等: type = 'weapon', name == '剑'\n" +
            "- 包含: description contains '传说'"
        ));

        conditionRow.getChildren().addAll(conditionLabel, conditionField, helpButton);

        // 替换选项行
        HBox optionRow = new HBox(20);
        optionRow.setAlignment(Pos.CENTER_LEFT);
        optionRow.setPadding(new Insets(5, 0, 0, 85));

        previewCheck = new CheckBox("预览模式");
        previewCheck.setSelected(true);
        backupCheck = new CheckBox("备份原文件");
        backupCheck.setSelected(true);

        optionRow.getChildren().addAll(previewCheck, backupCheck);

        section.getChildren().addAll(titleLabel, replaceRow, conditionRow, optionRow);

        // 事件处理
        replaceAllButton.setOnAction(e -> performReplace(true));
        replaceSelectedButton.setOnAction(e -> performReplace(false));

        return section;
    }

    private VBox createResultSection() {
        VBox section = new VBox(5);
        section.setStyle("-fx-background-color: white; -fx-padding: 10; " +
                        "-fx-border-color: #ddd; -fx-border-radius: 5;");

        Label titleLabel = new Label("搜索结果");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        // 工具栏
        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button selectAllBtn = new Button("全选");
        Button deselectAllBtn = new Button("取消全选");
        Button invertSelectionBtn = new Button("反选");
        Label countLabel = new Label("共 0 个结果");

        toolbar.getChildren().addAll(selectAllBtn, deselectAllBtn,
                                     invertSelectionBtn, new Separator(),
                                     countLabel);

        // 结果表格
        resultTable = new TableView<>();
        resultTable.setPrefHeight(300);
        resultTable.setItems(searchResults);

        TableColumn<SearchResultItem, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setCellValueFactory(cellData -> cellData.getValue().getSelected().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(50);
        selectCol.setEditable(true);

        TableColumn<SearchResultItem, String> fileCol = new TableColumn<>("文件");
        fileCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getFile()));
        fileCol.setPrefWidth(200);

        TableColumn<SearchResultItem, String> pathCol = new TableColumn<>("元素路径");
        pathCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getPath()));
        pathCol.setPrefWidth(300);

        TableColumn<SearchResultItem, String> matchCol = new TableColumn<>("匹配内容");
        matchCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getMatch()));
        matchCol.setPrefWidth(200);

        TableColumn<SearchResultItem, String> contextCol = new TableColumn<>("上下文");
        contextCol.setCellValueFactory(cellData ->
            new SimpleStringProperty(cellData.getValue().getContext()));
        contextCol.setPrefWidth(400);

        resultTable.getColumns().addAll(selectCol, fileCol, pathCol, matchCol, contextCol);
        resultTable.setEditable(true);

        section.getChildren().addAll(titleLabel, toolbar, resultTable);

        // 事件处理
        selectAllBtn.setOnAction(e ->
            searchResults.forEach(item -> item.getSelected().setSelected(true)));

        deselectAllBtn.setOnAction(e ->
            searchResults.forEach(item -> item.getSelected().setSelected(false)));

        invertSelectionBtn.setOnAction(e ->
            searchResults.forEach(item ->
                item.getSelected().setSelected(!item.getSelected().isSelected())));

        searchResults.addListener((javafx.collections.ListChangeListener<SearchResultItem>) c ->
            countLabel.setText("共 " + searchResults.size() + " 个结果"));

        return section;
    }

    private VBox createPreviewSection() {
        VBox section = new VBox(5);
        section.setStyle("-fx-background-color: white; -fx-padding: 10; " +
                        "-fx-border-color: #ddd; -fx-border-radius: 5;");

        Label titleLabel = new Label("预览");
        titleLabel.setStyle("-fx-font-size: 16; -fx-font-weight: bold;");

        previewArea = new TextArea();
        previewArea.setPrefHeight(150);
        previewArea.setEditable(false);
        previewArea.setStyle("-fx-font-family: 'Courier New';");

        section.getChildren().addAll(titleLabel, previewArea);

        return section;
    }

    private HBox createStatusBar() {
        HBox statusBar = new HBox(10);
        statusBar.setAlignment(Pos.CENTER_LEFT);
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #e0e0e0;");

        statusLabel = new Label("就绪");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button closeButton = new Button("关闭");
        closeButton.setOnAction(e -> close());

        statusBar.getChildren().addAll(statusLabel, progressBar, spacer, closeButton);

        return statusBar;
    }

    private void setupEventHandlers() {
        // 选中结果时显示预览
        resultTable.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldVal, newVal) -> {
                if (newVal != null) {
                    showPreview(newVal);
                }
            });

        // 关闭时清理资源
        setOnCloseRequest(e -> {
            searchEngine.shutdown();
        });
    }

    private void performSearch() {
        String searchText = searchField.getText().trim();
        if (searchText.isEmpty()) {
            showAlert("请输入搜索内容", Alert.AlertType.WARNING);
            return;
        }

        searchResults.clear();
        progressBar.setVisible(true);
        statusLabel.setText("正在搜索...");

        Task<List<SearchResult>> searchTask = new Task<List<SearchResult>>() {
            @Override
            protected List<SearchResult> call() throws Exception {
                List<Path> searchPaths = getSearchPaths();
                GlobalSearchEngine.SearchMode mode = searchModeCombo.getValue();

                List<SearchResult> results = new ArrayList<>();
                AtomicInteger processed = new AtomicInteger(0);

                for (Path path : searchPaths) {
                    if (isCancelled()) break;

                    List<SearchResult> pathResults = searchEngine.searchInFiles(
                        Collections.singletonList(path), searchText, mode);
                    results.addAll(pathResults);

                    int current = processed.incrementAndGet();
                    updateProgress(current, searchPaths.size());
                    updateMessage("已搜索 " + current + "/" + searchPaths.size() + " 个文件");
                }

                return results;
            }

            @Override
            protected void succeeded() {
                List<SearchResult> results = getValue();
                Platform.runLater(() -> {
                    searchResults.addAll(results.stream()
                        .map(SearchResultItem::new)
                        .collect(Collectors.toList()));

                    progressBar.setVisible(false);
                    statusLabel.setText("搜索完成，找到 " + results.size() + " 个结果");

                    if (results.isEmpty()) {
                        showAlert("未找到匹配的内容", Alert.AlertType.INFORMATION);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("搜索失败");
                    Throwable ex = getException();
                    log.error("Search failed", ex);
                    showAlert("搜索失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        progressBar.progressProperty().bind(searchTask.progressProperty());
        statusLabel.textProperty().bind(searchTask.messageProperty());

        Thread searchThread = new Thread(searchTask);
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void performReplace(boolean replaceAll) {
        String replaceText = replaceField.getText();
        if (searchResults.isEmpty()) {
            showAlert("请先执行搜索", Alert.AlertType.WARNING);
            return;
        }

        // 获取要替换的项
        List<SearchResult> toReplace = searchResults.stream()
            .filter(item -> replaceAll || item.getSelected().isSelected())
            .map(SearchResultItem::getSearchResult)
            .collect(Collectors.toList());

        if (toReplace.isEmpty()) {
            showAlert("请选择要替换的项", Alert.AlertType.WARNING);
            return;
        }

        // 创建替换选项
        ReplaceOptions options = new ReplaceOptions();
        options.setCaseSensitive(caseSensitiveCheck.isSelected());
        options.setWholeWord(wholeWordCheck.isSelected());
        options.setUseRegex(regexCheck.isSelected());
        options.setPreview(previewCheck.isSelected());
        options.setBackup(backupCheck.isSelected());
        options.setCondition(conditionField.getText());

        progressBar.setVisible(true);
        statusLabel.setText("正在执行替换...");

        Task<Map<String, Integer>> replaceTask = new Task<Map<String, Integer>>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                return searchEngine.replaceAll(toReplace, replaceText, options);
            }

            @Override
            protected void succeeded() {
                Map<String, Integer> result = getValue();
                Platform.runLater(() -> {
                    progressBar.setVisible(false);

                    int totalFiles = result.size();
                    int totalReplacements = result.values().stream()
                        .mapToInt(Integer::intValue).sum();

                    statusLabel.setText(String.format("替换完成: %d 个文件，共 %d 处",
                                                     totalFiles, totalReplacements));

                    if (options.isPreview()) {
                        showReplacePreview(result);
                    } else {
                        showAlert(String.format("成功替换 %d 个文件中的 %d 处内容",
                                              totalFiles, totalReplacements),
                                Alert.AlertType.INFORMATION);
                    }
                });
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    progressBar.setVisible(false);
                    statusLabel.setText("替换失败");
                    Throwable ex = getException();
                    log.error("Replace failed", ex);
                    showAlert("替换失败: " + ex.getMessage(), Alert.AlertType.ERROR);
                });
            }
        };

        Thread replaceThread = new Thread(replaceTask);
        replaceThread.setDaemon(true);
        replaceThread.start();
    }

    private List<Path> getSearchPaths() {
        List<Path> paths = new ArrayList<>();
        String scope = searchScopeCombo.getValue();

        try {
            switch (scope) {
                case "服务端XML":
                    paths.addAll(getAllXmlFiles("D:\\workspace\\dbxmlTool\\data\\DATA\\SVR_DATA"));
                    break;
                case "客户端XML":
                    paths.addAll(getAllXmlFiles("D:\\workspace\\dbxmlTool\\data\\DATA\\CLT_DATA"));
                    break;
                case "所有XML":
                    paths.addAll(getAllXmlFiles("D:\\workspace\\dbxmlTool\\data\\DATA"));
                    break;
                case "自定义路径":
                    String customPath = customPathField.getText();
                    if (!customPath.isEmpty()) {
                        paths.addAll(getAllXmlFiles(customPath));
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to get search paths", e);
        }

        return paths;
    }

    private List<Path> getAllXmlFiles(String directory) throws Exception {
        Path dir = Paths.get(directory);
        if (!java.nio.file.Files.exists(dir)) {
            return new ArrayList<>();
        }

        return java.nio.file.Files.walk(dir)
            .filter(path -> path.toString().toLowerCase().endsWith(".xml"))
            .collect(Collectors.toList());
    }

    private void showPreview(SearchResultItem item) {
        SearchResult result = item.getSearchResult();
        StringBuilder preview = new StringBuilder();

        preview.append("文件: ").append(result.getFilePath()).append("\n");
        preview.append("路径: ").append(result.getElementPath()).append("\n");
        preview.append("行号: ").append(result.getLineNumber()).append("\n");
        preview.append("\n");

        // 显示属性
        if (result.getAttributes() != null && !result.getAttributes().isEmpty()) {
            preview.append("属性:\n");
            result.getAttributes().forEach((k, v) ->
                preview.append("  ").append(k).append(" = ").append(v).append("\n"));
            preview.append("\n");
        }

        // 显示关联文件
        if (result.getRelatedFiles() != null && !result.getRelatedFiles().isEmpty()) {
            preview.append("关联文件:\n");
            result.getRelatedFiles().forEach(file ->
                preview.append("  - ").append(file).append("\n"));
            preview.append("\n");
        }

        // 显示匹配内容
        preview.append("匹配内容:\n");
        preview.append(result.getMatchedText());

        previewArea.setText(preview.toString());
    }

    private void showReplacePreview(Map<String, Integer> result) {
        StringBuilder preview = new StringBuilder();
        preview.append("替换预览（尚未实际执行）:\n");
        for (int i = 0; i < 50; i++) preview.append("=");
        preview.append("\n\n");

        result.forEach((file, count) -> {
            preview.append("文件: ").append(new File(file).getName()).append("\n");
            preview.append("  替换数量: ").append(count).append(" 处\n\n");
        });

        preview.append("\n总计: ")
               .append(result.size()).append(" 个文件, ")
               .append(result.values().stream().mapToInt(Integer::intValue).sum())
               .append(" 处替换\n");

        previewArea.setText(preview.toString());

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("替换预览");
        alert.setHeaderText("确认执行替换?");
        alert.setContentText("将在 " + result.size() + " 个文件中执行替换操作");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                previewCheck.setSelected(false);
                performReplace(true);
            }
        });
    }

    private void showAlert(String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(type == Alert.AlertType.ERROR ? "错误" : "提示");
        alert.setContentText(message);
        alert.showAndWait();
    }
}