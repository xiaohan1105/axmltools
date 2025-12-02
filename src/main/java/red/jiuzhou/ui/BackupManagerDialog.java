package red.jiuzhou.ui;

import javafx.application.Platform;
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
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import lombok.Data;
import red.jiuzhou.safety.DataSafetyManager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 备份管理器对话框
 * 提供备份文件的浏览、管理、恢复和验证功能
 *
 * 功能特点：
 * - 备份浏览：列出所有备份文件，支持搜索和筛选
 * - 备份恢复：恢复单个或批量备份文件
 * - 备份验证：验证备份文件完整性
 * - 备份清理：删除旧备份，释放空间
 * - 备份导出：导出备份到其他位置
 * - 版本对比：对比不同版本的备份内容
 * - 空间管理：显示备份占用空间，设置保留策略
 */
public class BackupManagerDialog extends Stage {

    private final DataSafetyManager safetyManager;

    // UI组件
    private TableView<BackupFileInfo> backupTable;
    private ObservableList<BackupFileInfo> backupData;

    private TextField searchField;
    private ComboBox<String> fileFilter;
    private DatePicker startDatePicker;
    private DatePicker endDatePicker;
    private Spinner<Integer> retentionSpinner;

    private Label totalCountLabel;
    private Label totalSizeLabel;
    private Label selectedCountLabel;

    private TextArea detailArea;
    private ProgressBar operationProgress;
    private Label progressLabel;

    /**
     * 备份文件信息
     */
    @Data
    public static class BackupFileInfo {
        private boolean selected;
        private String originalFile;
        private String backupFile;
        private LocalDateTime backupTime;
        private long fileSize;
        private String checksum;
        private boolean verified;
        private String status;

        public String getBackupTimeStr() {
            return backupTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        }

        public String getFileSizeStr() {
            return formatFileSize(fileSize);
        }

        public String getStatusIcon() {
            if (verified) {
                return "✓ 已验证";
            } else {
                return "? 未验证";
            }
        }

        private static String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }

    public BackupManagerDialog(Stage owner) {
        try {
            this.safetyManager = new DataSafetyManager();
        } catch (IOException e) {
            throw new RuntimeException("初始化数据安全管理器失败", e);
        }

        initModality(Modality.APPLICATION_MODAL);
        initOwner(owner);
        setTitle("💾 备份管理器");
        setResizable(true);

        initUI();
        loadBackupFiles();
    }

    private void initUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #f5f5f5;");

        // 顶部标题栏
        VBox header = createHeader();
        root.setTop(header);

        // 中间主内容区
        BorderPane centerPane = new BorderPane();
        centerPane.setPadding(new Insets(15));

        // 左侧：备份文件列表
        VBox leftPane = createBackupListPane();
        BorderPane.setMargin(leftPane, new Insets(0, 10, 0, 0));
        centerPane.setLeft(leftPane);

        // 右侧：详情和操作
        VBox rightPane = createDetailPane();
        centerPane.setRight(rightPane);

        root.setCenter(centerPane);

        // 底部：状态和操作栏
        VBox bottomPane = createBottomPane();
        root.setBottom(bottomPane);

