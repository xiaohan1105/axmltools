package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import red.jiuzhou.analysis.aion.IdNameResolver;
import red.jiuzhou.dbxml.*;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;
import red.jiuzhou.util.YmlConfigUtil;
import red.jiuzhou.xmltosql.XmlProcess;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
/**
 * @className: red.jiuzhou.ui.PaginatedTable.java
 * @description: 分页表格
 * @author: yanxq
 * @date:  2025-04-15 20:43
 * @version V1.0
 */
public class PaginatedTable{

    private static final Logger log = LoggerFactory.getLogger(PaginatedTable.class);
    private String tabName;

    private String tabFilePath;

    private TableView<Map<String, Object>> tableView;
    // 总行数
    private int totalRows;
    private Pagination pagination;
    private TextField searchField;
    private Label progressLabel;
    private  ProgressBar progressBar;

    private  String mapType;
    // 线程池
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private List<String> filterList;

    public VBox createVbox(TabPane tabPane, Tab tab) {
        long startTime = System.currentTimeMillis();
        log.info("start time {}", startTime);
        String tabName = tab.getText();
        try {
            filterList = new ArrayList<>();
            //System.out.println("tabPane::::" + tabPane);
            if(tabName == null || tabName.isEmpty()){
                return new VBox();
            }
            this.tabName = tabName;
            this.tabFilePath = tab.getUserData() + "";
            log.info("tabFilePath init: {}", tabFilePath);
            // 查询总记录数
            try{
                totalRows = DatabaseUtil.getTotalRowCount(tabName  + buildWhereClause());
            }catch (Exception e) {
                log.error("获取总行数失败: {}", e.getMessage());
            }

            // 创建 TableView
            tableView = new TableView<>();
            // 单元格选择
            tableView.getSelectionModel().setCellSelectionEnabled(true);
            // 多选
            tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

            tableView.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.C && event.isControlDown()) {
                    StringBuilder clipboardString = new StringBuilder();
                    ObservableList<TablePosition> posList = tableView.getSelectionModel().getSelectedCells();

                    int prevRow = -1;
                    for (TablePosition position : posList) {
                        int row = position.getRow();
                        int col = position.getColumn();
                        Object cell = tableView.getColumns().get(col).getCellData(row);

                        if (prevRow == row) {
                            clipboardString.append('\t'); // 同一行：列之间用 tab 分隔
                        } else if (prevRow != -1) {
                            clipboardString.append('\n'); // 不同行：换行
                        }
                        clipboardString.append(cell != null ? cell.toString() : "");
                        prevRow = row;
                    }

                    final ClipboardContent clipboardContent = new ClipboardContent();
                    clipboardContent.putString(clipboardString.toString());
                    Clipboard.getSystemClipboard().setContent(clipboardContent);
                }
            });

            createColumns();

            // 创建查询框和按钮
            searchField = new TextField();
            searchField.setPromptText("输入 ID 进行查询");

            Button searchButton = new Button("搜索");
            Button clearFilterButton = new Button("清除筛选");
            Button xmlToDb = new Button("xmlToDb");
            // --- 新增：带有字段选择的 XML 导入按钮 ---
            Button xmlToDbWithField = new Button("xmlToDbWithField");
            // 点击时调用
            xmlToDbWithField.setOnAction(e -> showColumnSelectionForXmlToDb(tab.getUserData() + ".xml"));

            //Button choosexmlToDb = new Button("chooseXmlToDb");
            Button dbToXml = new Button("dbToXml");
            Button ddlBun = new Button("DDL生成");
            if("world".equals(tabName)){
                ddlBun.setDisable(true);
            }
            ddlBun.setOnAction(e -> {
                String selectedFile = tab.getUserData() + ".xml";

                log.info("选择文件：{}", selectedFile);
                String sqlDdlFilePath = XmlProcess.parseXmlFile(selectedFile);
                //执行sql文件
                log.info("执行sql文件：{}", sqlDdlFilePath);
                try {
                    DatabaseUtil.executeSqlScript(sqlDdlFilePath);
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                log.info("生成DDL成功");
            });
            // 绑定查询功能
            searchButton.setOnAction(e -> searchById());
            clearFilterButton.setOnAction(e -> {
                filterList.clear();
                this.totalRows = DatabaseUtil.getTotalRowCount(tabName + buildWhereClause());
                int pageCount = (int) Math.ceil((double) totalRows / DatabaseUtil.ROWS_PER_PAGE);
                pagination.setPageCount(pageCount);
                pagination.setCurrentPageIndex(0);
                pagination.setPageFactory(this::createPage);
            });
            xmlToDb.setOnAction(e -> xmlToDb(tab.getUserData() + ".xml", null, null));
            dbToXml.setOnAction(e -> dbToXml());
            progressLabel = new Label("");

            // 按钮区域
            HBox searchBox = new HBox(10, searchField, searchButton, clearFilterButton, xmlToDb, xmlToDbWithField, dbToXml, ddlBun);
            searchBox.setPadding(new Insets(10));
            VBox progressBox = null;
            if("world".equals(tabName)){
                Path path = Paths.get(tabFilePath);
                mapType = path.getName(path.getNameCount() - 2).toString();
                log.info("msgType:{}", mapType);
            }
            progressBox = new VBox(5, progressLabel, new Region());
            progressBox.setPadding(new Insets(10));

            // 创建 Pagination 控件
            int pageCount = (int) Math.ceil((double) totalRows / DatabaseUtil.ROWS_PER_PAGE);
            pagination = new Pagination(pageCount, 0);
            pagination.setMaxPageIndicatorCount(10);
            pagination.setPageFactory(this::createPage);
            VBox rightControl = new VBox();
            rightControl.getChildren().add(tabPane);
            // 添加到右侧面板
            rightControl.getChildren().addAll(searchBox, progressBox);
            rightControl.getChildren().add(pagination);
            //VBox vBox = new VBox(searchBox, progressBox, pagination);
            log.info("time {}", System.currentTimeMillis() - startTime);
            return rightControl;
        } catch (Exception e) {
            showError(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void createColumns() {
        // 避免重复添加列
        tableView.getColumns().clear();
        List<Map<String, Object>> sampleData = null;
        try  {
            sampleData = DatabaseUtil.getJdbcTemplate()
                    .queryForList("SELECT * FROM " + tabName + " limit 15");
        } catch (Exception e) {
            log.error("获取数据失败:{}", e.getMessage());
            //showError(e.getMessage());
            //throw new RuntimeException(e);
        }

        if (sampleData != null && !sampleData.isEmpty()) {
            log.info("sampleData_size:{}", sampleData.size());
            Map<String, Object> firstRow = sampleData.get(0);

            // ID->NAME解析器实例
            final IdNameResolver idNameResolver = IdNameResolver.getInstance();

            // 创建列
            for (String columnName : firstRow.keySet()) {
                TableColumn<Map<String, Object>, Object> column = new TableColumn<>(columnName);
                column.setCellValueFactory(cellData ->
                        new ReadOnlyObjectWrapper<>(cellData.getValue().get(columnName))
                );

                // 检测是否是ID引用字段，添加NAME显示
                final boolean isIdField = idNameResolver.isIdField(columnName);
                if (isIdField) {
                    final String colName = columnName;
                    column.setCellFactory(col -> new TableCell<Map<String, Object>, Object>() {
                        @Override
                        protected void updateItem(Object item, boolean empty) {
                            super.updateItem(item, empty);
                            if (empty || item == null) {
                                setText(null);
                                setTooltip(null);
                            } else {
                                String idValue = item.toString();
                                String formattedValue = idNameResolver.formatIdWithName(colName, idValue);
                                setText(formattedValue);
                                // 如果有NAME，添加详细提示
                                if (!formattedValue.equals(idValue)) {
                                    String name = idNameResolver.resolveByField(colName, idValue);
                                    Tooltip tip = new Tooltip(
                                        "ID: " + idValue + "\n" +
                                        "名称: " + name + "\n" +
                                        "字段: " + colName
                                    );
                                    setTooltip(tip);
                                }
                            }
                        }
                    });
                    // 标记ID列（可选：改变标题样式）
                    column.setText(columnName + " \u279C");  // 添加箭头标记
                }

                // 右键菜单
                ContextMenu contextMenu = new ContextMenu();
                MenuItem showPopup = new MenuItem("查看");
                MenuItem clearFilter = new MenuItem("清除条件");
                showPopup.setOnAction(event -> showColumnDetails(columnName));
                clearFilter.setOnAction(event -> {
                    filterList.removeIf(item -> item.startsWith(columnName + "="));
                    this.totalRows = DatabaseUtil.getTotalRowCount(tabName + buildWhereClause());
                    int pageCount = (int) Math.ceil((double) totalRows / DatabaseUtil.ROWS_PER_PAGE);
                    pagination.setPageCount(pageCount);
                    pagination.setCurrentPageIndex(0);
                    pagination.setPageFactory(this::createPage);
                });

                contextMenu.getItems().add(showPopup);
                contextMenu.getItems().add(clearFilter);
                column.setContextMenu(contextMenu);

                tableView.getColumns().add(column);
            }

            // 使用 ObservableList 来绑定数据
            ObservableList<Map<String, Object>> observableData = FXCollections.observableArrayList(sampleData);

            // 更新 TableView 数据
            tableView.setItems(observableData);
        }
    }

    public String buildWhereClause() {
        if (filterList == null || filterList.isEmpty()) {
            return "";
        }

        StringJoiner joiner = new StringJoiner(" AND ");
        for (String condition : filterList) {
            joiner.add(condition);
        }

        return " WHERE " + joiner.toString();
    }
    /**
     * 弹出一个窗口，展示两列小列表
     */
    private void showColumnDetails(String columnName) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("列详情：" + columnName);

        // 创建 TableView
        TableView<EnumQuery.DataRow> tableView = new TableView<>();

        // "值" 列（文本类型）
        TableColumn<EnumQuery.DataRow, String> valueColumn = new TableColumn<>("值");
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue()));

        // "次数" 列（int 类型 + 可排序）
        TableColumn<EnumQuery.DataRow, Number> countColumn = new TableColumn<>("次数");
        countColumn.setCellValueFactory(data -> new SimpleIntegerProperty(data.getValue().getCount()));
        // 确保支持排序
        countColumn.setSortable(true);


        tableView.getColumns().addAll(valueColumn, countColumn);
        List<EnumQuery.DataRow> dataRows = new EnumQuery().getDataRows(tabName, columnName);
        ObservableList<EnumQuery.DataRow> observableList = FXCollections.observableArrayList(dataRows);

        tableView.setItems(observableList);

        // 添加双击事件监听
        tableView.setRowFactory(tv -> {
            TableRow<EnumQuery.DataRow> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    EnumQuery.DataRow rowData = row.getItem();
                    String condition = columnName + "='" + rowData.getValue() + "'";
                    filterList.removeIf(item -> item.startsWith(columnName + "="));
                    filterList.add(condition);
                    this.totalRows = DatabaseUtil.getTotalRowCount(tabName + buildWhereClause());
                    int pageCount = (int) Math.ceil((double) totalRows / DatabaseUtil.ROWS_PER_PAGE);
                    pagination.setPageCount(pageCount);
                    pagination.setCurrentPageIndex(0);
                    pagination.setPageFactory(this::createPage);
                    popupStage.close();
                }
            });
            return row;
        });

        // 布局
        VBox layout = new VBox(10, tableView);
        layout.setPadding(new Insets(10));

        Scene scene = new Scene(layout, 500, 350);
        popupStage.setScene(scene);
        popupStage.show();
    }


    /**
     * 创建某个分页的数据页面
     */
    private VBox createPage(int pageIndex) {
        List<Map<String, Object>> data = Collections.emptyList();
        try {
            data = DatabaseUtil.fetchPageData(tabName, pageIndex, buildWhereClause(), tabFilePath);

        } catch (Exception e) {
            log.error("获取数据失败:{}", e.getMessage());
            //showError(e.getMessage());
            //throw new RuntimeException(e);
        }

        // 更新 TableView 数据
        ObservableList<Map<String, Object>> observableData = FXCollections.observableArrayList(data);
        tableView.setItems(observableData);
        return new VBox(tableView);
    }

    /**
     * 按 ID 查询
     */
    private void searchById() {
        String id = searchField.getText().trim();
        if (id.isEmpty()) {
            showError("请输入有效的 ID 进行查询！");
            return;
        }

        String sql = "SELECT * FROM " + tabName + " WHERE id = ?";
        List<Map<String, Object>> result;
        try {
            result = DatabaseUtil.getJdbcTemplate().queryForList(sql, id);
            if (result.isEmpty()) {
                showError("未找到对应 ID 的数据！");
            }
        } catch (Exception e) {
            showError("查询失败: " + e.getMessage());
            return;
        }

        // 更新 TableView 数据
        ObservableList<Map<String, Object>> observableData = FXCollections.observableArrayList(result);
        tableView.setItems(observableData);
    }

    /**
     * 进度条导入数据（模拟）
     */
    private void xmlToDb(String filePath, List<String> selectedColumns, String aiModule) {
        tabIsExist();
        if ("world".equals(tabName)) {
            if (mapType == null || mapType.trim().isEmpty()) {
                throw new RuntimeException("请选择地图类型");
            }
        }
        Stage progressStage = createProgressDialog("正在导入XML至数据库...");

        executor.execute(() -> {
            updateProgress(0, "导入数据中...");
            XmlToDbGenerator xmlToDbGenerator = new XmlToDbGenerator(tabName, mapType, filePath, tabFilePath);

            AtomicReference<Throwable> threadException = new AtomicReference<>();
            // 启动线程
            Thread importThread = new Thread(() -> {
                try {
                    xmlToDbGenerator.xmlTodb(aiModule, selectedColumns);
                } catch (Throwable t) {
                    threadException.set(t);
                }
            });
            importThread.start();

            // 等待导入线程结束
            while (importThread.isAlive()) {
                try {
                    Thread.sleep(1000);
                    double progress = xmlToDbGenerator.getProgress();
                    updateProgress(progress, "导入进度: " + (progress * 100) + "%");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showErrorAndClose(progressStage, "线程被中断: " + e.getMessage());
                    return;
                }
            }

            // 导入线程已经结束，此时判断是否抛出异常
            if (threadException.get() != null) {
                String msg = threadException.get().getMessage();

                log.error("导入失败: " + JSONRecord.getErrorMsg(threadException.get()));
                Pattern colPattern = Pattern.compile("Data too long for column '(.+?)'");
                Pattern tablePattern = Pattern.compile("(?i)insert\\s+into\\s+[`]?([a-zA-Z0-9_]+)[`]?");

                Matcher colMatcher = colPattern.matcher(msg);
                Matcher tableMatcher = tablePattern.matcher(msg);
                if (colMatcher.find() && tableMatcher.find()) {
                    String columnName = colMatcher.group(1);
                    String realTableName = tableMatcher.group(1);
                    // 获取当前长度
                    String infoSchemaSql = "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
                    Integer oldLength = DatabaseUtil.getJdbcTemplate().queryForObject(infoSchemaSql, new Object[]{realTableName, columnName}, Integer.class);

                    if (oldLength == null || oldLength <= 0) {
                        throw new RuntimeException("无法获取字段原始长度");
                    }

                    int newLength = oldLength * 2;

                    String alterSql = String.format("ALTER TABLE `%s` MODIFY COLUMN `%s` VARCHAR(%d)", realTableName, columnName, newLength);
                    System.out.println("字段过长，尝试修改字段长度并重试：" + alterSql);
                    DatabaseUtil.getJdbcTemplate().execute(alterSql);
                    // 不跳过当前记录，重试
                }
                showErrorAndClose(progressStage, "导入失败: " + threadException.get().getMessage());
                return;
            }

            updateProgress(1, "导入完成");
            Platform.runLater(progressStage::close);
        });
    }
    // 错误提示 + 关闭窗口的封装
    private void showErrorAndClose(Stage stage, String message) {
        Platform.runLater(() -> {
            stage.close();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("错误");
            alert.setHeaderText("导入过程中出现错误");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    /**
     * 进度条导出数据
     */
    private void dbToXml() {
        tabIsExist();
        if("world".equals(tabName)){
            if(mapType == null || mapType.trim().isEmpty()){
                throw new RuntimeException("请选择地图类型");
            }
        }
        Stage progressStage = createProgressDialog("正在导出数据至XML...");
        executor.execute(() -> {
            updateProgress(0, "导出数据中...");
            Thread importThread = null;
            if("world".equals(tabName)){
                WorldDbToXmlGenerator dbToXmlGenerator = new WorldDbToXmlGenerator(tabName, mapType, tabFilePath);
                // 在新线程中执行 dbToXml 导入
                importThread = new Thread(dbToXmlGenerator::processAndMerge);
                importThread.start();
                while (true){
                    try {
                        Thread.sleep(500);
                        double progress = dbToXmlGenerator.getProgress();
                        updateProgress(progress, "导出进度: " + String.format("%.2f", (progress * 100)) + "%");
                        if(progress == 1){
                            break;
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }else{
                DbToXmlGenerator dbToXmlGenerator = new DbToXmlGenerator(tabName, mapType, tabFilePath);
                importThread = new Thread(dbToXmlGenerator::processAndMerge);
                importThread.start();
                while (true){
                    try {
                        Thread.sleep(500);
                        double progress = dbToXmlGenerator.getProgress();
                        updateProgress(progress, "导出进度: " + String.format("%.2f", (progress * 100)) + "%");
                        if(progress == 1){
                            break;
                        }
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }


            updateProgress(1, "导出完成");
            Platform.runLater(progressStage::close);
        });
    }

    /**
     * 创建进度条弹窗
     */
    private Stage createProgressDialog(String title) {
        Stage progressStage = new Stage();
        progressStage.setTitle(title);
        progressStage.setResizable(false);

        progressBar = new ProgressBar(0);

        progressBar.setPrefWidth(300);
        progressLabel = new Label("请稍候...");

        VBox progressBox = new VBox(10, progressLabel, progressBar);
        progressBox.setPadding(new Insets(10));
        progressBox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(progressBox, 350, 120);
        progressStage.setScene(scene);
        progressStage.show();

        return progressStage;
    }


    /**
     * 更新进度条
     */
    private void updateProgress(double progress, String message) {
        Platform.runLater(() -> {
            double roundedValue = Math.round(progress * 100.0) / 100.0;
            progressBar.setProgress(roundedValue);
            progressLabel.setText(message);
        });
    }

    public void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("错误提示");
        alert.setHeaderText("发生异常");
        alert.setContentText(message);
        alert.show();
    }

    private void tabIsExist(){
        try {
            TableConf tale = TabConfLoad.getTale(tabName, tabFilePath);
        } catch (Exception e) {
            String message = e.getMessage();
            if(message.contains("表配置文件不存在")){
                message = "该表配置不存在，请先执行“DDL生成”";
            }
            showError(message);
            throw new RuntimeException(message);
        }
    }

    /**
     * 显示一个弹出框，让用户选择要ai改写的列。
     * 选中的列将传递给 xmlTodbWithSelectedFields 方法。
     */
    /*private void showColumnSelectionForXmlToDb(String xmlFile) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("选择AI改写的列");

        VBox dialogVBox = new VBox(10);
        dialogVBox.setPadding(new Insets(10));

        // --- 模型选择下拉框 ---
        Label modelLabel = new Label("选择AI模型：");
        ComboBox<String> modelComboBox = new ComboBox<>();
        List<String> modelKeys = YamlUtils.loadAiModelKeys("application.yml");

        if (modelKeys.isEmpty()) {
            showError("未从配置文件中读取到 AI 模型 key，请检查 配置文件！");
            dialogStage.close();
            return;
        }

        modelComboBox.getItems().addAll(modelKeys);
        modelComboBox.setValue(modelKeys.get(0));

        HBox modelBox = new HBox(10, modelLabel, modelComboBox);
        modelBox.setAlignment(Pos.CENTER_LEFT);
        dialogVBox.getChildren().add(modelBox);

        // 用于存储列名和对应 CheckBox 的映射
        Map<String, CheckBox> columnCheckBoxes = new LinkedHashMap<>();

        List<String> allColumnNames = tableView.getColumns().stream()
                .map(TableColumn::getText)
                .collect(Collectors.toList());

        if (allColumnNames.isEmpty()) {
            try {
                allColumnNames = DatabaseUtil.getColumnNamesFromDb(tabName);
                if (allColumnNames.isEmpty()) {
                    showError("数据库表 [" + tabName + "] 中未找到任何列！");
                    dialogStage.close();
                    return;
                }
            } catch (Exception e) {
                showError("无法读取数据库表 [" + tabName + "]，请确认表是否存在！");
                e.printStackTrace();
                dialogStage.close();
                return;
            }
        }

        VBox checkboxesContainer = new VBox(5);
        for (String colName : allColumnNames) {
            CheckBox checkBox = new CheckBox(colName);
            checkBox.setSelected(false); // 默认不选中
            columnCheckBoxes.put(colName, checkBox);
            checkboxesContainer.getChildren().add(checkBox);

            final String currentColumnName = colName;
            checkBox.selectedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) {
                    boolean isValid = validateColumnSelection(currentColumnName);
                    if (!isValid) {
                        Platform.runLater(() -> {
                            checkBox.setSelected(false);
                            showWarning("列 '" + currentColumnName + "' 提示词未配置，无法勾选。");
                        });
                    }
                }
            });
        }

        ScrollPane scrollPane = new ScrollPane(checkboxesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(Math.min(300, checkboxesContainer.getChildren().size() * 30));

        dialogVBox.getChildren().add(scrollPane);

        // --- 底部按钮区域 ---
        Button confirmButton = new Button("确定");
        confirmButton.setOnAction(event -> {
            List<String> selectedColumns = new ArrayList<>();
            columnCheckBoxes.forEach((colName, checkBox) -> {
                if (checkBox.isSelected()) {
                    selectedColumns.add(colName);
                }
            });

            if (selectedColumns.isEmpty()) {
                showError("请至少选择一列进行导入！");
                return;
            }

            String selectedModel = modelComboBox.getValue();
            log.info("用户从文件 [{}] 中选择了列：{}，使用模型：{}", xmlFile, selectedColumns, selectedModel);

            xmlToDb(xmlFile, selectedColumns, selectedModel); // 调用带模型参数的方法
            dialogStage.close();
        });

        Button cancelButton = new Button("取消");
        cancelButton.setOnAction(event -> dialogStage.close());

        HBox buttonBox = new HBox(10, confirmButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        dialogVBox.getChildren().add(buttonBox);

        Scene scene = new Scene(dialogVBox, 400, Region.USE_COMPUTED_SIZE);
        dialogStage.setScene(scene);
        dialogStage.sizeToScene();
        dialogStage.showAndWait();
    }*/

    private void showColumnSelectionForXmlToDb(String xmlFile) {
        Stage dialogStage = new Stage();
        dialogStage.initModality(Modality.APPLICATION_MODAL);
        dialogStage.setTitle("选择AI改写的列");

        VBox dialogVBox = new VBox(10);
        dialogVBox.setPadding(new Insets(10));
        // 设置对话框宽度，确保能显示完整
        dialogStage.setWidth(1100);
        dialogStage.setMinWidth(1100);

        // VBox设置合适宽度
        dialogVBox.setPrefWidth(1100);
        // --- 模型选择下拉框 ---
        Label modelLabel = new Label("选择AI模型：");
        ComboBox<String> modelComboBox = new ComboBox<>();
        List<String> modelKeys = YamlUtils.loadAiModelKeys("application.yml");

        if (modelKeys.isEmpty()) {
            showError("未从配置文件中读取到 AI 模型 key，请检查 配置文件！");
            dialogStage.close();
            return;
        }

        modelComboBox.getItems().addAll(modelKeys);
        modelComboBox.setValue(modelKeys.get(0));

        HBox modelBox = new HBox(10, modelLabel, modelComboBox);
        modelBox.setAlignment(Pos.CENTER_LEFT);
        dialogVBox.getChildren().add(modelBox);

        // --- 获取所有列名 ---
        List<String> allColumnNames = tableView.getColumns().stream()
                .map(TableColumn::getText)
                .collect(Collectors.toList());

        if (allColumnNames.isEmpty()) {
            try {
                allColumnNames = DatabaseUtil.getColumnNamesFromDb(tabName);
                if (allColumnNames.isEmpty()) {
                    showError("数据库表 [" + tabName + "] 中未找到任何列！");
                    dialogStage.close();
                    return;
                }
            } catch (Exception e) {
                showError("无法读取数据库表 [" + tabName + "]，请确认表是否存在！");
                e.printStackTrace();
                dialogStage.close();
                return;
            }
        }

        // --- 表格显示列名 & 提示词 ---
        TableView<ColumnPrompt> table = new TableView<>();
        table.setEditable(true);
        // 让table宽度跟VBox宽度绑定，随窗口变动
        table.prefWidthProperty().bind(dialogVBox.widthProperty());

        // 勾选列
        TableColumn<ColumnPrompt, Boolean> selectCol = new TableColumn<>("选择");
        selectCol.setCellValueFactory(param -> param.getValue().selectedProperty());
        selectCol.setCellFactory(CheckBoxTableCell.forTableColumn(selectCol));
        selectCol.setPrefWidth(60);
        // 假设是你初始化 TableView 时，给选择列的复选框监听：
        selectCol.setCellFactory(tc -> {
            CheckBoxTableCell<ColumnPrompt, Boolean> cell = new CheckBoxTableCell<>(index -> {
                BooleanProperty selected = table.getItems().get(index).selectedProperty();
                // 监听selected属性变化
                selected.addListener((obs, wasSelected, isNowSelected) -> {
                    if (isNowSelected) {
                        ColumnPrompt rowItem = table.getItems().get(index);

                        if (rowItem.getPrompt() == null || rowItem.getPrompt().isEmpty()) {
                            String property = YamlUtils.getProperty("ai.promptKey." + tabName + "@" + rowItem.getName());
                            rowItem.setPrompt(property);
                        }
                    }
                });
                return selected;
            });
            return cell;
        });

        // 列名
        TableColumn<ColumnPrompt, String> nameCol = new TableColumn<>("列名");
        nameCol.setCellValueFactory(param -> param.getValue().nameProperty());
        nameCol.setEditable(false);


        // 提示词（可下拉、可输入、带校验）
        TableColumn<ColumnPrompt, String> promptCol = new TableColumn<>("提示词");
        List<String> defaultPrompts = YamlUtils.loadPromptListFromYaml("ai.promptKey.common");
        ObservableList<String> promptOptions = FXCollections.observableArrayList(defaultPrompts);
        promptCol.setPrefWidth(870);
        promptCol.setCellValueFactory(param -> param.getValue().promptProperty());
        promptCol.setCellFactory(col -> {
            TableCell<ColumnPrompt, String> cell = new TableCell<ColumnPrompt, String>() {
                private final ComboBox<String> comboBox = new ComboBox<>(promptOptions);
                {
                    comboBox.setEditable(true);
                    // 宽度绑定：ComboBox宽度 = 当前列宽度 - 10 (留出一点边距)
                    comboBox.prefWidthProperty().bind(promptCol.widthProperty().subtract(10));
                    comboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
                        if (!isEmpty()) {
                            ColumnPrompt rowItem = getTableView().getItems().get(getIndex());
                            rowItem.setPrompt(newVal);
                        }
                    });
                }

                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty) {
                        setGraphic(null);
                    } else {
                        comboBox.setValue(item);
                        setGraphic(comboBox);
                    }
                }
            };
            return cell;
        });

        table.getColumns().addAll(selectCol, nameCol, promptCol);

        // 填充表格数据
        for (String colName : allColumnNames) {
            table.getItems().add(new ColumnPrompt(colName));
        }

        dialogVBox.getChildren().add(table);
        // --- 底部按钮 ---
        Button confirmButton = new Button("确定");
        confirmButton.setOnAction(event -> {

            List<ColumnPrompt> selectedItems = table.getItems().stream()
                    .filter(ColumnPrompt::isSelected)    // 只要被勾选的列
                    .collect(Collectors.toList());
            Map<String, String> colNameToPrompt = selectedItems.stream()
                    .collect(Collectors.toMap(ColumnPrompt::getName, ColumnPrompt::getPrompt));

            if (colNameToPrompt.isEmpty()) {
                showError("请至少选择一列进行导入！");
                return;
            }
            colNameToPrompt.forEach((colName, prompt) -> {
                if (prompt == null || prompt.trim().isEmpty()) {
                    showError("请为列 [" + colName + "] 填写有效的提示词！");
                }
                YmlConfigUtil.updateResourcesYml("ai.promptKey." + tabName + "@"  + colName, prompt);
            });

            List<String> selectedColumns = selectedItems.stream()
                    .map(ColumnPrompt::getName)
                    .collect(Collectors.toList());

            String selectedModel = modelComboBox.getValue();
            log.info("用户从文件 [{}] 中选择了列：{}，使用模型：{}", xmlFile, selectedColumns, selectedModel);

            xmlToDb(xmlFile, selectedColumns, selectedModel);
            dialogStage.close();
        });

        Button cancelButton = new Button("取消");
        cancelButton.setOnAction(event -> dialogStage.close());

        HBox buttonBox = new HBox(10, confirmButton, cancelButton);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        dialogVBox.getChildren().add(buttonBox);

        Scene scene = new Scene(dialogVBox, 600, 400);
        dialogStage.setScene(scene);
        dialogStage.showAndWait();
    }

    // 校验提示词
//    private boolean isPromptValid(String prompt) {
//        if (prompt == null || prompt.trim().isEmpty()) return false;
//        if (prompt.length() > 50) return false;
//        return prompt.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9]+$");
//    }

    // 数据模型
    public static class ColumnPrompt {
        private final StringProperty name = new SimpleStringProperty();
        private final BooleanProperty selected = new SimpleBooleanProperty(false);
        private final StringProperty prompt = new SimpleStringProperty();

        public ColumnPrompt(String name) {
            this.name.set(name);
            this.prompt.set("");
        }

        public StringProperty nameProperty() { return name; }
        public BooleanProperty selectedProperty() { return selected; }
        public StringProperty promptProperty() { return prompt; }

        public String getName() { return name.get(); }
        public boolean isSelected() { return selected.get(); }
        public String getPrompt() { return prompt.get(); }
        public void setPrompt(String value) { prompt.set(value); }
    }



    /**
     * 判断所选列是否合法。
     * 这里只是一个示例，您需要根据您的业务需求实现具体的判断逻辑。
     * @param columnName 被勾选的列名
     * @return 如果列合法则返回 true，否则返回 false
     */
    private boolean validateColumnSelection(String columnName) {
        String property = YamlUtils.getProperty("ai.promptKey." + tabName + "@" +columnName);
        log.info("正在验证列选择: {}", columnName);
        if (!StringUtils.hasLength(property)) {
            log.warn("列 '{}' prompt未配置。", columnName);
            return false;
        }

        return true;
    }

    /**
     * 显示一个警告提示框。
     * @param message 警告信息
     */
    public void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("警告");
            alert.setHeaderText(null); // 不显示头部文本
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

}

