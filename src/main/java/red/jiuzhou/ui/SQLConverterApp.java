package red.jiuzhou.ui;

import cn.hutool.core.io.FileUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.controlsfx.control.CheckComboBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import red.jiuzhou.util.DatabaseUtil;
import red.jiuzhou.util.JSONRecord;
import red.jiuzhou.util.YamlUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SQLConverterApp{

    private static final Logger log = LoggerFactory.getLogger(SQLConverterApp.class);
    private ComboBox<String> sourceDbCombo;
    private ComboBox<String> sourceTableCombo;
    private ComboBox<String> targetDbCombo;
    private ComboBox<String> targetTableCombo;
    private CheckComboBox<String> idCheckComboBox;
    private CheckBox selectAllCheckBox;
    private ComboBox<String> operationTypeCombo;
    private TextArea resultArea;


    public void show(Stage primaryStage) {
        Stage stage = new Stage();
        stage.setTitle("SQL 转换器");
        stage.initOwner(primaryStage);
        operationTypeCombo = new ComboBox<>();
        // 操作类型初始化
        operationTypeCombo.getItems().addAll("新增", "更新");
        operationTypeCombo.setValue("新增");

        sourceDbCombo = new ComboBox<>();
        sourceTableCombo = new ComboBox<>();
        targetDbCombo = new ComboBox<>();
        targetTableCombo = new ComboBox<>();
        idCheckComboBox = new CheckComboBox<>();

        selectAllCheckBox = new CheckBox("全部 ID");

        resultArea = new TextArea();

        operationTypeCombo.setPrefWidth(100);
        // 固定宽度
        sourceDbCombo.setPrefWidth(100);
        targetDbCombo.setPrefWidth(100);
        sourceTableCombo.setPrefWidth(240);
        targetTableCombo.setPrefWidth(240);
        idCheckComboBox.setPrefWidth(140);

        // 布局
        GridPane controlRow = new GridPane();
        controlRow.setHgap(10);
        controlRow.setVgap(5);
        controlRow.setPadding(new Insets(10));
        controlRow.add(new Label("操作:"), 0, 0);
        controlRow.add(operationTypeCombo, 1, 0);
        controlRow.add(new Label("源库:"), 2, 0);
        controlRow.add(sourceDbCombo, 3, 0);
        controlRow.add(new Label("源表:"), 4, 0);
        controlRow.add(sourceTableCombo, 5, 0);
        controlRow.add(new Label("目标库:"), 6, 0);
        controlRow.add(targetDbCombo, 7, 0);
        controlRow.add(new Label("目标表:"), 8, 0);
        controlRow.add(targetTableCombo, 9, 0);
        controlRow.add(new Label("选择ID:"), 10, 0);
        controlRow.add(idCheckComboBox, 11, 0);
        controlRow.add(selectAllCheckBox, 12, 0);


        Button convertButton = new Button("转换");
        Button exportButton = new Button("导出");
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setVisible(false);
        spinner.setPrefSize(24, 24);
        //HBox buttonBox = new HBox(10, convertButton, exportButton);
        HBox buttonBox = new HBox(10, convertButton, exportButton, spinner);
        buttonBox.setPadding(new Insets(0, 0, 10, 10));

        convertButton.setOnAction(e -> {
            runConverterTask(false, spinner);
        });
        exportButton.setOnAction(e -> {
            runConverterTask(true, spinner);
        });

        resultArea.setPrefHeight(300);
        resultArea.setWrapText(true);

        VBox root = new VBox(10, controlRow, buttonBox, resultArea);

        Scene scene = new Scene(root, 1500, 550);
        stage.setScene(scene);
        stage.show();
        sourceDbCombo.getItems().setAll(DatabaseUtil.listAllDatabases());
        targetDbCombo.getItems().setAll(DatabaseUtil.listAllDatabases());
        // 异步加载表名和 ID
        //new Thread(this::loadTableNames).start();

        //loadTableNames();
        // 源表选择监听，加载该表的 ID
        sourceDbCombo.setOnAction(e -> {
            String dbName = sourceDbCombo.getValue();
            loadTableNames(dbName, true);
            sourceTableCombo.setValue(null);
        });
        targetDbCombo.setOnAction(e -> {
            String dbName = targetDbCombo.getValue();
            loadTableNames(dbName, false);
            targetTableCombo.setValue(null);
        });
        sourceTableCombo.setOnAction(e -> loadIdsFromSourceTable());
        targetTableCombo.setOnAction(e -> loadIdsFromSourceTable());
        operationTypeCombo.setOnAction(e -> loadIdsFromSourceTable());

        selectAllCheckBox.setOnAction(e -> {
            boolean selectAll = selectAllCheckBox.isSelected();
            idCheckComboBox.setDisable(selectAll);

        });
    }

    private void runConverterTask(boolean export, ProgressIndicator spinner) {
        spinner.setVisible(true);

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() {
                converter(export); // 原本的逻辑
                return null;
            }

            @Override
            protected void succeeded() {
                spinner.setVisible(false);
            }

            @Override
            protected void failed() {
                spinner.setVisible(false);
                getException().printStackTrace();
            }
        };

        new Thread(task).start();
    }


    private void converter(boolean exportFile) {

        String sourceTable = sourceTableCombo.getValue();
        String targetTable = targetTableCombo.getValue();
        List<String> sourceTableList = DatabaseUtil.getTableNamesByPrefix(sourceTable, sourceDbCombo.getValue());
        resultArea.clear();
        StringBuilder sqlBuilder = new StringBuilder();
        int total = sourceTableList.size();
        for (int i = 0; i < total; i++) {
            String sourceTableName = sourceTableList.get(i);
            String targetTableName = targetTable + sourceTableName.replace(sourceTable, "");
            if (DatabaseUtil.tableExists(targetTableName, targetDbCombo.getValue())) {
                String sql = convertSQL(sourceTableName, targetTableName);
                sqlBuilder.append("-- ").append(targetTableName).append("\n");
                sqlBuilder.append(sql).append("\n");
            }
        }

        Platform.runLater(() -> {
            if(exportFile){
                String exportPath = YamlUtils.getProperty("file.exportDataPath");
                String type = "新增".equals(operationTypeCombo.getValue()) ? "INSERT" : "UPDATE";
                String fileName =  type + "-" + sourceTable + "-TO-" + targetTable + "_" + System.currentTimeMillis() + ".sql";
                FileUtil.writeUtf8String(sqlBuilder.toString(), exportPath + fileName);
                sqlBuilder.setLength(0);
                resultArea.setText("文件已导出至：" + exportPath + File.separator + fileName);
            }else{
                resultArea.setText(sqlBuilder.toString());
            }
        });
    }

    private void loadTableNames(String dbName, boolean isSource) {
        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();
            String sql = "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE' AND TABLE_SCHEMA = '" + dbName + "'";
            List<String> tables = jdbc.queryForList(sql, String.class);

            Platform.runLater(() -> {
                if (!tables.isEmpty()) {
                    if (isSource){
                        sourceTableCombo.getItems().setAll(tables);
                        enableComboBoxFilter(sourceTableCombo, tables);
                    }else{
                        targetTableCombo.getItems().setAll(tables);
                        enableComboBoxFilter(targetTableCombo, tables);
                    }


                }
            });
        } catch (Exception e) {
            log.error("err:::::{}", JSONRecord.getErrorMsg(e));
        }
    }
    private void loadIdsFromSourceTable() {
        String sourceTable = sourceTableCombo.getValue();
        String targetTable = targetTableCombo.getValue();
        String operation = operationTypeCombo.getValue();

        if (sourceTable == null || targetTable == null || operation == null){
            return;
        }

        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();
            List<String> ids;

            if ("新增".equals(operation)) {
                // 操作为新增，选择源表中有但目的表中没有的 ID
                String sql = String.format(
                        "SELECT DISTINCT ID FROM %s.%s WHERE ID NOT IN (SELECT DISTINCT ID FROM %s.%s) ORDER BY CAST(ID AS UNSIGNED)", sourceDbCombo.getValue(), sourceTable, targetDbCombo.getValue(), targetTable);
                ids = jdbc.queryForList(sql, String.class);
            } else if ("更新".equals(operation)) {
                // 操作为更新，选择源表和目的表中都存在的 ID
                String sql = String.format(
                        "SELECT DISTINCT ID FROM %s.%s WHERE ID IN (SELECT DISTINCT ID FROM %s.%s) ORDER BY CAST(ID AS UNSIGNED)", sourceDbCombo.getValue(), sourceTable, targetDbCombo.getValue(), targetTable);
                ids = jdbc.queryForList(sql, String.class);
            } else {
                ids = null;
            }

            Platform.runLater(() -> {
                idCheckComboBox.getItems().clear();
                if (ids.isEmpty()) {
                    idCheckComboBox.setDisable(true);
                } else {
                    // 否则，加载 ID 到下拉框
                    idCheckComboBox.getItems().setAll(ids);
                    // 根据复选框状态来决定是否禁用 ID 下拉框
                    if (!selectAllCheckBox.isSelected()) {
                        idCheckComboBox.setDisable(false);
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            log.error("err:::::{}", JSONRecord.getErrorMsg(e));
        }
    }

    private String convertSQL(String sourceTable, String targetTable) {
        Set<String> addedSqls = new HashSet<>();
        String idField = "id";
        String operation = operationTypeCombo.getValue();
        String sourceDb = sourceDbCombo.getValue();
        String targetDb =  targetDbCombo.getValue();
        if (sourceDb == null || sourceTable == null || targetDb == null || targetTable == null || operation == null) {
            return "-- 请先选择源库、源表、目标库、目标表、操作类型";
        }

        // 库名 + 表名都相同时才拦截
        if (sourceDb.equals(targetDb) && sourceTable.equals(targetTable)) {
            return "-- 源库和目标库的表相同，无法同步";
        }

        boolean selectAll = selectAllCheckBox.isSelected();
        List<String> selectedIds;
        if(selectAll){
            selectedIds = idCheckComboBox.getItems();
        }else{
            selectedIds = idCheckComboBox.getCheckModel().getCheckedItems();
        }

        log.info("selectedIds::{}", selectedIds);
        if (selectedIds == null || selectedIds.isEmpty()) {
            return "-- 未选择任何 ID";
        }

        JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();

        try {
            List<String> commonFields = getCommonFields(sourceTable, targetTable);
            log.info("commonFields:{}", commonFields);
            if (commonFields.isEmpty()){
                return "-- 源表"+sourceTable+"和目标表"+targetTable+"没有相同字段";
            }

            String fieldList = commonFields.stream()
                    .map(f -> "`" + f + "`")
                    .collect(Collectors.joining(", "));

            String placeholders = commonFields.stream().map(f -> "?").collect(Collectors.joining(", "));

            StringBuilder sqlBuilder = new StringBuilder();

            for (String idValue : selectedIds) {
                // 查询源表对应数据
                //String selectSQL = String.format("SELECT %s FROM %s WHERE %s = ?", fieldList, sourceTable, idField);
                String selectSQL = String.format("SELECT %s FROM `%s` WHERE `%s` = ?", fieldList, sourceTable, idField);

                List<Map<String, Object>> resultList = jdbc.queryForList(selectSQL, idValue);
                if (resultList.isEmpty()) {
                    //sqlBuilder.append("-- ID ").append(idValue).append(" 未在源表中找到对应数据\n");
                    continue; // 跳过当前 id
                }
                if(!sourceTable.equals(sourceTableCombo.getValue())){
                    sqlBuilder.append("DELETE FROM " + targetDb + "."  + targetTable + " WHERE ID = '" + idValue + "';\n");
                    operation = "新增";
                }
                String finalOperation = operation;
                resultList.forEach(sourceData ->{
                    if ("新增".equalsIgnoreCase(finalOperation)) {
                        if (sourceTable.equals(targetTable)) {
                            // 表名一样，直接insert-select
                            String sql = String.format(
                                    "INSERT INTO `%s`.`%s` SELECT * FROM `%s`.`%s` WHERE `%s` = '%s';\n",
                                    targetDb, targetTable, sourceDb, sourceTable, idField, idValue
                            );
                            if(!addedSqls.contains(sql)){
                                addedSqls.add(sql);
                                sqlBuilder.append(sql);
                            }

                        } else {
                            String insertSQL = String.format("INSERT INTO `%s` (%s) VALUES (%s);", targetTable, fieldList, placeholders);
                            sqlBuilder.append(fillSQLWithValues(insertSQL, commonFields, sourceData)).append("\n");
                        }
                    } else if ("更新".equalsIgnoreCase(finalOperation)) {
                        String setClause = commonFields.stream()
                                .map(f -> "`" + f + "` = ?")
                                .collect(Collectors.joining(", "));

                        String updateSQL = String.format("UPDATE `%s` SET %s WHERE `%s` = ?;", targetTable, setClause, idField);
                        String sql = fillSQLWithValues(updateSQL, commonFields, sourceData, idValue);
                        if(StringUtils.hasLength(sql)){
                            sqlBuilder.append(sql).append("\n");
                        }

                    }
                });


            }

            return sqlBuilder.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "-- 生成 SQL 时出错：" + e.getMessage();
        }
    }

    private String fillSQLWithValues(String sql, List<String> fields, Map<String, Object> valuesMap) {
        List<String> values = fields.stream()
                .map(f -> formatValue(valuesMap.get(f)))
                .collect(Collectors.toList());

        for (String val : values) {
            sql = sql.replaceFirst("\\?", val);
        }
        return sql;
    }

    private String fillSQLWithValues(String sql, List<String> fields, Map<String, Object> valuesMap, String idVal) {
        // 同上方法，末尾加 ID 值
        List<String> values = fields.stream()
                .map(f -> formatValue(valuesMap.get(f)))
                .collect(Collectors.toList());
        values.add(formatValue(idVal));

        for (String val : values) {
            sql = sql.replaceFirst("\\?", val);
        }
        return cleanSql(sql);
    }

    public static String cleanSql(String sql) {
        // 提取 SET 子句内容
        Matcher matcher = Pattern.compile("(?i)SET\\s+(.*?)\\s+WHERE").matcher(sql);
        if (matcher.find()) {
            String setClause = matcher.group(1);
            // 分割字段赋值语句
            String[] assignments = setClause.split("\\s*,\\s*");
            // 过滤掉 id 字段
            List<String> filtered = Arrays.stream(assignments)
                    .filter(s -> !s.matches("(?i)`?id`?\\s*=\\s*'.*'"))
                    .collect(Collectors.toList());

            // 如果过滤后为空，说明只有 id，返回空
            if (filtered.isEmpty()) {
                return "";
            }

            // 构造新 SQL
            String newSetClause = "SET " + String.join(", ", filtered);
            return sql.replaceFirst("(?i)SET\\s+.*?\\s+WHERE", newSetClause + " WHERE");
        }

        return sql;
    }


    private String formatValue(Object val) {
        if (val == null){
            return "NULL";
        }
        if (val instanceof Number){
            return val.toString();
        }
        return "'" + val.toString().replace("'", "''") + "'";
    }


    private void exportToFile(Stage stage) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("保存 SQL 文件");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL 文件", "*.sql"));
        fileChooser.setInitialFileName("converted.sql");

        java.io.File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(resultArea.getText());
            } catch (IOException e) {
                log.error("err:::::{}", JSONRecord.getErrorMsg(e));
            }
        }
    }
    private void enableComboBoxFilter(ComboBox<String> comboBox, List<String> allItems) {
        // 确保 ComboBox 初始时有项，避免空值问题
        if (allItems == null || allItems.isEmpty()) {
            log.warn("ComboBox items list is empty, skipping filter initialization.");
            return;
        }

        comboBox.setEditable(true); // 使 ComboBox 可编辑，允许用户输入
        TextField editor = comboBox.getEditor();

        // 初始时加载所有项
        Platform.runLater(() -> comboBox.getItems().setAll(allItems));

        // 监听文本变化
        editor.textProperty().addListener((obs, oldVal, newVal) -> {
            // 如果输入框为空，显示所有项
            if (newVal == null || newVal.isEmpty()) {
                Platform.runLater(() -> comboBox.getItems().setAll(allItems));
            } else {
                // 根据输入内容进行过滤
                List<String> filtered = allItems.stream()
                        .filter(item -> item.toLowerCase().contains(newVal.toLowerCase())) // 使用小写做模糊匹配
                        .collect(Collectors.toList());

                // 更新 ComboBox 项目列表
                Platform.runLater(() -> comboBox.getItems().setAll(filtered));
            }

            // 显示下拉列表
            comboBox.show();
        });
    }

    private List<String> getCommonFields(String sourceTable, String targetTable) {

        if (sourceTable == null || targetTable == null) {
            resultArea.setText("请完整选择源表、目标表和操作类型！");
            return null;
        }
        List<String> commonFields = new ArrayList<>();

        try {
            JdbcTemplate jdbc = DatabaseUtil.getJdbcTemplate();

            // 查询两个表共有的字段
            String sql = String.format(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s' " +
                            "AND COLUMN_NAME IN (" +
                            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s')",
                    sourceDbCombo.getValue(), sourceTable, targetDbCombo.getValue(), targetTable);

            commonFields = jdbc.queryForList(sql, String.class);

        } catch (Exception e) {
            e.printStackTrace();
            log.error("err:::::{}", JSONRecord.getErrorMsg(e));
        }

        return commonFields;
    }

}