        Scene scene = new Scene(root, 1100, 750);
        setScene(scene);
    }

    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setStyle("-fx-background-color: linear-gradient(to right, #11998e 0%, #38ef7d 100%);");
        header.setPadding(new Insets(20));

        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);

        Label icon = new Label("💾");
        icon.setFont(Font.font("Arial", 36));

        VBox textBox = new VBox(3);
        Label title = new Label("备份管理器");
        title.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        title.setTextFill(Color.WHITE);

        Label subtitle = new Label("管理和恢复数据文件备份");
        subtitle.setFont(Font.font("Arial", 13));
        subtitle.setTextFill(Color.web("#e0e0e0"));

        textBox.getChildren().addAll(title, subtitle);
        titleBox.getChildren().addAll(icon, textBox);

        // 统计信息
        HBox statsBox = new HBox(30);
        statsBox.setAlignment(Pos.CENTER_LEFT);
        statsBox.setPadding(new Insets(10, 0, 0, 0));

        VBox countBox = new VBox(3);
        countBox.setAlignment(Pos.CENTER_LEFT);
        Label countTitle = new Label("备份文件");
        countTitle.setFont(Font.font("Arial", 11));
        countTitle.setTextFill(Color.web("#d0d0d0"));
        totalCountLabel = new Label("0");
        totalCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        totalCountLabel.setTextFill(Color.WHITE);
        countBox.getChildren().addAll(countTitle, totalCountLabel);

        VBox sizeBox = new VBox(3);
        sizeBox.setAlignment(Pos.CENTER_LEFT);
        Label sizeTitle = new Label("总大小");
        sizeTitle.setFont(Font.font("Arial", 11));
        sizeTitle.setTextFill(Color.web("#d0d0d0"));
        totalSizeLabel = new Label("0 B");
        totalSizeLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        totalSizeLabel.setTextFill(Color.WHITE);
        sizeBox.getChildren().addAll(sizeTitle, totalSizeLabel);

        VBox selectedBox = new VBox(3);
        selectedBox.setAlignment(Pos.CENTER_LEFT);
        Label selectedTitle = new Label("已选择");
        selectedTitle.setFont(Font.font("Arial", 11));
        selectedTitle.setTextFill(Color.web("#d0d0d0"));
        selectedCountLabel = new Label("0");
        selectedCountLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        selectedCountLabel.setTextFill(Color.WHITE);
        selectedBox.getChildren().addAll(selectedTitle, selectedCountLabel);

        statsBox.getChildren().addAll(countBox, sizeBox, selectedBox);

        header.getChildren().addAll(titleBox, statsBox);
        return header;
    }

    private VBox createBackupListPane() {
        VBox pane = new VBox(10);
        pane.setPrefWidth(680);

        // 搜索和过滤区域
        VBox filterPane = new VBox(10);
        filterPane.setPadding(new Insets(15));
        filterPane.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label filterTitle = new Label("🔍 搜索和筛选");
        filterTitle.setFont(Font.font("Arial", FontWeight.BOLD, 13));

        // 第一行：搜索和文件过滤
        HBox row1 = new HBox(10);
        row1.setAlignment(Pos.CENTER_LEFT);

        Label searchLabel = new Label("搜索:");
        searchField = new TextField();
        searchField.setPromptText("搜索原始文件名...");
        searchField.setPrefWidth(250);
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());

        Label fileLabel = new Label("文件:");
        fileFilter = new ComboBox<>();
        fileFilter.setPrefWidth(150);
        fileFilter.setOnAction(e -> applyFilters());

        row1.getChildren().addAll(searchLabel, searchField, fileLabel, fileFilter);

        // 第二行：日期范围
        HBox row2 = new HBox(10);
        row2.setAlignment(Pos.CENTER_LEFT);

        Label dateLabel = new Label("日期范围:");
        startDatePicker = new DatePicker();
        startDatePicker.setPromptText("开始日期");
        startDatePicker.setPrefWidth(140);
        startDatePicker.setOnAction(e -> applyFilters());

        Label toLabel = new Label("至");
        endDatePicker = new DatePicker();
        endDatePicker.setPromptText("结束日期");
        endDatePicker.setPrefWidth(140);
        endDatePicker.setOnAction(e -> applyFilters());

        Button clearFilterBtn = new Button("清除筛选");
        clearFilterBtn.setOnAction(e -> clearFilters());

        row2.getChildren().addAll(dateLabel, startDatePicker, toLabel, endDatePicker, clearFilterBtn);

        filterPane.getChildren().addAll(filterTitle, row1, row2);

        // 备份文件表格
        backupData = FXCollections.observableArrayList();
        backupTable = new TableView<>(backupData);
        backupTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        backupTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<BackupFileInfo, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleBooleanProperty(data.getValue().isSelected())
        );
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setEditable(true);
        selectCol.setPrefWidth(50);
        selectCol.setOnEditCommit(event -> {
            BackupFileInfo info = event.getRowValue();
            info.setSelected(event.getNewValue());
            updateSelectedCount();
        });

        TableColumn<BackupFileInfo, String> fileCol = new TableColumn<>("原始文件");
        fileCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getOriginalFile())
        );
        fileCol.setPrefWidth(200);

        TableColumn<BackupFileInfo, String> timeCol = new TableColumn<>("备份时间");
        timeCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getBackupTimeStr())
        );
        timeCol.setPrefWidth(150);

        TableColumn<BackupFileInfo, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getFileSizeStr())
        );
        sizeCol.setPrefWidth(80);

        TableColumn<BackupFileInfo, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(data ->
            new javafx.beans.property.SimpleStringProperty(data.getValue().getStatusIcon())
        );
        statusCol.setPrefWidth(90);

        backupTable.setEditable(true);
        backupTable.getColumns().addAll(selectCol, fileCol, timeCol, sizeCol, statusCol);

        // 选择监听
        backupTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                showBackupDetails(newSel);
            }
        });

        VBox.setVgrow(backupTable, Priority.ALWAYS);

        // 批量操作按钮
        HBox batchOpsBox = new HBox(10);
        batchOpsBox.setAlignment(Pos.CENTER_LEFT);
        batchOpsBox.setPadding(new Insets(10, 0, 0, 0));

        Button selectAllBtn = new Button("全选");
        selectAllBtn.setOnAction(e -> selectAll(true));

        Button deselectAllBtn = new Button("取消全选");
        deselectAllBtn.setOnAction(e -> selectAll(false));

        Button verifySelectedBtn = new Button("✓ 验证选中");
        verifySelectedBtn.setOnAction(e -> verifySelected());

        Button deleteSelectedBtn = new Button("🗑️ 删除选中");
        deleteSelectedBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
        deleteSelectedBtn.setOnAction(e -> deleteSelected());

        batchOpsBox.getChildren().addAll(selectAllBtn, deselectAllBtn, verifySelectedBtn, deleteSelectedBtn);

        pane.getChildren().addAll(filterPane, backupTable, batchOpsBox);
        return pane;
    }

    private VBox createDetailPane() {
        VBox pane = new VBox(10);
        pane.setPrefWidth(350);

        // 详情显示
        VBox detailBox = new VBox(10);
        detailBox.setPadding(new Insets(15));
        detailBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label detailTitle = new Label("📄 备份详情");
        detailTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        detailArea = new TextArea();
        detailArea.setEditable(false);
        detailArea.setWrapText(true);
        detailArea.setPrefHeight(250);
        detailArea.setStyle("-fx-font-family: 'Courier New'; -fx-font-size: 11;");
        detailArea.setPromptText("选择一个备份文件查看详情");

        VBox.setVgrow(detailArea, Priority.ALWAYS);
        detailBox.getChildren().addAll(detailTitle, detailArea);

        // 单个文件操作
        VBox operationsBox = new VBox(10);
        operationsBox.setPadding(new Insets(15));
        operationsBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label opsTitle = new Label("⚙️ 操作");
        opsTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        Button restoreBtn = new Button("🔄 恢复此备份");
        restoreBtn.setPrefWidth(300);
        restoreBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 10;");
        restoreBtn.setOnAction(e -> restoreSelected());

        Button verifyBtn = new Button("✓ 验证完整性");
        verifyBtn.setPrefWidth(300);
        verifyBtn.setOnAction(e -> verifySelected());

        Button exportBtn = new Button("📤 导出备份");
        exportBtn.setPrefWidth(300);
        exportBtn.setOnAction(e -> exportSelected());

        Button compareBtn = new Button("🔀 版本对比");
        compareBtn.setPrefWidth(300);
        compareBtn.setOnAction(e -> compareVersions());

        Button deleteBtn = new Button("🗑️ 删除备份");
        deleteBtn.setPrefWidth(300);
        deleteBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white;");
        deleteBtn.setOnAction(e -> deleteSelected());

        operationsBox.getChildren().addAll(opsTitle, restoreBtn, verifyBtn, exportBtn, compareBtn, deleteBtn);

        // 备份策略
        VBox policyBox = new VBox(10);
        policyBox.setPadding(new Insets(15));
        policyBox.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-radius: 5;");

        Label policyTitle = new Label("📋 备份策略");
        policyTitle.setFont(Font.font("Arial", FontWeight.BOLD, 14));

        HBox retentionBox = new HBox(10);
        retentionBox.setAlignment(Pos.CENTER_LEFT);
        Label retentionLabel = new Label("保留版本数:");
        retentionSpinner = new Spinner<>(1, 50, 10);
        retentionSpinner.setPrefWidth(80);
        retentionSpinner.setEditable(true);
        retentionBox.getChildren().addAll(retentionLabel, retentionSpinner);

        Button applyPolicyBtn = new Button("应用策略");
        applyPolicyBtn.setPrefWidth(140);
        applyPolicyBtn.setOnAction(e -> applyRetentionPolicy());

        Button cleanOldBtn = new Button("🧹 清理旧备份");
        cleanOldBtn.setPrefWidth(140);
        cleanOldBtn.setOnAction(e -> cleanOldBackups());

        HBox policyBtnBox = new HBox(10);
        policyBtnBox.getChildren().addAll(applyPolicyBtn, cleanOldBtn);

        policyBox.getChildren().addAll(policyTitle, retentionBox, policyBtnBox);

        VBox.setVgrow(detailBox, Priority.ALWAYS);
        pane.getChildren().addAll(detailBox, operationsBox, policyBox);
        return pane;
    }

    private VBox createBottomPane() {
        VBox bottom = new VBox(10);
        bottom.setPadding(new Insets(15));
        bottom.setStyle("-fx-background-color: white; -fx-border-color: #dee2e6; -fx-border-width: 1 0 0 0;");

        // 进度条
        HBox progressBox = new HBox(10);
        progressBox.setAlignment(Pos.CENTER_LEFT);
        progressBox.setVisible(false);
        progressBox.setManaged(false);

        Label progressIcon = new Label("⏳");
        progressIcon.setFont(Font.font("Arial", 16));

        operationProgress = new ProgressBar(0);
        operationProgress.setPrefWidth(400);

        progressLabel = new Label("准备中...");
        progressLabel.setFont(Font.font("Arial", 12));

        progressBox.getChildren().addAll(progressIcon, operationProgress, progressLabel);

        // 按钮栏
        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);

        Button refreshBtn = new Button("🔄 刷新列表");
        refreshBtn.setOnAction(e -> loadBackupFiles());

        Button exportAllBtn = new Button("📦 批量导出");
        exportAllBtn.setOnAction(e -> exportAll());

        Button closeBtn = new Button("关闭");
        closeBtn.setPrefWidth(100);
        closeBtn.setOnAction(e -> close());

        buttonBox.getChildren().addAll(refreshBtn, exportAllBtn, closeBtn);

        bottom.getChildren().addAll(progressBox, buttonBox);
        return bottom;
    }

    /**
     * 加载备份文件
     */
    private void loadBackupFiles() {
        Task<List<BackupFileInfo>> task = new Task<List<BackupFileInfo>>() {
            @Override
            protected List<BackupFileInfo> call() throws Exception {
                List<BackupFileInfo> backups = new ArrayList<>();

                Path backupDir = Paths.get("backup");
                if (!Files.exists(backupDir)) {
                    return backups;
                }

                Files.walk(backupDir)
                    .filter(p -> p.toString().endsWith(".bak"))
                    .forEach(backupFile -> {
                        try {
                            BackupFileInfo info = createBackupInfo(backupFile);
                            if (info != null) {
                                backups.add(info);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                // 按时间倒序排序
                backups.sort((a, b) -> b.getBackupTime().compareTo(a.getBackupTime()));

                return backups;
            }
        };

        task.setOnSucceeded(e -> {
            List<BackupFileInfo> backups = task.getValue();
            backupData.setAll(backups);
            updateStatistics();
            populateFileFilter();
        });

        task.setOnFailed(e -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("加载失败");
            alert.setHeaderText("加载备份文件失败");
            alert.setContentText(task.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(task).start();
    }

    /**
     * 创建备份文件信息
     */
    private BackupFileInfo createBackupInfo(Path backupFile) throws IOException {
        BackupFileInfo info = new BackupFileInfo();

        // 解析文件名获取原始文件和时间
        String fileName = backupFile.getFileName().toString();
        // 格式: originalFile.20250111_140523.bak
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot == -1) return null;

        String withoutBak = fileName.substring(0, lastDot);
        int secondLastDot = withoutBak.lastIndexOf('.');
        if (secondLastDot == -1) return null;

        String originalFile = withoutBak.substring(0, secondLastDot);
        String timeStr = withoutBak.substring(secondLastDot + 1);

        info.setOriginalFile(originalFile);
        info.setBackupFile(backupFile.toString());

        // 解析时间
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            info.setBackupTime(LocalDateTime.parse(timeStr, formatter));
        } catch (Exception e) {
            // 如果解析失败，使用文件修改时间
            BasicFileAttributes attrs = Files.readAttributes(backupFile, BasicFileAttributes.class);
            info.setBackupTime(LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(),
                ZoneId.systemDefault()
            ));
        }

        // 文件大小
        info.setFileSize(Files.size(backupFile));

        // 校验和（从备份元数据文件读取，如果存在）
        Path checksumFile = Paths.get(backupFile.toString() + ".checksum");
        if (Files.exists(checksumFile)) {
            info.setChecksum(new String(Files.readAllBytes(checksumFile)).trim());
        }

        info.setVerified(false);
        info.setStatus("正常");
        info.setSelected(false);

        return info;
    }

    /**
     * 更新统计信息
     */
    private void updateStatistics() {
        int totalCount = backupData.size();
        long totalSize = backupData.stream().mapToLong(BackupFileInfo::getFileSize).sum();

        totalCountLabel.setText(String.valueOf(totalCount));
        totalSizeLabel.setText(BackupFileInfo.formatFileSize(totalSize));
    }

    /**
     * 更新选中数量
     */
    private void updateSelectedCount() {
        long selectedCount = backupData.stream().filter(BackupFileInfo::isSelected).count();
        selectedCountLabel.setText(String.valueOf(selectedCount));
    }

    /**
     * 填充文件过滤器
     */
    private void populateFileFilter() {
        Set<String> files = backupData.stream()
            .map(BackupFileInfo::getOriginalFile)
            .collect(Collectors.toSet());

        List<String> sortedFiles = new ArrayList<>(files);
        sortedFiles.sort(String::compareTo);

        fileFilter.getItems().clear();
        fileFilter.getItems().add("全部文件");
        fileFilter.getItems().addAll(sortedFiles);
        fileFilter.setValue("全部文件");
    }

    /**
     * 应用过滤器
     */
    private void applyFilters() {
        // TODO: 实现过滤逻辑
    }

    /**
     * 清除过滤器
     */
    private void clearFilters() {
        searchField.clear();
        fileFilter.setValue("全部文件");
        startDatePicker.setValue(null);
        endDatePicker.setValue(null);
        applyFilters();
    }

    /**
     * 全选/取消全选
     */
    private void selectAll(boolean select) {
        backupData.forEach(info -> info.setSelected(select));
        backupTable.refresh();
        updateSelectedCount();
    }

    /**
     * 显示备份详情
     */
    private void showBackupDetails(BackupFileInfo info) {
        StringBuilder sb = new StringBuilder();
        sb.append("原始文件:\n  ").append(info.getOriginalFile()).append("\n\n");
        sb.append("备份文件:\n  ").append(info.getBackupFile()).append("\n\n");
        sb.append("备份时间:\n  ").append(info.getBackupTimeStr()).append("\n\n");
        sb.append("文件大小:\n  ").append(info.getFileSizeStr()).append("\n\n");

        if (info.getChecksum() != null) {
            sb.append("校验和:\n  ").append(info.getChecksum()).append("\n\n");
        }

        sb.append("验证状态:\n  ").append(info.getStatusIcon()).append("\n\n");
        sb.append("状态:\n  ").append(info.getStatus());

        detailArea.setText(sb.toString());
    }

    /**
     * 恢复选中的备份
     */
    private void restoreSelected() {
        List<BackupFileInfo> selected = backupTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            selected = backupData.stream()
                .filter(BackupFileInfo::isSelected)
                .collect(Collectors.toList());
        }

        if (selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("未选择");
            alert.setHeaderText("请选择要恢复的备份");
            alert.setContentText("请在表格中选择一个或多个备份文件");
            alert.showAndWait();
            return;
        }

        // 确认
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认恢复");
        confirm.setHeaderText("即将恢复 " + selected.size() + " 个备份文件");
        confirm.setContentText("此操作将覆盖当前文件，是否继续？");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        // 执行恢复
        performRestore(selected);
    }

    /**
     * 执行恢复操作
     */
    private void performRestore(List<BackupFileInfo> backups) {
        Task<Map<String, Boolean>> task = new Task<Map<String, Boolean>>() {
            @Override
            protected Map<String, Boolean> call() throws Exception {
                Map<String, Boolean> results = new HashMap<>();
                int total = backups.size();

                for (int i = 0; i < total; i++) {
                    BackupFileInfo backup = backups.get(i);
                    updateProgress(i, total);
                    updateMessage("恢复 " + backup.getOriginalFile() + "...");

                    try {
                        // 使用 DataSafetyManager 的恢复功能
                        String timeStr = backup.getBackupTime().format(
                            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                        );
                        safetyManager.restoreFromBackup(backup.getOriginalFile(), timeStr);
                        results.put(backup.getBackupFile(), true);
                    } catch (Exception e) {
                        e.printStackTrace();
                        results.put(backup.getBackupFile(), false);
                    }
                }

                updateProgress(total, total);
                updateMessage("恢复完成");
                return results;
            }
        };

        showProgress(task);

        task.setOnSucceeded(e -> {
            hideProgress();
            Map<String, Boolean> results = task.getValue();
            long successCount = results.values().stream().filter(b -> b).count();
            long failCount = results.size() - successCount;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("恢复完成");
            alert.setHeaderText("备份恢复完成");
            alert.setContentText(String.format(
                "成功: %d 个\n失败: %d 个", successCount, failCount
            ));
            alert.showAndWait();
        });

        task.setOnFailed(e -> {
            hideProgress();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("恢复失败");
            alert.setHeaderText("备份恢复失败");
            alert.setContentText(task.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(task).start();
    }

    /**
     * 验证选中的备份
     */
    private void verifySelected() {
        List<BackupFileInfo> selected = backupTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            selected = backupData.stream()
                .filter(BackupFileInfo::isSelected)
                .collect(Collectors.toList());
        }

        if (selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("未选择");
            alert.setHeaderText("请选择要验证的备份");
            alert.showAndWait();
            return;
        }

        performVerification(selected);
    }

    /**
     * 执行验证
     */
    private void performVerification(List<BackupFileInfo> backups) {
        Task<Map<BackupFileInfo, Boolean>> task = new Task<Map<BackupFileInfo, Boolean>>() {
            @Override
            protected Map<BackupFileInfo, Boolean> call() throws Exception {
                Map<BackupFileInfo, Boolean> results = new HashMap<>();
                int total = backups.size();

                for (int i = 0; i < total; i++) {
                    BackupFileInfo backup = backups.get(i);
                    updateProgress(i, total);
                    updateMessage("验证 " + backup.getOriginalFile() + "...");

                    try {
                        boolean valid = verifyBackupIntegrity(backup);
                        backup.setVerified(true);
                        backup.setStatus(valid ? "完整" : "损坏");
                        results.put(backup, valid);
                    } catch (Exception e) {
                        backup.setVerified(true);
                        backup.setStatus("验证失败");
                        results.put(backup, false);
                    }
                }

                updateProgress(total, total);
                updateMessage("验证完成");
                return results;
            }
        };

        showProgress(task);

        task.setOnSucceeded(e -> {
            hideProgress();
            backupTable.refresh();

            Map<BackupFileInfo, Boolean> results = task.getValue();
            long validCount = results.values().stream().filter(b -> b).count();
            long invalidCount = results.size() - validCount;

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("验证完成");
            alert.setHeaderText("备份验证完成");
            alert.setContentText(String.format(
                "有效: %d 个\n损坏: %d 个", validCount, invalidCount
            ));
            alert.showAndWait();
        });

        task.setOnFailed(e -> {
            hideProgress();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("验证失败");
            alert.setContentText(task.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(task).start();
    }

    /**
     * 验证备份完整性
     */
    private boolean verifyBackupIntegrity(BackupFileInfo backup) throws Exception {
        Path backupFile = Paths.get(backup.getBackupFile());
        if (!Files.exists(backupFile)) {
            return false;
        }

        // 计算当前校验和
        byte[] fileContent = Files.readAllBytes(backupFile);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(fileContent);
        String currentChecksum = bytesToHex(digest);

        // 与保存的校验和比较
        if (backup.getChecksum() != null) {
            return currentChecksum.equals(backup.getChecksum());
        }

        // 如果没有保存的校验和，保存当前的
        backup.setChecksum(currentChecksum);
        Path checksumFile = Paths.get(backup.getBackupFile() + ".checksum");
        Files.write(checksumFile, currentChecksum.getBytes());

        return true;
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 导出选中的备份
     */
    private void exportSelected() {
        List<BackupFileInfo> selected = backupTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            selected = backupData.stream()
                .filter(BackupFileInfo::isSelected)
                .collect(Collectors.toList());
        }

        if (selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("未选择");
            alert.setHeaderText("请选择要导出的备份");
            alert.showAndWait();
            return;
        }

        // 选择导出目录
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File exportDir = chooser.showDialog(this);

        if (exportDir == null) {
            return;
        }

        performExport(selected, exportDir.toPath());
    }

    /**
     * 执行导出
     */
    private void performExport(List<BackupFileInfo> backups, Path exportDir) {
        Task<Integer> task = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                int total = backups.size();
                int success = 0;

                for (int i = 0; i < total; i++) {
                    BackupFileInfo backup = backups.get(i);
                    updateProgress(i, total);
                    updateMessage("导出 " + backup.getOriginalFile() + "...");

                    try {
                        Path source = Paths.get(backup.getBackupFile());
                        Path target = exportDir.resolve(source.getFileName());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

                        // 同时复制校验和文件
                        Path checksumSource = Paths.get(backup.getBackupFile() + ".checksum");
                        if (Files.exists(checksumSource)) {
                            Path checksumTarget = exportDir.resolve(checksumSource.getFileName());
                            Files.copy(checksumSource, checksumTarget, StandardCopyOption.REPLACE_EXISTING);
                        }

                        success++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                updateProgress(total, total);
                updateMessage("导出完成");
                return success;
            }
        };

        showProgress(task);

        task.setOnSucceeded(e -> {
            hideProgress();
            int success = task.getValue();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("导出完成");
            alert.setHeaderText("备份导出完成");
            alert.setContentText("成功导出 " + success + " 个备份文件到:\n" + exportDir.toString());
            alert.showAndWait();
        });

        task.setOnFailed(e -> {
            hideProgress();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("导出失败");
            alert.setContentText(task.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(task).start();
    }

    /**
     * 删除选中的备份
     */
    private void deleteSelected() {
        List<BackupFileInfo> selected = backupTable.getSelectionModel().getSelectedItems();
        if (selected.isEmpty()) {
            selected = backupData.stream()
                .filter(BackupFileInfo::isSelected)
                .collect(Collectors.toList());
        }

        if (selected.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("未选择");
            alert.setHeaderText("请选择要删除的备份");
            alert.showAndWait();
            return;
        }

        // 确认删除
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认删除");
        confirm.setHeaderText("即将删除 " + selected.size() + " 个备份文件");
        confirm.setContentText("此操作不可恢复，是否继续？");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        performDelete(selected);
    }

    /**
     * 执行删除
     */
    private void performDelete(List<BackupFileInfo> backups) {
        Task<Integer> task = new Task<Integer>() {
            @Override
            protected Integer call() throws Exception {
                int success = 0;

                for (BackupFileInfo backup : backups) {
                    try {
                        Path backupFile = Paths.get(backup.getBackupFile());
                        Files.deleteIfExists(backupFile);

                        // 同时删除校验和文件
                        Path checksumFile = Paths.get(backup.getBackupFile() + ".checksum");
                        Files.deleteIfExists(checksumFile);

                        success++;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                return success;
            }
        };

        task.setOnSucceeded(e -> {
            int success = task.getValue();
            backupData.removeAll(backups);
            updateStatistics();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("删除完成");
            alert.setHeaderText("成功删除 " + success + " 个备份文件");
            alert.showAndWait();
        });

        task.setOnFailed(e -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("删除失败");
            alert.setContentText(task.getException().getMessage());
            alert.showAndWait();
        });

        new Thread(task).start();
    }

    /**
     * 版本对比
     */
    private void compareVersions() {
        List<BackupFileInfo> selected = backupTable.getSelectionModel().getSelectedItems();
        if (selected.size() != 2) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("选择错误");
            alert.setHeaderText("请选择两个备份进行对比");
            alert.setContentText("版本对比需要选择恰好两个备份文件");
            alert.showAndWait();
            return;
        }

        // TODO: 实现版本对比功能
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("版本对比");
        alert.setHeaderText("功能开发中");
        alert.setContentText("版本对比功能即将推出");
        alert.showAndWait();
    }

    /**
     * 应用保留策略
     */
    private void applyRetentionPolicy() {
        int retentionCount = retentionSpinner.getValue();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("确认应用策略");
        confirm.setHeaderText("应用备份保留策略");
        confirm.setContentText("将为每个文件保留最近 " + retentionCount + " 个版本，删除更早的备份。是否继续？");

        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        // TODO: 实现保留策略
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("策略应用");
        alert.setHeaderText("功能开发中");
        alert.setContentText("保留策略功能即将推出");
        alert.showAndWait();
    }

    /**
     * 清理旧备份
     */
    private void cleanOldBackups() {
        // TODO: 实现清理旧备份
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("清理旧备份");
        alert.setHeaderText("功能开发中");
        alert.setContentText("旧备份清理功能即将推出");
        alert.showAndWait();
    }

    /**
     * 批量导出
     */
    private void exportAll() {
        if (backupData.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("无备份");
            alert.setHeaderText("没有可导出的备份");
            alert.showAndWait();
            return;
        }

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出目录");
        File exportDir = chooser.showDialog(this);

        if (exportDir != null) {
            performExport(new ArrayList<>(backupData), exportDir.toPath());
        }
    }

    /**
     * 显示进度
     */
    private void showProgress(Task<?> task) {
        operationProgress.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());

        BorderPane root = (BorderPane) getScene().getRoot();
        VBox bottom = (VBox) root.getBottom();
        HBox progressBox = (HBox) bottom.getChildren().get(0);
        progressBox.setVisible(true);
        progressBox.setManaged(true);
    }

    /**
     * 隐藏进度
     */
    private void hideProgress() {
        BorderPane root = (BorderPane) getScene().getRoot();
        VBox bottom = (VBox) root.getBottom();
        HBox progressBox = (HBox) bottom.getChildren().get(0);
        progressBox.setVisible(false);
        progressBox.setManaged(false);

        operationProgress.progressProperty().unbind();
        progressLabel.textProperty().unbind();
        operationProgress.setProgress(0);
        progressLabel.setText("");
    }

    /**
     * 快速显示备份管理器
     */
    public static void showManager(Stage owner) {
        BackupManagerDialog dialog = new BackupManagerDialog(owner);
        dialog.show();
    }
}
